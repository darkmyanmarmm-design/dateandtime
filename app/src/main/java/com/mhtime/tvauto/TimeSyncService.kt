package com.mhtime.tvauto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class TimeSyncService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(101, buildNotification())

        executor.execute {
            try {
                for (attempt in 1..30) {
                    val res = TimeSyncHelper.syncSystemTimeFromHttps(applicationContext)
                    if (res.success) {
                        break
                    }
                    Thread.sleep(3000L)
                }
            } catch (_: Exception) {
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "mhtime_boot_sync"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "MH Time Boot Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("MH Time TV Auto")
            .setContentText("Syncing system time via HTTPS...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
