package com.mhtime.tvauto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

class TimeSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()                       // must happen within ~5 s of startForegroundService()

        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "restart"
        val force = intent?.getBooleanExtra(EXTRA_FORCE, false) ?: false
        val attempts = intent?.getIntExtra(EXTRA_ATTEMPTS, 2) ?: 2

        if (!RUNNING.compareAndSet(false, true)) {
            SyncStatus.log("A sync is already running; ignoring request ($reason)")
            return START_NOT_STICKY
        }
        thread(name = "tv-time-sync") {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvauto:sync").apply {
                setReferenceCounted(false)
                acquire(5 * 60 * 1000L)
            }
            try {
                performSync(applicationContext, reason, force, attempts)
            } catch (t: Throwable) {
                SyncStatus.finish(applicationContext, false, "Unexpected error: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                if (wl.isHeld) wl.release()
                RUNNING.set(false)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_FORCE = "force"
        const val EXTRA_ATTEMPTS = "attempts"
        private const val CHANNEL_ID = "clock_sync"
        private const val NOTIFICATION_ID = 42
        private const val IN_SYNC_THRESHOLD_MS = 1_500L
        private val RUNNING = AtomicBoolean(false)

        fun start(ctx: Context, reason: String, force: Boolean = false, attempts: Int = 2) {
            val i = Intent(ctx, TimeSyncService::class.java)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_FORCE, force)
                .putExtra(EXTRA_ATTEMPTS, attempts)
            ContextCompat.startForegroundService(ctx, i)
        }

        private fun backoffMs(attempt: Int) = (2_000L * attempt).coerceAtMost(20_000L)
        private fun sleepQuiet(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

        private fun setTimeCommand(epochMs: Long) =
            "settings put global auto_time 0; cmd alarm set-time $epochMs; echo RC=\$?"

        /**
         * Blocking: fetch time over HTTP(S) -> (unless already in sync) set clock through local adbd -> verify.
         * Call from a background thread only.
         */
        fun performSync(ctx: Context, reason: String, force: Boolean, maxAttempts: Int): Boolean {
            val app = ctx.applicationContext
            val attempts = maxAttempts.coerceAtLeast(1)
            SyncStatus.begin("Sync started ($reason, force=$force, attempts=$attempts)")

            // 1) network time (the network / DNS is often not ready right after boot -> retry with backoff)
            var fetched: TimeFetcher.Result? = null
            for (i in 1..attempts) {
                SyncStatus.setState("Fetching network time ($i/$attempts)…")
                fetched = TimeFetcher.fetch { SyncStatus.log(it) }
                if (fetched != null) break
                if (i < attempts) sleepQuiet(backoffMs(i))
            }
            val time = fetched ?: run {
                SyncStatus.finish(app, false, "Could not read a Date header from any HTTP(S) source")
                return false
            }
            val drift = time.nowEpochMs() - System.currentTimeMillis()
            SyncStatus.log("Network time is ${drift} ms ahead of the TV clock (source ${time.source})")
            if (!force && abs(drift) < IN_SYNC_THRESHOLD_MS) {
                SyncStatus.finish(app, true, "Clock already in sync (drift ${drift} ms)")
                return true
            }

            // 2) set the clock through adbd on loopback (adbd may still be starting at boot -> retry)
            var lastError = "unknown error"
            for (i in 1..attempts) {
                SyncStatus.setState("Setting clock via ADB ($i/$attempts)…")
                try {
                    var output = ""
                    LocalAdbClient(app, log = { SyncStatus.log(it) }).use { adb ->
                        adb.connect()
                        output = adb.shell { setTimeCommand(time.nowEpochMs()) }
                    }
                    sleepQuiet(800)
                    val err = System.currentTimeMillis() - time.nowEpochMs()
                    return if (abs(err) <= 3_000) {
                        SyncStatus.finish(app, true, "Clock set from ${time.source} (was ${-drift} ms off, now ${err} ms)")
                        true
                    } else {
                        SyncStatus.finish(app, false, "Command ran but clock is still ${err} ms off. adbd said: $output")
                        false
                    }
                } catch (e: AdbAuthException) {
                    SyncStatus.finish(app, false, e.message ?: "ADB authorization failed")
                    return false                // do not retry: every retry could raise another prompt
                } catch (e: Exception) {
                    lastError = "${e.javaClass.simpleName}: ${e.message}"
                    SyncStatus.log("ADB attempt $i failed: $lastError")
                    if (i < attempts) sleepQuiet(backoffMs(i))
                }
            }
            SyncStatus.finish(app, false, "ADB failed: $lastError")
            return false
        }
    }
}
