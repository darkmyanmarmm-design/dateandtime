package com.mhtime.tvauto

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/** SharedPreferences in device-protected storage: readable at LOCKED_BOOT_COMPLETED too. */
fun Context.dpPrefs(name: String): SharedPreferences {
    val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) createDeviceProtectedStorageContext() else this
    return c.getSharedPreferences(name, Context.MODE_PRIVATE)
}

/** In-process status + log shared between TimeSyncService and MainActivity. */
object SyncStatus {
    fun interface Listener { fun onSyncStatusChanged() }

    private const val TAG = "TvTimeSync"
    private const val PREFS = "sync_status"
    private const val MAX_LINES = 80

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val buffer = ArrayDeque<String>()
    private val stamp get() = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile var state: String = "Idle"; private set
    @Volatile var busy: Boolean = false; private set

    val lines: List<String> get() = synchronized(buffer) { buffer.toList() }

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun log(msg: String) {
        Log.i(TAG, msg)
        synchronized(buffer) {
            buffer.addLast("${stamp.format(Date())}  $msg")
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        changed()
    }

    fun begin(msg: String) { busy = true; state = msg; log(msg) }
    fun setState(s: String) { state = s; log(s) }

    fun finish(ctx: Context, ok: Boolean, msg: String) {
        busy = false
        state = if (ok) "OK: $msg" else "FAILED: $msg"
        val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        ctx.dpPrefs(PREFS).edit().putString("last", "${if (ok) "Last success" else "Last failure"} ($whenText): $msg").apply()
        log(state)
    }

    fun lastResult(ctx: Context): String =
        ctx.dpPrefs(PREFS).getString("last", "No sync has run yet") ?: ""

    private fun changed() { main.post { listeners.forEach { it.onSyncStatusChanged() } } }
}
