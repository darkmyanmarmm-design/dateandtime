package com.mhtime.tvauto

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tvCurrentClock: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnSyncNow: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCurrentClock = findViewById(R.id.tvCurrentClock)
        tvStatus = findViewById(R.id.tvStatus)
        btnSyncNow = findViewById(R.id.btnSyncNow)

        refreshClockDisplay()

        btnSyncNow.setOnClickListener {
            runManualSync()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshClockDisplay()
    }

    private fun refreshClockDisplay() {
        tvCurrentClock.text = "Device Time: ${TimeSyncHelper.formatCurrentDeviceClock()}"
    }

    private fun runManualSync() {
        btnSyncNow.isEnabled = false
        tvStatus.text = "Status: Fetching HTTPS time..."

        executor.execute {
            val result = TimeSyncHelper.syncSystemTimeFromHttps(this)
            runOnUiThread {
                refreshClockDisplay()
                tvStatus.text = "Status: ${result.message}"
                btnSyncNow.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
