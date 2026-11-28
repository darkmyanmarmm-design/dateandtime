package com.mhtime.tvauto

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec

open class AdbException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** adbd refused / never approved our key. Retrying immediately only causes more prompts. */
class AdbAuthException(message: String) : AdbException(message)

class AdbKeyPair(val priv: RSAPrivateCrtKey, val pub: RSAPublicKey)

/* ------------------------------------------------------------------------------------------ *
 *  Crypto / encoding helpers (pure functions, easy to reason about)
 * ------------------------------------------------------------------------------------------ */
object AdbCrypto {
    /** struct RSAPublicKey { u32 len=64; u32 n0inv; u8 n[256]; u8 rr[256]; u32 exponent; } */
    const val KEY_STRUCT_SIZE = 524
    private const val KEY_WORDS = 64
    private const val MOD_BYTES = 256
    private val TWO_32: BigInteger = BigInteger.ONE.shiftLeft(32)

    /** ASN.1 DigestInfo prefix for SHA-1. adbd calls RSA_verify(NID_sha1, token[20], ...). */
    private val SHA1_DIGEST_INFO = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    )

    fun encodePublicKeyStruct(pub: RSAPublicKey): ByteArray {
        val n = pub.modulus
        require(n.bitLength() == 2048) { "ADB needs an RSA-2048 key (got ${n.bitLength()} bits)" }
        // n0inv = -(n^-1) mod 2^32   (NOTE the negation)
        val n0inv = TWO_32.subtract(n.mod(TWO_32).modInverse(TWO_32)).toInt()
        // rr = R^2 mod n with R = 2^2048
        val rr = BigInteger.ONE.shiftLeft(2 * 2048).mod(n)

        val bb = ByteBuffer.allocate(KEY_STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(KEY_WORDS)
        bb.putInt(n0inv)
        bb.put(toLittleEndian(n, MOD_BYTES))
        bb.put(toLittleEndian(rr, MOD_BYTES))
        bb.putInt(pub.publicExponent.toInt())
        check(!bb.hasRemaining()) { "key struct size mismatch" }
        return bb.array()
    }

    /** Re-parses the struct and checks the Montgomery constants. Returns an error text or null. */
    fun validateStruct(struct: ByteArray, pub: RSAPublicKey): String? {
        if (struct.size != KEY_STRUCT_SIZE) return "size ${struct.size} != $KEY_STRUCT_SIZE"
        val bb = ByteBuffer.wrap(struct).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.getInt() != KEY_WORDS) return "len word != 64"
        val n0inv = bb.getInt().toLong() and 0xFFFFFFFFL
        val nBytes = ByteArray(MOD_BYTES).also { bb.get(it) }
        val rrBytes = ByteArray(MOD_BYTES).also { bb.get(it) }
        val exp = bb.getInt()
        val n = fromLittleEndian(nBytes)
        if (n != pub.modulus) return "modulus mismatch"
        val n0 = n.mod(TWO_32).toLong()
        if (((n0 * n0inv) and 0xFFFFFFFFL) != 0xFFFFFFFFL) return "n0inv is not -1/n[0] mod 2^32"
        if (fromLittleEndian(rrBytes) != BigInteger.ONE.shiftLeft(4096).mod(n)) return "rr != R^2 mod n"
        if (exp != pub.publicExponent.toInt()) return "exponent mismatch"
        return null
    }

    /**
     * AUTH_RSAPUBLICKEY payload: standard Base64 (NO line wraps, with padding), one space, a plain
     * ASCII name, then a terminating NUL (same as host adb). Exactly 700 base64 chars.
     */
    fun publicKeyPayload(pub: RSAPublicKey, name: String): ByteArray {
        val b64 = Base64.encodeToString(encodePublicKeyStruct(pub), Base64.NO_WRAP)
        check(b64.length == 700 && !b64.contains('\n')) { "unexpected base64 length ${b64.length}" }
        require(name.all { it.code in 0x21..0x7E }) { "key name must be printable ASCII without spaces" }
        return "$b64 $name\u0000".toByteArray(Charsets.US_ASCII)
    }

    /** Same fingerprint the "Allow USB debugging?" dialog shows (MD5 over the decoded struct). */
    fun fingerprint(pub: RSAPublicKey): String =
        MessageDigest.getInstance("MD5").digest(encodePublicKeyStruct(pub))
            .joinToString(":") { "%02X".format(it) }

    /**
     * Signs the 20-byte AUTH token. The token is treated as an already-computed SHA-1 digest, so we use
     * NONEwithRSA over DigestInfo||token (PKCS#1 v1.5). The result is verified locally with the public
     * key; if the provider misbehaves we fall back to textbook RSA with BigInteger.
     */
    fun signToken(keys: AdbKeyPair, token: ByteArray, log: (String) -> Unit): ByteArray {
        val payload = SHA1_DIGEST_INFO + token
        val expected = pkcs1Type1(payload, MOD_BYTES)
        try {
            val sig = Signature.getInstance("NONEwithRSA").run {
                initSign(keys.priv)
                update(payload)
                sign()
            }
            val recovered = toBigEndian(
                BigInteger(1, sig).modPow(keys.pub.publicExponent, keys.pub.modulus), MOD_BYTES
            )
            if (recovered.contentEquals(expected)) return sig
            log("NONEwithRSA signature failed local verification -> manual RSA fallback")
        } catch (e: Exception) {
            log("NONEwithRSA unavailable (${e.javaClass.simpleName}: ${e.message}) -> manual RSA fallback")
        }
        val s = BigInteger(1, expected).modPow(keys.priv.privateExponent, keys.priv.modulus)
        return toBigEndian(s, MOD_BYTES)
    }

    private fun pkcs1Type1(payload: ByteArray, k: Int): ByteArray {
        val em = ByteArray(k) { 0xFF.toByte() }
        em[0] = 0x00
        em[1] = 0x01
        em[k - payload.size - 1] = 0x00
        System.arraycopy(payload, 0, em, k - payload.size, payload.size)
        return em
    }

    private fun toBigEndian(v: BigInteger, len: Int): ByteArray {
        val raw = v.toByteArray()                     // may carry a leading sign byte
        val start = maxOf(0, raw.size - len)
        for (i in 0 until start) require(raw[i] == 0.toByte()) { "value does not fit in $len bytes" }
        val count = raw.size - start
        val out = ByteArray(len)
        System.arraycopy(raw, start, out, len - count, count)
        return out
    }

    private fun toLittleEndian(v: BigInteger, len: Int): ByteArray = toBigEndian(v, len).reversedArray()
    private fun fromLittleEndian(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())
}

