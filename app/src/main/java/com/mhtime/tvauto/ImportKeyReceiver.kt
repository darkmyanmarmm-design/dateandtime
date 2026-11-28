package com.mhtime.tvauto

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64

/**
 * Imports an ADB private key that adbd already trusts (e.g. the one Termux/PC uses), which avoids the
 * public-key upload and the prompt entirely:
 *
 *   adb shell am broadcast -n com.mhtime.tvauto/.ImportKeyReceiver \
 *       --es pem_b64 "$(base64 -w0 ~/.android/adbkey)"
 */
class ImportKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val b64 = intent.getStringExtra("pem_b64") ?: error("missing extra pem_b64")
            val pem = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            val pair = AdbKeyStore(context.applicationContext).importPem(pem)
            val msg = "Imported ADB key, fingerprint ${AdbCrypto.fingerprint(pair.pub)}"
            SyncStatus.log(msg)
            setResult(Activity.RESULT_OK, msg, null)
        } catch (e: Exception) {
            val msg = "Key import failed: ${e.javaClass.simpleName}: ${e.message}"
            SyncStatus.log(msg)
            setResult(Activity.RESULT_CANCELED, msg, null)
        }
    }
}
