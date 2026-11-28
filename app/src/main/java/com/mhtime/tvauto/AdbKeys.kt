package com.mhtime.tvauto

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec

/**
 * Self-contained ADB key handling. Rules this file enforces:
 *  1. ONE storage location, always device-protected storage, always the application context.
 *  2. A stored key is NEVER replaced because of a decode problem (that silently creates a new identity
 *     and a new "Allow USB debugging?" prompt). Corruption throws; call resetKey() on purpose.
 *  3. Writes use commit() and are read back, so a failed write is an error, not a silent regeneration.
 *  4. peekFingerprint() only reads. It never generates or writes anything.
 *  5. The public-key line is: standard Base64 of the 524-byte struct (no wraps, no newline), one space,
 *     a printable-ASCII name, one NUL. adbd/framework add the newline when writing adb_keys.
 */
object AdbKeys {
    private const val PREFS = "adb_keys"
    private const val K_PRIV = "private_key_pkcs8_b64"
    private const val STRUCT_SIZE = 524
    private const val WORDS = 64
    private const val MOD_BYTES = 256

    private val LOCK = Any()
    private val TWO_32: BigInteger = BigInteger.ONE.shiftLeft(32)

    /** DigestInfo prefix for SHA-1; adbd verifies the 20-byte token as if it were a SHA-1 digest. */
    private val SHA1_DIGEST_INFO = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    )

    private fun prefs(ctx: Context): SharedPreferences {
        val app = ctx.applicationContext ?: ctx
        val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) app.createDeviceProtectedStorageContext() else app
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /* ------------------------------ keypair persistence ------------------------------ */

    fun getOrCreateKeyPair(ctx: Context): KeyPair = synchronized(LOCK) {
        val p = prefs(ctx)
        val stored = p.getString(K_PRIV, null)
        if (stored != null) {
            decode(stored)                                   // throws on corruption, never regenerates
        } else {
            val kp = generate()
            val ok = p.edit()
                .putString(K_PRIV, Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
                .commit()                                    // synchronous, survives an immediate reboot
            check(ok) { "Could not persist the ADB key (commit() returned false)" }
            decode(p.getString(K_PRIV, null) ?: error("ADB key vanished right after commit()"))
        }
    }

    /** Read-only. Safe to call from the UI thread on every redraw. */
    fun peekFingerprint(ctx: Context): String? {
        val stored = prefs(ctx).getString(K_PRIV, null) ?: return null
        return try {
            fingerprint(decode(stored).public as RSAPublicKey)
        } catch (e: Exception) {
            "UNREADABLE"
        }
    }

    /** Deliberate reset only (this WILL cause one new prompt). */
    fun resetKey(ctx: Context) {
        synchronized(LOCK) { prefs(ctx).edit().remove(K_PRIV).commit() }
    }

    private fun generate(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
        return kpg.generateKeyPair()
    }

    private fun decode(b64: String): KeyPair {
        try {
            val kf = KeyFactory.getInstance("RSA")
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT))) as RSAPrivateCrtKey
            val pub = kf.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent)) as RSAPublicKey
            require(pub.modulus.bitLength() == 2048) { "key is ${pub.modulus.bitLength()} bits, ADB needs 2048" }
            return KeyPair(pub, priv)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Stored ADB key is unreadable (${e.message}); refusing to replace it silently. " +
                    "Call AdbKeys.resetKey() if you really want a new one.", e
            )
        }
    }

    /* ------------------------------ public key encoding ------------------------------ */

    /** struct RSAPublicKey { u32 len=64; u32 n0inv; u8 n[256]; u8 rr[256]; u32 exponent; } little-endian. */
    fun encodeStruct(pub: RSAPublicKey): ByteArray {
        val n = pub.modulus
        require(n.bitLength() == 2048) { "ADB needs RSA-2048" }
        val n0inv = TWO_32.subtract(n.mod(TWO_32).modInverse(TWO_32)).toInt()      // -(n^-1) mod 2^32
        val rr = BigInteger.ONE.shiftLeft(2 * 2048).mod(n)                          // R^2 mod n, R = 2^2048
        val bb = ByteBuffer.allocate(STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(WORDS)
        bb.putInt(n0inv)
        bb.put(toBigEndian(n, MOD_BYTES).reversedArray())                           // LE byte order
        bb.put(toBigEndian(rr, MOD_BYTES).reversedArray())
        bb.putInt(pub.publicExponent.toInt())
        check(!bb.hasRemaining()) { "struct is not $STRUCT_SIZE bytes" }
        return bb.array()
    }

    /** Payload for AUTH_RSAPUBLICKEY (type 3). [userName]: printable ASCII, no spaces. */
    fun encodeRsaPublicKey(pub: RSAPublicKey, userName: String = "mhtime@tv"): ByteArray {
        require(userName.isNotEmpty() && userName.all { it.code in 0x21..0x7E }) {
            "userName must be printable ASCII without spaces or control characters"
        }
        val b64 = Base64.encodeToString(encodeStruct(pub), Base64.NO_WRAP)             // NO_WRAP: no "\n" every 76 chars
        check(b64.length == 700 && '\n' !in b64 && '\r' !in b64) { "unexpected base64 (${b64.length} chars)" }
        return "$b64 $userName\u0000".toByteArray(Charsets.US_ASCII)                   // single space, trailing NUL, no newline
    }

    /** Same fingerprint the "Allow USB debugging?" dialog shows. */
    fun fingerprint(pub: RSAPublicKey): String =
        MessageDigest.getInstance("MD5").digest(encodeStruct(pub)).joinToString(":") { "%02X".format(it) }

    /* ------------------------------ AUTH token signature ------------------------------ */

    /** Signature for AUTH_SIGNATURE (type 2). Self-verified; manual RSA fallback if the provider misbehaves. */
    fun signToken(kp: KeyPair, token: ByteArray): ByteArray {
        val priv = kp.private as RSAPrivateCrtKey
        val pub = kp.public as RSAPublicKey
        val payload = SHA1_DIGEST_INFO + token
        val expected = pkcs1Type1(payload)
        try {
            val sig = Signature.getInstance("NONEwithRSA").run {
                initSign(priv); update(payload); sign()
            }
            if (verifies(sig, pub, expected)) return sig
        } catch (_: Exception) {
            // fall through to manual RSA
        }
        return toBigEndian(BigInteger(1, expected).modPow(priv.privateExponent, priv.modulus), MOD_BYTES)
    }

    private fun verifies(sig: ByteArray, pub: RSAPublicKey, expected: ByteArray): Boolean =
        toBigEndian(BigInteger(1, sig).modPow(pub.publicExponent, pub.modulus), MOD_BYTES).contentEquals(expected)

    private fun pkcs1Type1(payload: ByteArray): ByteArray {
        val em = ByteArray(MOD_BYTES) { 0xFF.toByte() }
        em[0] = 0x00
        em[1] = 0x01
        em[MOD_BYTES - payload.size - 1] = 0x00
        System.arraycopy(payload, 0, em, MOD_BYTES - payload.size, payload.size)
        return em
    }

    private fun toBigEndian(v: BigInteger, len: Int): ByteArray {
        val raw = v.toByteArray()
        val start = maxOf(0, raw.size - len)
        for (i in 0 until start) require(raw[i] == 0.toByte()) { "value does not fit in $len bytes" }
        val out = ByteArray(len)
        System.arraycopy(raw, start, out, len - (raw.size - start), raw.size - start)
        return out
    }

    /* ------------------------------ on-device self test ------------------------------ */

    /** Call from the button handler (background thread) and log the result. */
    fun selfTest(ctx: Context): String {
        val k1 = getOrCreateKeyPair(ctx)
        val k2 = getOrCreateKeyPair(ctx)
        val pub = k1.public as RSAPublicKey
        val same = fingerprint(pub) == fingerprint(k2.public as RSAPublicKey)
        val line = String(encodeRsaPublicKey(pub, "selftest@tv"), Charsets.US_ASCII)
        val token = ByteArray(20) { it.toByte() }
        val sigOk = verifies(signToken(k1, token), pub, pkcs1Type1(SHA1_DIGEST_INFO + token))
        return buildString {
            appendLine("fingerprint      : ${fingerprint(pub)}")
            appendLine("same key 2 calls : $same")
            appendLine("struct bytes     : ${encodeStruct(pub).size} (expect 524)")
            appendLine("base64 chars     : ${line.substringBefore(' ').length} (expect 700)")
            appendLine("ends with NUL    : ${line.endsWith("\u0000")}, newline inside: ${'\n' in line}")
            appendLine("token signature  : ${if (sigOk) "verifies" else "FAILS"}")
        }
    }
}
