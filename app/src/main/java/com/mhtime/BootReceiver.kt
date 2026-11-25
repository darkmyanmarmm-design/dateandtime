package com.mhtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == ConnectivityManager.CONNECTIVITY_ACTION
        ) {
            Handler(Looper.getMainLooper()).postDelayed({
                syncFastWithRetry(context, attemptsLeft = 5) {}
            }, 2_000L)
        }
    }

    companion object {
        fun formatCurrentDateTime(timeZoneId: String = "Asia/Yangon"): String {
            val sdf = SimpleDateFormat("dd MMM yyyy  |  HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(timeZoneId)
            }
            return sdf.format(Date())
        }

        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun syncFastWithRetry(
            context: Context,
            attemptsLeft: Int = 5,
            onResult: (String) -> Unit
        ) {
            if (!isNetworkConnected(context)) {
                if (attemptsLeft > 1) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        syncFastWithRetry(context, attemptsLeft - 1, onResult)
                    }, 3_000L)
                } else {
                    onResult("Waiting for network...")
                }
                return
            }

            syncNow(context) { status ->
                val needsRetry = status.contains("HTTPS unreachable") && attemptsLeft > 1
                if (needsRetry) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        syncFastWithRetry(context, attemptsLeft - 1, onResult)
                    }, 3_000L)
                } else {
                    onResult(status)
                }
            }
        }

        fun syncNow(context: Context, onResult: (String) -> Unit) {
            thread {
                val results = mutableListOf<String>()

                val hasWriteSecure = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_SECURE_SETTINGS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasWriteSecure) {
                    try {
                        val resolver = context.contentResolver
                        Settings.Global.putString(resolver, "ntp_server", "pool.ntp.org")
                        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, 0)
                        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, 1)
                        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME_ZONE, 1)
                        results.add("AutoTime refreshed")
                    } catch (e: Exception) {
                        results.add("SecureSettings err: ${e.message}")
                    }
                }

                val epochMillis = fetchHttpsEpochMillis()
                if (epochMillis != null) {
                    results.add("HTTPS time OK")
                    if (setSystemTimeWithRoot(epochMillis)) {
                        results.add("Root clock set")
                    } else if (!hasWriteSecure) {
                        results.add("Grant WRITE_SECURE_SETTINGS via ADB")
                    }
                } else {
                    results.add("HTTPS unreachable")
                }

                val summary = results.joinToString(" • ")
                Handler(Looper.getMainLooper()).post {
                    onResult(summary)
                }
            }
        }

        private fun fetchHttpsEpochMillis(): Long? {
            val endpoints = listOf(
                "https://www.google.com",
                "https://1.1.1.1",
                "https://cloudflare.com",
                "https://www.apple.com"
            )
            for (endpoint in endpoints) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        connectTimeout = 3000
                        readTimeout = 3000
                        instanceFollowRedirects = true
                    }
                    conn.connect()
                    val dateHeader = conn.getHeaderField("Date")
                    if (!dateHeader.isNullOrBlank()) {
                        val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("GMT")
                        }
                        val parsed = parser.parse(dateHeader)
                        if (parsed != null) return parsed.time
                    }
                } catch (_: Exception) {
                    // Try next endpoint
                } finally {
                    conn?.disconnect()
                }
            }
            return null
        }

        private fun setSystemTimeWithRoot(epochMillis: Long): Boolean {
            return try {
                val toyboxFmt = SimpleDateFormat("MMddHHmmyy.ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val dateStr = toyboxFmt.format(epochMillis)
                val epochSec = epochMillis / 1000L

                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("settings put global auto_time 0\n")
                os.writeBytes("date -u @$epochSec || date $dateStr\n")
                os.writeBytes("hwclock -w || true\n")
                os.writeBytes("exit\n")
                os.flush()
                process.waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }
    }
}