/* ------------------------------------------------------------------------------------------ *
 *  Persistent key storage (SharedPreferences, device-protected so it works before first unlock)
 * ------------------------------------------------------------------------------------------ */
class AdbKeyStore(context: Context) {
    private val prefs = context.dpPrefs(PREFS)

    /** One-line summary of the stored key, without generating anything (safe on the main thread). */
    fun peekSummary(): String {
        val fp = prefs.getString(KEY_FP, null) ?: return "not generated yet"
        return "$fp (created ${prefs.getString(KEY_CREATED, "?")}, used ${prefs.getInt(KEY_USES, 0)}x)"
    }

    /**
     * Returns the persisted keypair, creating it only if none is stored. Process-wide lock, so the boot
     * service, the inline fallback and the UI can never race into generating two different keys.
     * Every call logs whether the key was REUSED (with a growing use counter) or NEWLY CREATED:
     * if you see "Generating a new key" after a reboot, persistence is the problem; if you see
     * "Reusing ... use #N" and adbd still prompts, adbd/framework is not keeping the key.
     */
    fun getOrCreate(log: (String) -> Unit): AdbKeyPair = synchronized(LOCK) {
        val existing = load(log)
        if (existing != null) {
            val uses = prefs.getInt(KEY_USES, 0) + 1
            prefs.edit().putInt(KEY_USES, uses).commit()
            log("Reusing persisted ADB key (created ${prefs.getString(KEY_CREATED, "?")}, use #$uses)")
            existing
        } else {
            log("Generating a NEW RSA-2048 ADB key (expected only on the very first run)…")
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            val kp = kpg.generateKeyPair()
            val pair = AdbKeyPair(kp.private as RSAPrivateCrtKey, kp.public as RSAPublicKey)
            save(pair)
            log("New key saved. Fingerprint ${AdbCrypto.fingerprint(pair.pub)}")
            pair
        }
    }

