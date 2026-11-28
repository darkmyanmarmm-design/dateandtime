package com.mhtime.tvauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootTimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val serviceIntent = Intent(context, TimeSyncService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
        }
    }
}
