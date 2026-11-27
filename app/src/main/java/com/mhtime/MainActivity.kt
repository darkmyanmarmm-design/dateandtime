package com.mhtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private val defaultNtpServers: List<String> = listOf(
        "time.google.com",
        "time1.google.com",
        "time2.google.com",
        "time3.google.com",
        "time4.google.com",
        "time.android.com",
        "time.aws.com",
        "amazon.pool.ntp.org",
        "0.amazon.pool.ntp.org",
        "1.amazon.pool.ntp.org",
        "2.amazon.pool.ntp.org",
        "3.amazon.pool.ntp.org",
        "time.cloudflare.com",
        "time.windows.com",
        "time.apple.com",
        "time1.apple.com",
        "time2.apple.com",
        "time.nist.gov",
        "time-a-g.nist.gov",
        "pool.ntp.org",
        "0.pool.ntp.org",
        "1.pool.ntp.org",
        "asia.pool.ntp.org",
        "0.asia.pool.ntp.org",
        "1.asia.pool.ntp.org",
        "2.asia.pool.ntp.org",
        "3.asia.pool.ntp.org",
        "ntp.nict.jp",
        "ntp1.jst.mfeed.ad.jp",
        "clock.fmt.he.net",
        "clock.sjc.he.net"
    ).distinct()

    private val customOptionLabel = "Custom..."
    private val prefsName = "mhtime_remote_prefs"
    private val keyTvIp = "tv_ip"
    private val keyTvPort = "tv_port"

    private val scanPool = Executors.newFixedThreadPool(16)
    private val coordExecutor = Executors.newSingleThreadExecutor()

    private lateinit var etTvIp: EditText
    private lateinit var etTvPort: EditText
    private lateinit var btnFindTv: Button
    private lateinit var btnConnectTv: Button
    private lateinit var btnReadTvNtp: Button
    private lateinit var tvTvConnection: TextView
    private lateinit var btnOpenServerDialog: Button
    private lateinit var tvSelectedTarget: TextView
    private lateinit var progressScanning: ProgressBar
    private lateinit var tvTestResult: TextView
    private lateinit var btnRescan: Button
    private lateinit var btnSyncTv: Button
    private lateinit var tvSyncStatus: TextView

    private var sortedScanResults: List<NtpScanItem> = defaultNtpServers.map {
        NtpScanItem(server = it, reachable = false, latencyMs = null, error = null)
    }

    private var selectedServer: String = defaultNtpServers.first()
    private var lastCustomServer: String = ""
    private var isCustomSelected: Boolean = false
    private var isTvConnected: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etTvIp = findViewById(R.id.etTvIp)
        etTvPort = findViewById(R.id.etTvPort)
        btnFindTv = findViewById(R.id.btnFindTv)
        btnConnectTv = findViewById(R.id.btnConnectTv)
        btnReadTvNtp = findViewById(R.id.btnReadTvNtp)
        tvTvConnection = findViewById(R.id.tvTvConnection)
        btnOpenServerDialog = findViewById(R.id.btnOpenServerDialog)
        tvSelectedTarget = findViewById(R.id.tvSelectedTarget)
        progressScanning = findViewById(R.id.progressScanning)
        tvTestResult = findViewById(R.id.tvTestResult)
        btnRescan = findViewById(R.id.btnRescan)
        btnSyncTv = findViewById(R.id.btnSyncTv)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        etTvIp.setText(prefs.getString(keyTvIp, "") ?: "")
        etTvPort.setText((prefs.getInt(keyTvPort, 5555)).toString())

        updateSelectedDisplay(selectedServer)

        btnFindTv.setOnClickListener {
            findTvOnLocalNetwork()
        }

        btnConnectTv.setOnClickListener {
            connectToTv(queryCurrentNtpAfterConnect = true)
        }

        btnReadTvNtp.setOnClickListener {
            readCurrentTvNtp()
        }

        btnOpenServerDialog.setOnClickListener {
            showNtpServerDialog()
        }

        btnRescan.setOnClickListener {
            autoScanServers()
        }

        btnSyncTv.setOnClickListener {
            pushNtpToTv(selectedServer)
        }

        autoScanServers()
    }

    private fun findTvOnLocalNetwork() {
        val port = etTvPort.text.toString().trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 5555
        etTvPort.setText(port.toString())

        val localIp = detectLocalIpv4Address()
        if (localIp == null) {
            tvSyncStatus.text = "Connect phone to Wi-Fi or type TV IP manually"
            return
        }

        val parts = localIp.split(".")
        if (parts.size != 4) {
            tvSyncStatus.text = "Cannot read Wi-Fi subnet; type TV IP manually"
            return
        }
        val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val checkedCount = AtomicInteger(0)

        setBusyState(
            busy = true,
            testText = tvTestResult.text.toString(),
            statusText = "Finding TV on $subnetPrefix.1-254:$port (0/254)..."
        )

        coordExecutor.execute {
            val foundHosts = mutableListOf<String>()
            val futures = (1..254).map { hostLastOctet ->
                val candidateIp = "$subnetPrefix.$hostLastOctet"
                scanPool.submit<String?> {
                    val open = isTcpPortOpen(candidateIp, port, timeoutMs = 180)
                    val done = checkedCount.incrementAndGet()
                    if (done % 25 == 0 || done == 254) {
                        runOnUiThread {
                            tvSyncStatus.text = "Finding TV on $subnetPrefix.x:$port ($done/254)..."
                        }
                    }
                    if (open) candidateIp else null
                }
            }

            for (future in futures) {
                try {
                    val ip = future.get()
                    if (ip != null && ip != localIp) {
                        foundHosts.add(ip)
                    }
                } catch (_: Exception) {
                }
            }

            runOnUiThread {
                setBusyState(
                    busy = false,
                    testText = tvTestResult.text.toString(),
                    statusText = if (foundHosts.isEmpty()) {
                        "No ADB TV found on $subnetPrefix.x:$port; type IP manually"
                    } else {
                        "Found ${foundHosts.size} device(s) on port $port"
                    }
                )

                if (foundHosts.isNotEmpty()) {
                    showFoundTvDialog(foundHosts, port)
                }
            }
        }
    }

    private fun showFoundTvDialog(ips: List<String>, port: Int) {
        val labels = ips.map { "$it:$port" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select TV Device")
            .setItems(labels) { _, which ->
                val chosenIp = ips[which]
                etTvIp.setText(chosenIp)
                etTvPort.setText(port.toString())
                connectToTv(queryCurrentNtpAfterConnect = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun detectLocalIpv4Address(): String? {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val linkProps: LinkProperties? = cm?.getLinkProperties(activeNetwork)
            val linkIp = linkProps?.linkAddresses
                ?.mapNotNull { it.address as? Inet4Address }
                ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
            if (!linkIp.isNullOrBlank()) return linkIp
        } catch (_: Exception) {
        }

        try {
            @Suppress("DEPRECATION")
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wm?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getTvEndpoint(): Pair<String, Int>? {
        val ip = etTvIp.text.toString().trim()
        val port = etTvPort.text.toString().trim().toIntOrNull() ?: 5555
        if (ip.isEmpty() || port !in 1..65535) {
            tvSyncStatus.text = "Enter a valid TV IP and port"
            return null
        }
        getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .putString(keyTvIp, ip)
            .putInt(keyTvPort, port)
            .apply()
        return ip to port
    }

    private fun connectToTv(queryCurrentNtpAfterConnect: Boolean) {
        val endpoint = getTvEndpoint() ?: return
        val (ip, port) = endpoint

        setBusyState(
            busy = true,
            testText = tvTestResult.text.toString(),
            statusText = "Connecting to TV $ip:$port..."
        )

        coordExecutor.execute {
            val res = runAdbShellCommand(
                ip = ip,
                port = port,
                shellCmd = if (queryCurrentNtpAfterConnect) {
                    "settings get global ntp_server"
                } else {
                    "echo OK"
                }
            )

            runOnUiThread {
                if (res.success) {
                    isTvConnected = true
                    tvTvConnection.text = "TV: Connected ($ip:$port)"
                    val currentNtp = res.output.trim().ifEmpty { "Default (OS)" }
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = if (queryCurrentNtpAfterConnect) {
                            "TV NTP: $currentNtp"
                        } else {
                            "TV ready"
                        }
                    )
                } else {
                    isTvConnected = false
                    tvTvConnection.text = "TV: Failed ($ip:$port)"
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "ADB error: ${res.error ?: "check TV network ADB & prompt"}"
                    )
                }
            }
        }
    }

    private fun readCurrentTvNtp() {
        val endpoint = getTvEndpoint() ?: return
        val (ip, port) = endpoint

        setBusyState(
            busy = true,
            testText = tvTestResult.text.toString(),
            statusText = "Reading current NTP from TV..."
        )

        coordExecutor.execute {
            val res = runAdbShellCommand(ip, port, "settings get global ntp_server")
            runOnUiThread {
                if (res.success) {
                    isTvConnected = true
                    tvTvConnection.text = "TV: Connected ($ip:$port)"
                    val value = res.output.trim()
                    val display = if (value.isEmpty() || value.equals("null", ignoreCase = true)) {
                        "Default (OS)"
                    } else {
                        value
                    }
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "TV Active NTP: $display"
                    )
                } else {
                    isTvConnected = false
                    tvTvConnection.text = "TV: Failed"
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "Read failed: ${res.error ?: "connection error"}"
                    )
                }
            }
        }
    }

    private fun pushNtpToTv(server: String) {
        val endpoint = getTvEndpoint() ?: return
        val (ip, port) = endpoint
        val cleanServer = sanitizeNtpHost(server)
        if (cleanServer.isEmpty()) {
            tvSyncStatus.text = "Invalid NTP server"
            return
        }

        setBusyState(
            busy = true,
            testText = tvTestResult.text.toString(),
            statusText = "Pushing $cleanServer to TV..."
        )

        coordExecutor.execute {
            val cmd = "settings put global ntp_server $cleanServer && " +
                "settings put global auto_time 0 && " +
                "settings put global auto_time 1 && " +
                "settings get global ntp_server"
            val res = runAdbShellCommand(ip, port, cmd)

            runOnUiThread {
                if (res.success) {
                    isTvConnected = true
                    tvTvConnection.text = "TV: Connected ($ip:$port)"
                    val updated = res.output.trim().lines().lastOrNull()?.trim().orEmpty()
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "TV Synced -> ${updated.ifEmpty { cleanServer }}"
                    )
                } else {
                    isTvConnected = false
                    tvTvConnection.text = "TV: Failed"
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "Push failed: ${res.error ?: "check TV ADB authorization"}"
                    )
                }
            }
        }
    }

    private fun autoScanServers() {
        val total = defaultNtpServers.size
        val completed = AtomicInteger(0)

        setBusyState(
            busy = true,
            testText = "Scanning... (0/$total)",
            statusText = "Testing NTP latency from phone..."
        )

        coordExecutor.execute {
            val results = mutableListOf<NtpScanItem>()
            val futures = defaultNtpServers.map { server ->
                scanPool.submit<NtpScanItem> {
                    val check = queryNtpLatency(server, timeoutMs = 1500)
                    val done = completed.incrementAndGet()
                    runOnUiThread {
                        tvTestResult.text = "Scanning... ($done/$total)"
                    }
                    NtpScanItem(
                        server = server,
                        reachable = check.reachable,
                        latencyMs = check.latencyMs,
                        error = check.error
                    )
                }
            }

            for (future in futures) {
                try {
                    results.add(future.get())
                } catch (_: Exception) {
                }
            }

            val ordered = results.sortedWith(
                compareBy<NtpScanItem> { if (it.reachable && it.latencyMs != null) 0 else 1 }
                    .thenBy { it.latencyMs ?: Double.MAX_VALUE }
            )

            runOnUiThread {
                if (ordered.isEmpty()) {
                    setBusyState(busy = false, testText = "ISP Test: Scan interrupted", statusText = "Ready")
                    return@runOnUiThread
                }

                sortedScanResults = ordered
                val fastest = ordered.firstOrNull { it.reachable && it.latencyMs != null }
                val workingCount = ordered.count { it.reachable && it.latencyMs != null }

                if (fastest != null) {
                    if (!isCustomSelected) {
                        selectedServer = fastest.server
                        btnOpenServerDialog.text = String.format(
                            Locale.US,
                            "Server: %s (%.1f ms)",
                            fastest.server,
                            fastest.latencyMs
                        )
                        updateSelectedDisplay(selectedServer)
                    }
                    setBusyState(
                        busy = false,
                        testText = String.format(
                            Locale.US,
                            "Best Ping: %s (%.1f ms)",
                            fastest.server,
                            fastest.latencyMs
                        ),
                        statusText = "$workingCount/${ordered.size} servers ready • Tap Push & Sync TV"
                    )
                } else {
                    setBusyState(
                        busy = false,
                        testText = "ISP Test: No response (UDP 123 blocked)",
                        statusText = "0/${ordered.size} servers reachable"
                    )
                }
            }
        }
    }

    private fun showNtpServerDialog() {
        val formattedItems = sortedScanResults.map { item ->
            if (item.reachable && item.latencyMs != null) {
                String.format(Locale.US, "%s  (%.1f ms)", item.server, item.latencyMs)
            } else {
                "${item.server}  (FAIL)"
            }
        } + customOptionLabel

        val checkedIndex = if (isCustomSelected) {
            formattedItems.lastIndex
        } else {
            val idx = sortedScanResults.indexOfFirst { it.server == selectedServer }
            if (idx >= 0) idx else 0
        }

        AlertDialog.Builder(this)
            .setTitle("Select NTP Server (Lowest ms First)")
            .setSingleChoiceItems(formattedItems.toTypedArray(), checkedIndex) { dialog, which ->
                dialog.dismiss()
                if (which == formattedItems.lastIndex) {
                    showCustomNtpInputDialog()
                } else {
                    val chosenItem = sortedScanResults[which]
                    isCustomSelected = false
                    selectedServer = chosenItem.server
                    btnOpenServerDialog.text = if (chosenItem.reachable && chosenItem.latencyMs != null) {
                        String.format(Locale.US, "Server: %s (%.1f ms)", chosenItem.server, chosenItem.latencyMs)
                    } else {
                        "Server: ${chosenItem.server}"
                    }
                    updateSelectedDisplay(selectedServer)
                    runSingleServerTest(selectedServer)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomNtpInputDialog() {
        val input = EditText(this).apply {
            hint = "e.g. time1.google.com"
            setText(lastCustomServer)
            setSelection(text.length)
            setPadding(40, 28, 40, 28)
        }

        AlertDialog.Builder(this)
            .setTitle("Custom NTP Server")
            .setView(input)
            .setPositiveButton("Test") { _, _ ->
                val cleaned = sanitizeNtpHost(input.text.toString())
                if (cleaned.isEmpty()) {
                    tvSyncStatus.text = "Invalid server address"
                    return@setPositiveButton
                }
                lastCustomServer = cleaned
                isCustomSelected = true
                selectedServer = cleaned
                btnOpenServerDialog.text = "Server: Custom ($selectedServer)"
                updateSelectedDisplay("$selectedServer (Custom)")
                runSingleServerTest(selectedServer)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runSingleServerTest(server: String) {
        setBusyState(
            busy = true,
            testText = "Testing $server...",
            statusText = "Checking UDP 123 latency..."
        )

        coordExecutor.execute {
            val check = queryNtpLatency(server, timeoutMs = 2500)
            runOnUiThread {
                val resultText = if (check.reachable && check.latencyMs != null) {
                    String.format(Locale.US, "ISP Test: OK (%.1f ms)", check.latencyMs)
                } else {
                    "ISP Test: Failed / Blocked"
                }
                setBusyState(
                    busy = false,
                    testText = resultText,
                    statusText = if (check.reachable) "Ready to push to TV" else "Server did not respond"
                )
            }
        }
    }

    private fun runAdbShellCommand(ip: String, port: Int, shellCmd: String): AdbShellResult {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(ip, port), 4000)

                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                val banner = "host::product=mhtime_remote\u0000".toByteArray(StandardCharsets.UTF_8)
                writeAdbPacket(out, CMD_CNXN, 0x01000000, 4096, banner)

                val cnxnReply = readAdbPacket(inp) ?: return AdbShellResult(false, "", "No CNXN reply")
                if (cnxnReply.command == CMD_AUTH) {
                    return AdbShellResult(
                        false,
                        "",
                        "TV requested ADB AUTH (check TV screen or pair via ADB)"
                    )
                }
                if (cnxnReply.command != CMD_CNXN) {
                    return AdbShellResult(false, "", "Unexpected ADB reply")
                }

                val localId = 1
                val destination = "shell:$shellCmd\u0000".toByteArray(StandardCharsets.UTF_8)
                writeAdbPacket(out, CMD_OPEN, localId, 0, destination)

                val outputBuffer = ByteArrayOutputStream()
                var remoteId = 0

                while (true) {
                    val pkt = readAdbPacket(inp) ?: break
                    when (pkt.command) {
                        CMD_OKAY -> {
                            if (remoteId == 0) remoteId = pkt.arg0
                        }
                        CMD_WRTE -> {
                            if (remoteId == 0) remoteId = pkt.arg0
                            if (pkt.payload.isNotEmpty()) {
                                outputBuffer.write(pkt.payload)
                            }
                            writeAdbPacket(out, CMD_OKAY, localId, remoteId, ByteArray(0))
                        }
                        CMD_CLSE -> {
                            writeAdbPacket(out, CMD_CLSE, localId, pkt.arg0, ByteArray(0))
                            break
                        }
                    }
                }

                AdbShellResult(
                    success = true,
                    output = outputBuffer.toString(StandardCharsets.UTF_8.name()),
                    error = null
                )
            }
        } catch (e: Exception) {
            AdbShellResult(false, "", e.message ?: "Socket error")
        }
    }

    private fun writeAdbPacket(
        out: DataOutputStream,
        cmd: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray
    ) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(cmd)
            putInt(arg0)
            putInt(arg1)
            putInt(payload.size)
            putInt(adbChecksum(payload))
            putInt(cmd xor -0x1)
        }.array()
        out.write(header)
        if (payload.isNotEmpty()) {
            out.write(payload)
        }
        out.flush()
    }

    private fun readAdbPacket(inp: DataInputStream): AdbPacket? {
        val header = ByteArray(24)
        inp.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val len = buf.int
        buf.int
        buf.int

        if (len !in 0..1_048_576) return null
        val payload = if (len > 0) {
            ByteArray(len).also { inp.readFully(it) }
        } else {
            ByteArray(0)
        }
        return AdbPacket(cmd, arg0, arg1, payload)
    }

    private fun adbChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
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

    private fun updateSelectedDisplay(label: String) {
        tvSelectedTarget.text = "Selected: $label"
    }

    private fun setBusyState(busy: Boolean, testText: String, statusText: String) {
        progressScanning.visibility = if (busy) View.VISIBLE else View.GONE
        tvTestResult.text = testText
        tvSyncStatus.text = statusText
        btnFindTv.isEnabled = !busy
        btnConnectTv.isEnabled = !busy
        btnReadTvNtp.isEnabled = !busy
        btnOpenServerDialog.isEnabled = !busy
        btnRescan.isEnabled = !busy
        btnSyncTv.isEnabled = !busy
    }

    private fun queryNtpLatency(server: String, timeoutMs: Int = 2000): NtpCheckResult {
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
        coordExecutor.shutdownNow()
        scanPool.shutdownNow()
    }

    private data class NtpCheckResult(
        val reachable: Boolean,
        val latencyMs: Double?,
        val error: String?
    )

    private data class NtpScanItem(
        val server: String,
        val reachable: Boolean,
        val latencyMs: Double?,
        val error: String?
    )

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray
    )

    private data class AdbShellResult(
        val success: Boolean,
        val output: String,
        val error: String?
    )

    companion object {
        private const val CMD_CNXN = 0x4e584e43
        private const val CMD_AUTH = 0x48545541
        private const val CMD_OPEN = 0x4e45504f
        private const val CMD_OKAY = 0x59414b4f
        private const val CMD_CLSE = 0x45534c43
        private const val CMD_WRTE = 0x45545257
    }
}