    /**
     * Imports an existing, already-trusted host key (e.g. Termux/PC ~/.android/adbkey, PEM, unencrypted).
     * Using it means adbd already knows the public key, so no prompt and no key upload are needed.
     */
    @Synchronized
    fun importPem(pem: String): AdbKeyPair {
        val pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")
        require(pkcs1 || pem.contains("BEGIN PRIVATE KEY")) { "Expected an unencrypted PEM private key" }
        val body = pem.lineSequence().filterNot { it.startsWith("-----") }
            .joinToString("").filterNot { it.isWhitespace() }
        var der = Base64.decode(body, Base64.DEFAULT)
        if (pkcs1) der = pkcs1ToPkcs8(der)
        val pair = parse(der)
        save(pair)
        return pair
    }

    private fun load(log: (String) -> Unit): AdbKeyPair? {
        val b64 = prefs.getString(KEY_PRIV, null) ?: return null
        return try {
            parse(Base64.decode(b64, Base64.DEFAULT))
        } catch (e: Exception) {
            // Keep the bad blob for inspection; a replacement key is unavoidable (and will prompt again).
            prefs.edit().putString("corrupt_" + KEY_PRIV, b64).commit()
            log("!! Stored ADB key is UNREADABLE (${e.javaClass.simpleName}: ${e.message}); generating a new one")
            null
        }
    }

    private fun parse(pkcs8: ByteArray): AdbKeyPair {
        val kf = KeyFactory.getInstance("RSA")
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey
        val pub = kf.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent)) as RSAPublicKey
        require(priv.modulus.bitLength() == 2048) { "Key must be RSA-2048" }
        return AdbKeyPair(priv, pub)
    }

    private fun save(pair: AdbKeyPair) = synchronized(LOCK) {
        val created = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val ok = prefs.edit()
            .putString(KEY_PRIV, Base64.encodeToString(pair.priv.encoded, Base64.NO_WRAP))
            .putString(KEY_FP, AdbCrypto.fingerprint(pair.pub))
            .putString(KEY_CREATED, created)
            .putInt(KEY_USES, 1)
            .commit()                                  // synchronous: must survive an immediate reboot
        check(ok) { "could not persist ADB key" }
    }

    private fun der(tag: Int, body: ByteArray): ByteArray {
        val n = body.size
        val len = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n <= 0xFF -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + len + body
    }

    private fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val rsaAlgId = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        )
        return der(0x30, version + rsaAlgId + der(0x04, pkcs1))
    }

    private companion object {
        const val PREFS = "adb_keys"
        const val KEY_PRIV = "private_key_pkcs8_b64"
        const val KEY_FP = "fingerprint"
        const val KEY_CREATED = "created_at"
        const val KEY_USES = "uses"
        val LOCK = Any()
    }
}

/* ------------------------------------------------------------------------------------------ *
 *  ADB wire protocol client (loopback, no TLS)
 * ------------------------------------------------------------------------------------------ */
