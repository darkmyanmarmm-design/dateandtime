package com.mhtime.tvauto

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TimeSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            val epochMillis = fetchHttpDateMillis("http://google.com")
            if (epochMillis != null && epochMillis > 0L) {
                LocalAdbClient.runShell(
                    context = applicationContext,
                    shellCommand = "settings put global auto_time 0; cmd alarm set-time $epochMillis"
                )
            }
            stopSelfResult(startId)
        }.start()
        return START_NOT_STICKY
    }

    private fun fetchHttpDateMillis(urlString: String = "http://google.com"): Long? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
            }
            conn.connect()

            val dateHeader = conn.getHeaderField("Date")
            if (!dateHeader.isNullOrBlank()) {
                val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                val parsed = fmt.parse(dateHeader)
                if (parsed != null) return parsed.time
            }

            val headerDate = conn.headerFieldDate
            if (headerDate > 0L) return headerDate

            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
