package com.mhtime.tvauto

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

object LocalAdbClient {

    private const val PREFS_NAME = "mhtime_tv_local_adb_v2"
    private const val KEY_PRIV = "priv_der"
    private const val KEY_PUB = "pub_wire"

    private const val CMD_CNXN = 0x4e584e43
    private const val CMD_AUTH = 0x48545541
    private const val CMD_OPEN = 0x4e45504f
    private const val CMD_OKAY = 0x59414b4f
    private const val CMD_CLSE = 0x45534c43
    private const val CMD_WRTE = 0x45545257

    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3

    data class ShellResult(
        val success: Boolean,
        val output: String,
        val error: String?
    )

    fun runShell(
        context: Context,
        shellCommand: String,
        port: Int = 5555,
        onPromptWaiting: (() -> Unit)? = null
    ): ShellResult {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 6000
                socket.connect(InetSocketAddress("127.0.0.1", port), 3500)

                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                val banner = "host::product=mhtime_tvauto\u0000".toByteArray(StandardCharsets.UTF_8)
                writePacket(out, CMD_CNXN, 0x01000000, 4096, banner)

                var reply = readPacket(inp) ?: return ShellResult(false, "", "No CNXN reply")
                var sentSignature = false

                while (reply.command == CMD_AUTH) {
                    val (privKey, pubWire) = getOrCreateKeyPair(context)
                    if (reply.arg0 == AUTH_TOKEN && !sentSignature) {
                        sentSignature = true
                        val sig = signToken(privKey, reply.payload)
                        writePacket(out, CMD_AUTH, AUTH_SIGNATURE, 0, sig)
                    } else {
                        writePacket(out, CMD_AUTH, AUTH_RSAPUBLICKEY, 0, pubWire)
                        onPromptWaiting?.invoke()
                    }
                    reply = readPacket(inp) ?: return ShellResult(
                        false,
                        "",
                        "ADB authorization timeout (allow prompt on TV)"
                    )
                }

                if (reply.command != CMD_CNXN) {
                    return ShellResult(false, "", "ADB handshake failed")
                }

                val localId = 1
                val dest = "shell:$shellCommand\u0000".toByteArray(StandardCharsets.UTF_8)
                writePacket(out, CMD_OPEN, localId, 0, dest)

                val outBuf = ByteArrayOutputStream()
                var remoteId = 0

                while (true) {
                    val pkt = readPacket(inp) ?: break
                    when (pkt.command) {
                        CMD_OKAY -> if (remoteId == 0) remoteId = pkt.arg0
                        CMD_WRTE -> {
                            if (remoteId == 0) remoteId = pkt.arg0
                            if (pkt.payload.isNotEmpty()) outBuf.write(pkt.payload)
                            writePacket(out, CMD_OKAY, localId, remoteId, ByteArray(0))
                        }
                        CMD_CLSE -> {
                            writePacket(out, CMD_CLSE, localId, pkt.arg0, ByteArray(0))
                            break
                        }
                    }
                }

                ShellResult(
                    success = true,
                    output = outBuf.toString(StandardCharsets.UTF_8.name()),
                    error = null
                )
            }
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "127.0.0.1:$port connection error")
        }
    }

    private fun getOrCreateKeyPair(context: Context): Pair<PrivateKey, ByteArray> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPriv = prefs.getString(KEY_PRIV, null)
        val savedPub = prefs.getString(KEY_PUB, null)

        if (savedPriv != null && savedPub != null) {
            try {
                val privBytes = Base64.decode(savedPriv, Base64.NO_WRAP)
                val pubBytes = Base64.decode(savedPub, Base64.NO_WRAP)
                val privKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(privBytes))
                return privKey to pubBytes
            } catch (_: Exception) {
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val privBytes = kp.private.encoded
        val pubWire = encodeRsaPublicKey(kp.public as RSAPublicKey, "mhtime@tv")

        prefs.edit()
            .putString(KEY_PRIV, Base64.encodeToString(privBytes, Base64.NO_WRAP))
            .putString(KEY_PUB, Base64.encodeToString(pubWire, Base64.NO_WRAP))
            .apply()

        return kp.private to pubWire
    }

    private fun encodeRsaPublicKey(pubKey: RSAPublicKey, userName: String): ByteArray {
        val nWords = 64
        val mod32 = BigInteger.ONE.shiftLeft(32)
        val n = pubKey.modulus
        val r = BigInteger.ONE.shiftLeft(nWords * 32)
        val rr = r.multiply(r).mod(n)

        val nInvMod32 = n.mod(mod32).modInverse(mod32)
        val n0inv = mod32.subtract(nInvMod32).mod(mod32)

        // Android adbd RSAPublicKey struct: 1 (nWords) + 1 (n0inv) + 64 (n) + 64 (rr) + 1 (exponent) = 131 int32s (524 bytes)
        val buf = ByteBuffer.allocate((1 + 1 + nWords * 2 + 1) * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(nWords)
        buf.putInt(n0inv.toInt())

        var tempN = n
        for (i in 0 until nWords) {
            val divRem = tempN.divideAndRemainder(mod32)
            buf.putInt(divRem[1].toInt())
            tempN = divRem[0]
        }

        var tempRr = rr
        for (i in 0 until nWords) {
            val divRem = tempRr.divideAndRemainder(mod32)
            buf.putInt(divRem[1].toInt())
            tempRr = divRem[0]
        }

        buf.putInt(pubKey.publicExponent.toInt())

        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "$b64 $userName\u0000".toByteArray(StandardCharsets.UTF_8)
    }

    private fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        val signature = Signature.getInstance("NONEwithRSA")
        signature.initSign(privateKey)
        signature.update(token)
        return signature.sign()
    }

    private fun writePacket(
        out: DataOutputStream,
        cmd: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray
    ) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(cmd)
            putInt(arg0)
            putInt(arg1)
            putInt(payload.size)
            putInt(checksum(payload))
            putInt(cmd xor -0x1)
        }.array()
        out.write(header)
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }

    private fun readPacket(inp: DataInputStream): AdbPacket? {
        val header = ByteArray(24)
        inp.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val len = buf.int
        buf.int
        buf.int
        if (len !in 0..1_048_576) return null
        val payload = if (len > 0) ByteArray(len).also { inp.readFully(it) } else ByteArray(0)
        return AdbPacket(cmd, arg0, arg1, payload)
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += (b.toInt() and 0xFF)
        return sum
    }

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray
    )
}
