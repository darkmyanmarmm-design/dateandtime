package com.mhtime.tvauto

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity(), SyncStatus.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvUtc: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLast: TextView
    private lateinit var tvKey: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnSync: Button

    private val clockFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    private val utcFmt = SimpleDateFormat("'UTC' yyyy-MM-dd HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val ticker = object : Runnable {
        override fun run() {
            val now = Date(System.currentTimeMillis())
            tvClock.text = clockFmt.format(now)
            tvDate.text = "${dateFmt.format(now)}  ·  ${TimeZone.getDefault().id}"
            tvUtc.text = utcFmt.format(now)
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvUtc = findViewById(R.id.tvUtc)
        tvStatus = findViewById(R.id.tvStatus)
        tvLast = findViewById(R.id.tvLast)
        tvKey = findViewById(R.id.tvKey)
        tvLog = findViewById(R.id.tvLog)
        btnSync = findViewById(R.id.btnSync)

        btnSync.setOnClickListener {
            try {
                // force = true: set the clock even if it already looks right (useful to test the ADB path)
                TimeSyncService.start(this, reason = "manual", force = true, attempts = 2)
            } catch (e: Exception) {
                SyncStatus.log("Could not start sync: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        btnSync.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        SyncStatus.addListener(this)
        render()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        SyncStatus.removeListener(this)
        super.onStop()
    }

    override fun onSyncStatusChanged() = render()

    private fun render() {
        tvStatus.text = SyncStatus.state
        tvLast.text = SyncStatus.lastResult(this)
        tvKey.text = "ADB key fingerprint: ${AdbKeyStore(this).peekFingerprint() ?: "not generated yet"}"
        tvLog.text = SyncStatus.lines.takeLast(14).joinToString("\n")
        btnSync.isEnabled = !SyncStatus.busy
        if (btnSync.isEnabled && !btnSync.hasFocus()) btnSync.requestFocus()
    }
}
