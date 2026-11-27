package com.mhtime.tvauto

import android.app.AlarmManager
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeSyncHelper {

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val epochMillis: Long?
    )

    private val endpoints = listOf(
        "https://1.1.1.1",
        "https://www.google.com",
        "https://www.cloudflare.com"
    )

    fun fetchHttpsEpochMillis(): Long? {
        for (urlStr in endpoints) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 3000
                    readTimeout = 3000
                    instanceFollowRedirects = false
                }
                conn.connect()
                val dateHeader = conn.date
                conn.disconnect()
                if (dateHeader > 1_700_000_000_000L) {
                    return dateHeader
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun syncSystemTimeFromHttps(context: Context): SyncResult {
        val millis = fetchHttpsEpochMillis()
            ?: return SyncResult(false, "HTTPS time fetch failed", null)

        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return SyncResult(false, "AlarmManager unavailable", millis)
            alarmManager.setTime(millis)
            val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
            SyncResult(true, "Synced: $formatted", millis)
        } catch (_: SecurityException) {
            SyncResult(
                false,
                "Missing SET_TIME permission (grant via ADB or use phone ADB sync)",
                millis
            )
        } catch (e: Exception) {
            SyncResult(false, e.message ?: "Failed to set system time", millis)
        }
    }

    fun formatCurrentDeviceClock(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
