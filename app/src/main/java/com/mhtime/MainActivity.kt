package com.mhtime

import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val ntpServers = listOf(
        "sg.pool.ntp.org",
        "0.sg.pool.ntp.org",
        "th.pool.ntp.org",
        "my.pool.ntp.org",
        "hk.pool.ntp.org",
        "asia.pool.ntp.org",
        "time.cloudflare.com",
        "time.windows.com",
        "time.apple.com",
        "time.nist.gov",
        "ntp.nict.jp"
    )

    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var tvCurrentNtp: TextView
    private lateinit var btnRefreshCurrent: Button
    private lateinit var spinnerNtpServer: Spinner
    private lateinit var etCustomNtp: EditText
    private lateinit var btnUseCustom: Button
    private lateinit var tvSelectedTarget: TextView
    private lateinit var tvTestResult: TextView
    private lateinit var btnTestServer: Button
    private lateinit var btnSyncNow: Button
    private lateinit var tvSyncStatus: TextView

    private var selectedServer: String = ntpServers.first()
    private var lastServerReachable: Boolean = false
    private var isInitialSpinnerSelection: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCurrentNtp = findViewById(R.id.tvCurrentNtp)
        btnRefreshCurrent = findViewById(R.id.btnRefreshCurrent)
        spinnerNtpServer = findViewById(R.id.spinnerNtpServer)
        etCustomNtp = findViewById(R.id.etCustomNtp)
        btnUseCustom = findViewById(R.id.btnUseCustom)
        tvSelectedTarget = findViewById(R.id.tvSelectedTarget)
        tvTestResult = findViewById(R.id.tvTestResult)
        btnTestServer = findViewById(R.id.btnTestServer)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)

        refreshCurrentNtpDisplay()
        updateSelectedDisplay(selectedServer)

        btnRefreshCurrent.setOnClickListener {
            refreshCurrentNtpDisplay()
            tvSyncStatus.text = "Status: Refreshed active NTP server"
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ntpServers
        )
        spinnerNtpServer.adapter = adapter

        spinnerNtpServer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedServer = ntpServers[position]
                updateSelectedDisplay(selectedServer)
                if (!isInitialSpinnerSelection) {
                    runServerTest(selectedServer)
                } else {
                    isInitialSpinnerSelection = false
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnUseCustom.setOnClickListener {
            val cleanedHost = sanitizeNtpHost(etCustomNtp.text.toString())
            if (cleanedHost.isEmpty()) {
                tvSyncStatus.text = "Status: Please enter a valid NTP server hostname or IP"
                return@setOnClickListener
            }
            etCustomNtp.setText(cleanedHost)
            selectedServer = cleanedHost
            updateSelectedDisplay(selectedServer)
            runServerTest(selectedServer)
        }

        btnTestServer.setOnClickListener {
            runServerTest(selectedServer)
        }

        btnSyncNow.setOnClickListener {
            applyNtpServerAndSync(selectedServer)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentNtpDisplay()
    }

    private fun sanitizeNtpHost(input: String): String {
        return input.trim()
            .removePrefix("ntp://")
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")
            .trim()
    }

    private fun updateSelectedDisplay(server: String) {
        tvSelectedTarget.text = "Selected: $server"
    }

    private fun refreshCurrentNtpDisplay() {
        val displayValue = try {
            val raw = Settings.Global.getString(contentResolver, "ntp_server")
            if (raw.isNullOrBlank()) {
                "Default (OS / Not overridden)"
            } else {
                raw
            }
        } catch (_: Exception) {
            "Unable to read"
        }
        tvCurrentNtp.text = "Active NTP Server: $displayValue"
    }

    private fun runServerTest(server: String) {
        lastServerReachable = false
        tvTestResult.text = "ISP Test: Testing $server..."
        btnTestServer.isEnabled = false
        btnUseCustom.isEnabled = false

        ioExecutor.execute {
            val result = queryNtpLatency(server)
            runOnUiThread {
                btnTestServer.isEnabled = true
                btnUseCustom.isEnabled = true
                lastServerReachable = result.reachable
                tvTestResult.text = if (result.reachable && result.latencyMs != null) {
                    String.format(
                        Locale.US,
                        "ISP Test: OK (%.1f ms) - Works with ISP",
                        result.latencyMs
                    )
                } else {
                    "ISP Test: Failed / Blocked (${result.error ?: "timeout"})"
                }
            }
        }
    }

    private fun applyNtpServerAndSync(server: String) {
        try {
            Settings.Global.putString(contentResolver, "ntp_server", server)
            Settings.Global.putInt(contentResolver, Settings.Global.AUTO_TIME, 0)
            Settings.Global.putInt(contentResolver, Settings.Global.AUTO_TIME, 1)
            Settings.Global.putInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, 1)

            refreshCurrentNtpDisplay()
            val warnText = if (lastServerReachable) "" else " (Note: last ISP test failed)"
            tvSyncStatus.text = "Status: Changed to $server & refreshed Auto Time$warnText"
        } catch (_: SecurityException) {
            tvSyncStatus.text = "Status: Missing WRITE_SECURE_SETTINGS or Root"
        } catch (e: Exception) {
            tvSyncStatus.text = "Status: Error - ${e.message}"
        }
    }

    private fun queryNtpLatency(server: String, timeoutMs: Int = 3000): NtpCheckResult {
        return try {
            val startNs = System.nanoTime()
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(server)
                val request = ByteArray(48).apply {
                    this[0] = 0x1B.toByte()
                }
                val requestPacket = DatagramPacket(request, request.size, address, 123)
                socket.send(requestPacket)

                val responseBuffer = ByteArray(48)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                NtpCheckResult(reachable = true, latencyMs = elapsedMs, error = null)
            }
        } catch (e: Exception) {
            NtpCheckResult(reachable = false, latencyMs = null, error = e.message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdownNow()
    }

    private data class NtpCheckResult(
        val reachable: Boolean,
        val latencyMs: Double?,
        val error: String?
    )
}
