package com.mhtime

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var clockText: TextView
    private lateinit var statusText: TextView
    private val uiHandler = Handler(Looper.getMainLooper())

    private val clockTicker = object : Runnable {
        override fun run() {
            clockText.text = BootReceiver.formatCurrentDateTime("Asia/Yangon")
            uiHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(48, 48, 48, 48)
        }

        val titleText = TextView(this).apply {
            text = "MH TIME (Asia/Yangon)"
            textSize = 20f
            setTextColor(Color.parseColor("#90CAF9"))
            gravity = Gravity.CENTER
        }

        clockText = TextView(this).apply {
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
            text = BootReceiver.formatCurrentDateTime("Asia/Yangon")
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#CFD8DC"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
            text = "Syncing..."
        }

        val syncButton = Button(this).apply {
            text = "Sync Now"
            setOnClickListener { triggerSync() }
        }

        val adbHint = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#78909C"))
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 0)
            text = "Non-root ADB:\nadb shell pm grant com.mhtime android.permission.WRITE_SECURE_SETTINGS"
        }

        root.addView(titleText)
        root.addView(clockText)
        root.addView(statusText)
        root.addView(syncButton)
        root.addView(adbHint)
        setContentView(root)

        triggerSync()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(clockTicker)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(clockTicker)
    }

    private fun triggerSync() {
        statusText.text = "Syncing time..."
        BootReceiver.syncFastWithRetry(this, attemptsLeft = 3) { result ->
            clockText.text = BootReceiver.formatCurrentDateTime("Asia/Yangon")
            statusText.text = result
        }
    }
}
