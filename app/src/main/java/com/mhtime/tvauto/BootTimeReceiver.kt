package com.mhtime.tvauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Starts a clock sync after boot. Both LOCKED_BOOT_COMPLETED and BOOT_COMPLETED are handled. */
class BootTimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        SyncStatus.log("Boot broadcast received: $action")

        try {
            TimeSyncService.start(context, reason = action, force = false, attempts = BOOT_ATTEMPTS)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException on a strict firmware: do a single quick try inline.
            SyncStatus.log("Foreground service start refused (${e.javaClass.simpleName}); running inline")
            val pending = goAsync()
            val app = context.applicationContext
            thread(name = "tv-time-sync-inline") {
                try {
                    TimeSyncService.performSync(app, action, false, 1)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private companion object {
        const val BOOT_ATTEMPTS = 10   // ~2 minutes of backoff while network and adbd come up
        val ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
