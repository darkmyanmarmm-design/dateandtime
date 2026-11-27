package com.mhtime.tvauto

import android.app.AlarmManager
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeSyncHelper {

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val epochMillis: Long?
    )

    private val endpoints = listOf(
        "https://www.google.com",
        "https://www.cloudflare.com",
        "https://1.1.1.1",
        "https://www.apple.com"
    )

    fun fetchHttpsEpochMillis(): Pair<Long?, String> {
        var lastError = "No internet or HTTPS blocked"

        for (urlStr in endpoints) {
            for (method in listOf("HEAD", "GET")) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        setRequestProperty("Connection", "close")
                    }
                    conn.connect()
                    val dateVal = conn.date
                    if (dateVal > 1_700_000_000_000L) {
                        return dateVal to urlStr.removePrefix("https://")
                    } else {
                        lastError = "No Date header (HTTP ${conn.responseCode})"
                    }
                } catch (e: Exception) {
                    lastError = "${e.javaClass.simpleName}: ${e.message ?: "error"}"
                } finally {
                    try {
                        conn?.disconnect()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return null to lastError
    }

    fun syncSystemTimeFromHttps(
        context: Context,
        onPromptWaiting: (() -> Unit)? = null
    ): SyncResult {
        val (millis, fetchDetail) = fetchHttpsEpochMillis()
        if (millis == null) {
            return SyncResult(false, "HTTPS fetch failed ($fetchDetail)", null)
        }

        // 1. Try direct AlarmManager.setTime first
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                alarmManager.setTime(millis)
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
                return SyncResult(true, "Synced ($fetchDetail): $formatted", millis)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        // 2. Local loopback ADB (127.0.0.1:5555)
        val timeZoneId = TimeZone.getDefault().id
        val legacyDateStr = SimpleDateFormat("MMddHHmmyyyy.ss", Locale.US).format(Date(millis))
        val cmd = "settings put global auto_time 0 && " +
            "settings put global auto_time_zone 0 && " +
            "cmd alarm set-timezone $timeZoneId ; " +
            "(cmd alarm set-time $millis || date $legacyDateStr) && " +
            "date"

        val adbRes = LocalAdbClient.runShell(
            context = context,
            shellCommand = cmd,
            port = 5555,
            onPromptWaiting = onPromptWaiting
        )

        return if (adbRes.success) {
            val tvDate = adbRes.output.trim().lines().lastOrNull()?.trim().orEmpty()
            SyncResult(true, "ADB Synced ($fetchDetail): ${tvDate.ifEmpty { "OK" }}", millis)
        } else {
            SyncResult(false, "Local ADB failed: ${adbRes.error ?: "error"}", millis)
        }
    }

    fun formatCurrentDeviceClock(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