class LocalAdbClient(
    context: Context,
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
    private val keyName: String = "mhtime@tv",
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : Closeable {

    private val keyStore = AdbKeyStore(context.applicationContext)
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null

    var deviceBanner: String = ""
        private set

    /** "signature" = trusted key, no prompt; "prompt" = user had to approve; "none" = auth disabled. */
    var authMode: String = "unknown"
        private set

    private class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** Opens the socket and completes the CNXN/AUTH handshake. Throws AdbException subclasses. */
    @Throws(IOException::class)
    fun connect() {
        val keys = keyStore.getOrCreate(log)
        AdbCrypto.validateStruct(AdbCrypto.encodePublicKeyStruct(keys.pub), keys.pub)
            ?.let { throw AdbException("Internal error: public key struct invalid: $it") }
        log("ADB key fingerprint: ${AdbCrypto.fingerprint(keys.pub)}")

        val s = Socket()
        s.tcpNoDelay = true
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            runCatching { s.close() }
            throw AdbException("Cannot reach adbd at $host:$port (${e.javaClass.simpleName}: ${e.message}). " +
                "Is 'ADB over network' enabled and adbd listening on TCP $port?", e)
        }
        s.soTimeout = IO_TIMEOUT_MS
        socket = s
        input = DataInputStream(s.getInputStream().buffered())
        output = s.getOutputStream()
        log("TCP connected to $host:$port")
        handshake(keys)
    }

    private fun handshake(keys: AdbKeyPair) {
        // Version 0x01000000 + 4 KiB payload: classic framing, no TLS upgrade (A_STLS) requested.
        send(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray(Charsets.US_ASCII))
        var signatureSent = false
        var keySent = false
        try {
            while (true) {
                val m = readMessage()
                when (m.command) {
                    A_CNXN -> {
                        deviceBanner = String(m.data, Charsets.US_ASCII).trimEnd('\u0000')
                        authMode = if (keySent) "prompt" else if (signatureSent) "signature" else "none"
                        log(
                            when (authMode) {
                                "prompt" -> "Authorized via on-screen prompt (adbd did NOT recognise our key). Device: $deviceBanner"
                                "signature" -> "Authorized by stored key (no prompt). Device: $deviceBanner"
                                else -> "Connected without authentication. Device: $deviceBanner"
                            }
                        )
                        socket?.soTimeout = IO_TIMEOUT_MS
                        return
                    }
                    A_AUTH -> {
                        if (m.arg0 != AUTH_TOKEN) throw AdbException("Unexpected AUTH type ${m.arg0}")
                        if (m.data.size != 20) log("Warning: AUTH token is ${m.data.size} bytes (expected 20)")
                        when {
                            !signatureSent -> {
                                val sig = AdbCrypto.signToken(keys, m.data, log)
                                log("AUTH TOKEN received -> sending AUTH_SIGNATURE (${sig.size} bytes)")
                                send(A_AUTH, AUTH_SIGNATURE, 0, sig)
                                signatureSent = true
                            }
                            !keySent -> {
                                val payload = AdbCrypto.publicKeyPayload(keys.pub, keyName)
                                log("Signature not accepted (key unknown to adbd) -> sending AUTH_RSAPUBLICKEY " +
                                    "(${payload.size} bytes). Approve the dialog on the TV " +
                                    "(tick 'Always allow'); waiting up to ${AUTH_PROMPT_TIMEOUT_MS / 1000}s…")
                                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, payload)
                                keySent = true
                                socket?.soTimeout = AUTH_PROMPT_TIMEOUT_MS
                            }
                            else -> throw AdbAuthException(
                                "adbd asked for a token again after the public key was sent: key rejected " +
                                    "(user pressed Deny, or adbd could not parse/store the key)."
                            )
                        }
                    }
                    A_STLS -> throw AdbException(
                        "adbd demands a TLS upgrade (A_STLS). This client speaks plain ADB 0x01000000 only."
                    )
                    else -> throw AdbException("Unexpected ${cmdName(m.command)} during handshake")
                }
            }
        } catch (e: SocketTimeoutException) {
            if (keySent) throw AdbAuthException("No answer to the authorization prompt within " +
                "${AUTH_PROMPT_TIMEOUT_MS / 1000}s (nobody approved it on the TV).")
            throw AdbException("adbd did not answer during handshake", e)
        } catch (e: EOFException) {
            if (keySent) throw AdbAuthException("adbd closed the connection after receiving the public key " +
                "(prompt denied/dismissed, or key rejected as invalid).")
            throw AdbException("adbd closed the connection during handshake", e)
        }
    }

    /**
     * Runs `shell:<command>` (legacy raw shell, no shell_v2) and returns everything it printed.
     * [commandProvider] is evaluated AFTER the connection is authorised, so a timestamp embedded in the
     * command is as fresh as possible even if the authorisation prompt took a while.
     */
    @Throws(IOException::class)
    fun shell(commandProvider: () -> String): String {
        val command = commandProvider()
        log("shell: $command")
        val localId = 1
        send(A_OPEN, localId, 0, "shell:$command\u0000".toByteArray(Charsets.UTF_8))
        var opened = false
        val out = ByteArrayOutputStream()
        val deadline = SystemClock.elapsedRealtime() + SHELL_TIMEOUT_MS
        loop@ while (true) {
            if (SystemClock.elapsedRealtime() > deadline) throw AdbException("shell command timed out")
            val m = readMessage()
            when (m.command) {
                A_OKAY -> opened = true
                A_WRTE -> {
                    opened = true
                    out.write(m.data)
                    send(A_OKAY, localId, m.arg0)
                }
                A_CLSE -> {
                    if (!opened) throw AdbException("adbd refused the shell service")
                    runCatching { send(A_CLSE, localId, m.arg0) }
                    break@loop
                }
                else -> log("Ignoring ${cmdName(m.command)} in shell stream")
            }
        }
        val text = out.toString("UTF-8").trim()
        log("shell output: ${if (text.isEmpty()) "<empty>" else text}")
        return text
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    /* ---- packet I/O ---- */

    @Synchronized
    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray = EMPTY) {
        val out = output ?: throw AdbException("not connected")
        var sum = 0
        for (b in data) sum += (b.toInt() and 0xFF)
        val bb = ByteBuffer.allocate(HEADER_SIZE + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(command).putInt(arg0).putInt(arg1).putInt(data.size).putInt(sum).putInt(command.inv())
        bb.put(data)
        out.write(bb.array())
        out.flush()
    }

    private fun readMessage(): Message {
        val inp = input ?: throw AdbException("not connected")
        val h = ByteArray(HEADER_SIZE)
        inp.readFully(h)
        val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val command = bb.getInt()
        val arg0 = bb.getInt()
        val arg1 = bb.getInt()
        val length = bb.getInt()
        bb.getInt() // data checksum (not enforced for incoming packets)
        val magic = bb.getInt()
        if (magic != command.inv()) throw AdbException("Bad packet magic (not an ADB peer?)")
        if (length < 0 || length > MAX_ACCEPT) throw AdbException("Bad packet length $length")
        val data = ByteArray(length)
        if (length > 0) inp.readFully(data)
        return Message(command, arg0, arg1, data)
    }

    private fun cmdName(c: Int): String = when (c) {
        A_CNXN -> "CNXN"; A_AUTH -> "AUTH"; A_OPEN -> "OPEN"; A_OKAY -> "OKAY"
        A_CLSE -> "CLSE"; A_WRTE -> "WRTE"; A_STLS -> "STLS"
        else -> "0x%08X".format(c)
    }

    companion object {
        private const val TAG = "TvTimeSync/ADB"
        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257
        private const val A_STLS = 0x534c5453
        private const val A_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 4096
        private const val MAX_ACCEPT = 1024 * 1024
        private const val HEADER_SIZE = 24
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val IO_TIMEOUT_MS = 10_000
        private const val AUTH_PROMPT_TIMEOUT_MS = 60_000
        private const val SHELL_TIMEOUT_MS = 15_000L
        private val EMPTY = ByteArray(0)
    }
}
