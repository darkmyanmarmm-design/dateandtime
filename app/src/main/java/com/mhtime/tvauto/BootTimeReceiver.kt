package com.mhtime.tvauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class BootTimeReceiver : BroadcastReceiver() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        executor.execute {
            try {
                for (attempt in 1..15) {
                    val result = TimeSyncHelper.syncSystemTimeFromHttps(context)
                    if (result.success) {
                        break
                    }
                    Thread.sleep(2000L)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
