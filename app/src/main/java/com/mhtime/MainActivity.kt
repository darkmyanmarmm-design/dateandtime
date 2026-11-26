package com.mhtime

import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private val defaultNtpServers: List<String> = listOf(
        // Google Public NTP
        "time.google.com",
        "time1.google.com",
        "time2.google.com",
        "time3.google.com",
        "time4.google.com",
        "time.android.com",
        // Amazon Time Sync
        "time.aws.com",
        "amazon.pool.ntp.org",
        "0.amazon.pool.ntp.org",
        "1.amazon.pool.ntp.org",
        "2.amazon.pool.ntp.org",
        "3.amazon.pool.ntp.org",
        // Cloudflare
        "time.cloudflare.com",
        // Facebook
        "time.facebook.com",
        "time1.facebook.com",
        "time2.facebook.com",
        "time3.facebook.com",
        "time4.facebook.com",
        "time5.facebook.com",
        // Microsoft
        "time.windows.com",
        // Apple
        "time.apple.com",
        "time-macos.apple.com",
        "time-ios.apple.com",
        "time1.apple.com",
        "time2.apple.com",
        "time3.apple.com",
        "time4.apple.com",
        "time5.apple.com",
        "time6.apple.com",
        "time7.apple.com",
        "time.euro.apple.com",
        // DEC / Compaq / HP
        "clepsydra.dec.com",
        "clepsydra.labs.hp.com",
        "clepsydra.hpl.hp.com",
        "usno.labs.hp.com",
        // NIST ITS
        "time-a-g.nist.gov",
        "time-b-g.nist.gov",
        "time-c-g.nist.gov",
        "time-d-g.nist.gov",
        "time-a-wwv.nist.gov",
        "time-b-wwv.nist.gov",
        "time-c-wwv.nist.gov",
        "time-d-wwv.nist.gov",
        "time-a-b.nist.gov",
        "time-b-b.nist.gov",
        "time-c-b.nist.gov",
        "time-d-b.nist.gov",
        "time.nist.gov",
        "time-e-b.nist.gov",
        "time-e-g.nist.gov",
        "time-e-wwv.nist.gov",
        "time-nw.nist.gov",
        "time-a.nist.gov",
        "time-b.nist.gov",
        "utcnist.colorado.edu",
        "utcnist2.colorado.edu",
        // VNIIFTRI
        "ntp1.vniiftri.ru",
        "ntp2.vniiftri.ru",
        "ntp3.vniiftri.ru",
        "ntp4.vniiftri.ru",
        "ntp.sstf.nsk.ru",
        "ntp1.niiftri.irkutsk.ru",
        "ntp2.niiftri.irkutsk.ru",
        "vniiftri.khv.ru",
        "vniiftri2.khv.ru",
        "ntp21.vniiftri.ru",
        // Mobatime
        "ntp.mobatime.ru",
        // NTP SERVERS
        "ntp0.ntp-servers.net",
        "ntp1.ntp-servers.net",
        "ntp2.ntp-servers.net",
        "ntp3.ntp-servers.net",
        "ntp4.ntp-servers.net",
        "ntp5.ntp-servers.net",
        "ntp6.ntp-servers.net",
        "ntp7.ntp-servers.net",
        // Stratum 1 / 2 RU & Stratum1
        "ntp1.stratum1.ru",
        "ntp2.stratum1.ru",
        "ntp3.stratum1.ru",
        "ntp4.stratum1.ru",
        "ntp5.stratum1.ru",
        "ntp1.stratum2.ru",
        "ntp2.stratum2.ru",
        "ntp3.stratum2.ru",
        "ntp4.stratum2.ru",
        "ntp5.stratum2.ru",
        "stratum1.net",
        // time.in.ua & Delfa & ACO.net
        "ntp.time.in.ua",
        "ntp2.time.in.ua",
        "ntp3.time.in.ua",
        "ntp.ru",
        "ts1.aco.net",
        "ts2.aco.net",
        // Universities & Research
        "ntp1.net.berkeley.edu",
        "ntp2.net.berkeley.edu",
        "ntp.gsu.edu",
        "tick.usask.ca",
        "tock.usask.ca",
        "ntp.nsu.ru",
        "ntp.psn.ru",
        "ntp.rsu.edu.ru",
        // NICT & NTT & HE.net
        "ntp.nict.jp",
        "x.ns.gin.ntt.net",
        "y.ns.gin.ntt.net",
        "clock.sjc.he.net",
        "clock.fmt.he.net",
        "clock.nyc.he.net",
        // TRC Fiord & AMS-IX & Netnod
        "ntp.fiord.ru",
        "time1.ams-ix.net",
        "time2.ams-ix.net",
        "gbg1.ntp.se",
        "gbg2.ntp.se",
        "mmo1.ntp.se",
        "mmo2.ntp.se",
        "sth1.ntp.se",
        "sth2.ntp.se",
        "svl1.ntp.se",
        "svl2.ntp.se",
        "ntp.se",
        // QiX & YYCIX & MSK-IX & Hetzner
        "ntp.qix.ca",
        "ntp1.qix.ca",
        "ntp2.qix.ca",
        "ntp.yycix.ca",
        "ntp.ix.ru",
        "ntp1.hetzner.de",
        "ntp2.hetzner.de",
        "ntp3.hetzner.de",
        // Trabia & RIPE & ISC & TimeNL & Zug
        "time-a.as43289.net",
        "time-b.as43289.net",
        "time-c.as43289.net",
        "ntp.ripe.net",
        "clock.isc.org",
        "ntp.time.nl",
        "ntp1.time.nl",
        "ntp2.time.nl",
        "ntp0.as34288.net",
        "ntp1.as34288.net",
        // MFEED & NTSC & Nat Morris
        "ntp1.jst.mfeed.ad.jp",
        "ntp2.jst.mfeed.ad.jp",
        "ntp3.jst.mfeed.ad.jp",
        "ntp.ntsc.ac.cn",
        "ntp.nat.ms",
        // NTP Pool & Zones & OS Pools
        "pool.ntp.org",
        "0.pool.ntp.org",
        "1.pool.ntp.org",
        "2.pool.ntp.org",
        "3.pool.ntp.org",
        "europe.pool.ntp.org",
        "0.europe.pool.ntp.org",
        "1.europe.pool.ntp.org",
        "2.europe.pool.ntp.org",
        "3.europe.pool.ntp.org",
        "asia.pool.ntp.org",
        "0.asia.pool.ntp.org",
        "1.asia.pool.ntp.org",
        "2.asia.pool.ntp.org",
        "3.asia.pool.ntp.org",
        "ru.pool.ntp.org",
        "0.ru.pool.ntp.org",
        "1.ru.pool.ntp.org",
        "2.ru.pool.ntp.org",
        "3.ru.pool.ntp.org",
        "north-america.pool.ntp.org",
        "0.north-america.pool.ntp.org",
        "1.north-america.pool.ntp.org",
        "2.north-america.pool.ntp.org",
        "3.north-america.pool.ntp.org",
        "0.gentoo.pool.ntp.org",
        "1.gentoo.pool.ntp.org",
        "2.gentoo.pool.ntp.org",
        "3.gentoo.pool.ntp.org",
        "0.arch.pool.ntp.org",
        "1.arch.pool.ntp.org",
        "2.arch.pool.ntp.org",
        "3.arch.pool.ntp.org",
        "0.fedora.pool.ntp.org",
        "1.fedora.pool.ntp.org",
        "2.fedora.pool.ntp.org",
        "3.fedora.pool.ntp.org",
        "0.opensuse.pool.ntp.org",
        "1.opensuse.pool.ntp.org",
        "2.opensuse.pool.ntp.org",
        "3.opensuse.pool.ntp.org",
        "0.centos.pool.ntp.org",
        "1.centos.pool.ntp.org",
        "2.centos.pool.ntp.org",
        "3.centos.pool.ntp.org",
        "0.debian.pool.ntp.org",
        "1.debian.pool.ntp.org",
        "2.debian.pool.ntp.org",
        "3.debian.pool.ntp.org",
        "0.askozia.pool.ntp.org",
        "1.askozia.pool.ntp.org",
        "2.askozia.pool.ntp.org",
        "3.askozia.pool.ntp.org",
        "0.freebsd.pool.ntp.org",
        "1.freebsd.pool.ntp.org",
        "2.freebsd.pool.ntp.org",
        "3.freebsd.pool.ntp.org",
        "0.netbsd.pool.ntp.org",
        "1.netbsd.pool.ntp.org",
        "2.netbsd.pool.ntp.org",
        "3.netbsd.pool.ntp.org",
        "0.openbsd.pool.ntp.org",
        "1.openbsd.pool.ntp.org",
        "2.openbsd.pool.ntp.org",
        "3.openbsd.pool.ntp.org",
        "0.dragonfly.pool.ntp.org",
        "1.dragonfly.pool.ntp.org",
        "2.dragonfly.pool.ntp.org",
        "3.dragonfly.pool.ntp.org",
        "0.pfsense.pool.ntp.org",
        "1.pfsense.pool.ntp.org",
        "2.pfsense.pool.ntp.org",
        "3.pfsense.pool.ntp.org",
        "0.opnsense.pool.ntp.org",
        "1.opnsense.pool.ntp.org",
        "2.opnsense.pool.ntp.org",
        "3.opnsense.pool.ntp.org",
        "0.smartos.pool.ntp.org",
        "1.smartos.pool.ntp.org",
        "2.smartos.pool.ntp.org",
        "3.smartos.pool.ntp.org",
        "0.android.pool.ntp.org",
        "1.android.pool.ntp.org",
        "2.android.pool.ntp.org",
        "3.android.pool.ntp.org",
        "0.ubnt.pool.ntp.org",
        "1.ubnt.pool.ntp.org",
        "2.ubnt.pool.ntp.org",
        "3.ubnt.pool.ntp.org",
        // Other TLDs & Institutions
        "tick.usno.navy.mil",
        "tock.usno.navy.mil",
        "ntp2.usno.navy.mil",
        "timekeeper.isi.edu",
        "rackety.udel.edu",
        "mizbeaver.udel.edu",
        "otc1.psu.edu",
        "gnomon.cc.columbia.edu",
        "navobs1.gatech.edu",
        "navobs1.wustl.edu",
        "now.okstate.edu",
        "ntp.colby.edu",
        "ntp-s1.cise.ufl.edu",
        "bonehed.lcs.mit.edu",
        "level1e.cs.unc.edu",
        "tick.ucla.edu",
        "tick.uh.edu",
        "ntpstm.netbone-digital.com",
        "nist1.symmetricom.com",
        "ntp.quintex.com",
        "ntp1.conectiv.com",
        "tock.usshc.com",
        "t2.timegps.net",
        "gps.layer42.net",
        "ntp-ca.stygium.net",
        "sesku.planeacion.net",
        "ntp0.nl.uu.net",
        "ntp1.nl.uu.net",
        "navobs1.oar.net",
        "ntp-galway.hea.net",
        "ntp1.ona.org",
        "ntp.your.org",
        "ntp.mrow.org",
        "time.fu-berlin.de",
        "ntps1-0.cs.tu-berlin.de",
        "ntps1-1.cs.tu-berlin.de",
        "ntps1-0.uni-erlangen.de",
        "ntps1-1.uni-erlangen.de",
        "ntp1.fau.de",
        "ntp2.fau.de",
        "ntp.dianacht.de",
        "zeit.fu-berlin.de",
        "ptbtime1.ptb.de",
        "ptbtime2.ptb.de",
        "rustime01.rus.uni-stuttgart.de",
        "rustime02.rus.uni-stuttgart.de",
        "chime1.surfnet.nl",
        "ntp.vsl.nl",
        "asynchronos.iiss.at",
        "ntp.nic.cz",
        "time.ufe.cz",
        "ntp.fizyka.umk.pl",
        "tempus1.gum.gov.pl",
        "tempus2.gum.gov.pl",
        "ntp1.usv.ro",
        "ntp3.usv.ro",
        "timehost.lysator.liu.se",
        "time1.stupi.se",
        "time.nrc.ca",
        "clock.uregina.ca",
        "cronos.cenam.mx",
        "ntp.lcf.mx",
        "hora.roa.es",
        "minuto.roa.es",
        "ntp1.inrim.it",
        "ntp2.inrim.it",
        "ntp1.oma.be",
        "ntp2.oma.be",
        "ntp.atomki.mta.hu",
        "ntp.i2t.ehu.eus",
        "ntp.neel.ch",
        "ntp.neu.edu.cn",
        "ntps1.pads.ufrj.br",
        "ntp.shoa.cl",
        "time.esa.int",
        "time1.esa.int"
    ).distinct()

    private val customOptionLabel = "Custom..."
    private val scanPool = Executors.newFixedThreadPool(16)
    private val coordExecutor = Executors.newSingleThreadExecutor()

    private lateinit var tvCurrentNtp: TextView
    private lateinit var btnOpenServerDialog: Button
    private lateinit var tvSelectedTarget: TextView
    private lateinit var progressScanning: ProgressBar
    private lateinit var tvTestResult: TextView
    private lateinit var btnTestServer: Button
    private lateinit var btnSyncNow: Button
    private lateinit var tvSyncStatus: TextView

    private var sortedScanResults: List<NtpScanItem> = defaultNtpServers.map {
        NtpScanItem(server = it, reachable = false, latencyMs = null, error = null)
    }

    private var selectedServer: String = defaultNtpServers.first()
    private var lastCustomServer: String = ""
    private var isCustomSelected: Boolean = false
    private var lastServerReachable: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCurrentNtp = findViewById(R.id.tvCurrentNtp)
        btnOpenServerDialog = findViewById(R.id.btnOpenServerDialog)
        tvSelectedTarget = findViewById(R.id.tvSelectedTarget)
        progressScanning = findViewById(R.id.progressScanning)
        tvTestResult = findViewById(R.id.tvTestResult)
        btnTestServer = findViewById(R.id.btnTestServer)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)

        refreshCurrentNtpDisplay()
        updateSelectedDisplay(selectedServer)

        btnOpenServerDialog.setOnClickListener {
            showNtpServerDialog()
        }

        btnTestServer.setOnClickListener {
            runSingleServerTest(selectedServer)
        }

        btnSyncNow.setOnClickListener {
            applyNtpServerAndSync(selectedServer)
        }

        autoScanDefaultServers()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentNtpDisplay()
    }

    private fun autoScanDefaultServers() {
        val total = defaultNtpServers.size
        val completed = AtomicInteger(0)

        setBusyState(
            busy = true,
            testText = "Scanning servers... (0/$total)",
            statusText = "Checking NTP latency across servers..."
        )

        coordExecutor.execute {
            val results = mutableListOf<NtpScanItem>()
            val futures = defaultNtpServers.map { server ->
                scanPool.submit<NtpScanItem> {
                    val check = queryNtpLatency(server, timeoutMs = 1500)
                    val done = completed.incrementAndGet()
                    runOnUiThread {
                        tvTestResult.text = "Scanning servers... ($done/$total)"
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
                        lastServerReachable = true
                    }
                    setBusyState(
                        busy = false,
                        testText = String.format(
                            Locale.US,
                            "Best Ping: %s (%.1f ms)",
                            fastest.server,
                            fastest.latencyMs
                        ),
                        statusText = "$workingCount/${ordered.size} servers ready • Lowest ping at top"
                    )
                } else {
                    lastServerReachable = false
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
        lastServerReachable = false
        setBusyState(
            busy = true,
            testText = "Testing $server...",
            statusText = "Checking UDP 123 response..."
        )

        coordExecutor.execute {
            val result = queryNtpLatency(server, timeoutMs = 2500)
            runOnUiThread {
                lastServerReachable = result.reachable
                val resultText = if (result.reachable && result.latencyMs != null) {
                    String.format(Locale.US, "ISP Test: OK (%.1f ms)", result.latencyMs)
                } else {
                    "ISP Test: Failed / Blocked"
                }
                val statusMsg = if (result.reachable) {
                    "Server works with current ISP"
                } else {
                    "Timeout or blocked by ISP"
                }
                setBusyState(busy = false, testText = resultText, statusText = statusMsg)

                val idx = sortedScanResults.indexOfFirst { it.server == server }
                if (idx >= 0) {
                    val updatedList = sortedScanResults.toMutableList()
                    updatedList[idx] = NtpScanItem(
                        server = server,
                        reachable = result.reachable,
                        latencyMs = result.latencyMs,
                        error = result.error
                    )
                    sortedScanResults = updatedList.sortedWith(
                        compareBy<NtpScanItem> { if (it.reachable && it.latencyMs != null) 0 else 1 }
                            .thenBy { it.latencyMs ?: Double.MAX_VALUE }
                    )
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
            val note = if (lastServerReachable) "" else " (last test failed)"
            tvSyncStatus.text = "Synced: $server$note"
        } catch (_: SecurityException) {
            tvSyncStatus.text = "Missing WRITE_SECURE_SETTINGS or Root"
        } catch (e: Exception) {
            tvSyncStatus.text = "Error: ${e.message}"
        }
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

    private fun refreshCurrentNtpDisplay() {
        val displayValue = try {
            val raw = Settings.Global.getString(contentResolver, "ntp_server")
            if (raw.isNullOrBlank()) "Default (OS)" else raw
        } catch (_: Exception) {
            "Restricted"
        }
        tvCurrentNtp.text = "Active NTP: $displayValue"
    }

    private fun setBusyState(busy: Boolean, testText: String, statusText: String) {
        progressScanning.visibility = if (busy) View.VISIBLE else View.GONE
        tvTestResult.text = testText
        tvSyncStatus.text = statusText
        btnOpenServerDialog.isEnabled = !busy
        btnTestServer.isEnabled = !busy
        btnSyncNow.isEnabled = !busy
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
}
