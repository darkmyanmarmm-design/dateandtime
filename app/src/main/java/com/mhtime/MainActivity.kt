package com.mhtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Base64
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
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

class MainActivity : AppCompatActivity() {

    private val defaultNtpServers: List<String> = listOf(
        // Google Public NTP [AS15169]
        "time.google.com",
        "time1.google.com",
        "time2.google.com",
        "time3.google.com",
        "time4.google.com",
        "time.android.com",
        // public Amazon Time Sync Service [AS16509, AS14618, AS399991]
        "time.aws.com",
        "amazon.pool.ntp.org",
        "0.amazon.pool.ntp.org",
        "1.amazon.pool.ntp.org",
        "2.amazon.pool.ntp.org",
        "3.amazon.pool.ntp.org",
        // Cloudflare NTP [AS13335]
        "time.cloudflare.com",
        // Facebook NTP [AS32934]
        "time.facebook.com",
        "time1.facebook.com",
        "time2.facebook.com",
        "time3.facebook.com",
        "time4.facebook.com",
        "time5.facebook.com",
        // Microsoft NTP server [AS8075]
        "time.windows.com",
        // Apple NTP server [AS714, AS6185]
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
        // DEC/Compaq/HP
        "clepsydra.dec.com",
        "clepsydra.labs.hp.com",
        "clepsydra.hpl.hp.com",
        "usno.labs.hp.com",
        // NIST Internet Time Service (ITS) [AS49, AS104]
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
        // time.in.ua
        "ntp.time.in.ua",
        "ntp2.time.in.ua",
        "ntp3.time.in.ua",
        // Company Delfa Co. Ltd. [AS8915]
        "ntp.ru",
        // ACO.net [AS1853]
        "ts1.aco.net",
        "ts2.aco.net",
        // Berkeley [AS25]
        "ntp1.net.berkeley.edu",
        "ntp2.net.berkeley.edu",
        // Georgia State University [AS10631]
        "ntp.gsu.edu",
        // University of Saskatchewan [AS22950]
        "tick.usask.ca",
        "tock.usask.ca",
        // NSU [AS3335]
        "ntp.nsu.ru",
        // ITAEC [AS41783]
        "ntp.psn.ru",
        // RSU [AS47124]
        "ntp.rsu.edu.ru",
        // NICT [AS9355]
        "ntp.nict.jp",
        // NTT [AS2914]
        "x.ns.gin.ntt.net",
        "y.ns.gin.ntt.net",
        // HE.net [AS6939]
        "clock.sjc.he.net",
        "clock.fmt.he.net",
        "clock.nyc.he.net",
        // TRC Fiord [AS28917]
        "ntp.fiord.ru",
        // AMS-IX [AS212928]
        "time1.ams-ix.net",
        "time2.ams-ix.net",
        // Netnod [AS57021]
        "gbg1.ntp.se",
        "gbg2.ntp.se",
        "mmo1.ntp.se",
        "mmo2.ntp.se",
        "sth1.ntp.se",
        "sth2.ntp.se",
        "svl1.ntp.se",
        "svl2.ntp.se",
        "ntp.se",
        // QiX NTP [AS14086]
        "ntp.qix.ca",
        "ntp1.qix.ca",
        "ntp2.qix.ca",
        // YYCIX NTP [AS396515]
        "ntp.yycix.ca",
        // MSK-IX NTP [AS43832]
        "ntp.ix.ru",
        // Hetzner Online [AS24940]
        "ntp1.hetzner.de",
        "ntp2.hetzner.de",
        "ntp3.hetzner.de",
        // Trabia-Network [AS43289]
        "time-a.as43289.net",
        "time-b.as43289.net",
        "time-c.as43289.net",
        // RIPE [AS3333]
        "ntp.ripe.net",
        // Internet Systems Consortium [AS1280]
        "clock.isc.org",
        // TimeNL/SIDN Labs [AS1140]
        "ntp.time.nl",
        "ntp1.time.nl",
        "ntp2.time.nl",
        // Kantonsschule Zug [AS34288]
        "ntp0.as34288.net",
        "ntp1.as34288.net",
        // INTERNET MULTIFEED CO. [AS7521]
        "ntp1.jst.mfeed.ad.jp",
        "ntp2.jst.mfeed.ad.jp",
        "ntp3.jst.mfeed.ad.jp",
        // Chinese Academy of Sciences NTSC [AS4808, AS9808, AS23724]
        "ntp.ntsc.ac.cn",
        // Nat Morris [AS30746]
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
    private val prefsName = "mhtime_remote_prefs"
    private val keyTvIp = "tv_ip"
    private val keyTvPort = "tv_port"
    private val keyPrivDer = "adb_rsa_priv_der"
    private val keyPubWire = "adb_rsa_pub_wire"

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
    private var connectedTvLabel: String? = null

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

    private fun queryTvDeviceName(ip: String, port: Int): String {
        val cmd = "echo \"MODEL=\$(getprop ro.product.model)|DEVICE=\$(getprop ro.product.device)\""
        val res = runAdbShellCommand(ip, port, cmd)
        if (!res.success) return "$ip:$port"

        var model = ""
        var device = ""
        for (line in res.output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("MODEL=")) {
                model = trimmed.removePrefix("MODEL=").trim()
            } else if (trimmed.startsWith("DEVICE=")) {
                device = trimmed.removePrefix("DEVICE=").trim()
            }
        }

        val name = when {
            model.isNotEmpty() && device.isNotEmpty() && !model.equals(device, ignoreCase = true) ->
                "$model ($device)"
            model.isNotEmpty() -> model
            device.isNotEmpty() -> device
            else -> "Android TV"
        }
        return "$name • $ip:$port"
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

            val labeledDevices = foundHosts.map { ip ->
                val name = queryTvDeviceName(ip, port)
                ip to name
            }

            runOnUiThread {
                setBusyState(
                    busy = false,
                    testText = tvTestResult.text.toString(),
                    statusText = if (labeledDevices.isEmpty()) {
                        "No ADB TV found on $subnetPrefix.x:$port; type IP manually"
                    } else {
                        "Found ${labeledDevices.size} device(s) on port $port"
                    }
                )

                if (labeledDevices.isNotEmpty()) {
                    showFoundTvDialog(labeledDevices, port)
                }
            }
        }
    }

    private fun showFoundTvDialog(devices: List<Pair<String, String>>, port: Int) {
        val labels = devices.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select TV Device")
            .setItems(labels) { _, which ->
                val chosenIp = devices[which].first
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
            val infoCmd = if (queryCurrentNtpAfterConnect) {
                "echo \"MODEL=\$(getprop ro.product.model)|DEVICE=\$(getprop ro.product.device)|NTP=\$(settings get global ntp_server)\""
            } else {
                "echo \"MODEL=\$(getprop ro.product.model)|DEVICE=\$(getprop ro.product.device)\""
            }
            val res = runAdbShellCommand(ip, port, infoCmd)

            runOnUiThread {
                if (res.success) {
                    var model = ""
                    var device = ""
                    var ntpVal = ""
                    for (line in res.output.lines()) {
                        for (part in line.trim().split("|")) {
                            when {
                                part.startsWith("MODEL=") -> model = part.removePrefix("MODEL=").trim()
                                part.startsWith("DEVICE=") -> device = part.removePrefix("DEVICE=").trim()
                                part.startsWith("NTP=") -> ntpVal = part.removePrefix("NTP=").trim()
                            }
                        }
                    }

                    val deviceName = when {
                        model.isNotEmpty() && device.isNotEmpty() && !model.equals(device, ignoreCase = true) ->
                            "$model ($device)"
                        model.isNotEmpty() -> model
                        device.isNotEmpty() -> device
                        else -> "Android TV"
                    }
                    val label = "$deviceName • $ip:$port"
                    connectedTvLabel = label
                    tvTvConnection.text = "TV: $label"

                    val currentNtp = if (ntpVal.isEmpty() || ntpVal.equals("null", ignoreCase = true)) {
                        "Default (OS)"
                    } else {
                        ntpVal
                    }

                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = if (queryCurrentNtpAfterConnect) {
                            "$deviceName NTP: $currentNtp"
                        } else {
                            "$deviceName connected"
                        }
                    )
                } else {
                    connectedTvLabel = null
                    tvTvConnection.text = "TV: Failed ($ip:$port)"
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "ADB error: ${res.error ?: "check TV screen prompt"}"
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
            val res = runAdbShellCommand(
                ip,
                port,
                "echo \"MODEL=\$(getprop ro.product.model)|NTP=\$(settings get global ntp_server)\""
            )
            runOnUiThread {
                if (res.success) {
                    var model = ""
                    var ntpVal = ""
                    for (line in res.output.lines()) {
                        for (part in line.trim().split("|")) {
                            when {
                                part.startsWith("MODEL=") -> model = part.removePrefix("MODEL=").trim()
                                part.startsWith("NTP=") -> ntpVal = part.removePrefix("NTP=").trim()
                            }
                        }
                    }
                    val devLabel = if (model.isNotEmpty()) "$model • $ip:$port" else (connectedTvLabel ?: "$ip:$port")
                    connectedTvLabel = devLabel
                    tvTvConnection.text = "TV: $devLabel"

                    val display = if (ntpVal.isEmpty() || ntpVal.equals("null", ignoreCase = true)) {
                        "Default (OS)"
                    } else {
                        ntpVal
                    }
                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "TV Active NTP: $display"
                    )
                } else {
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
                "echo \"MODEL=\$(getprop ro.product.model)|NTP=\$(settings get global ntp_server)\""
            val res = runAdbShellCommand(ip, port, cmd)

            runOnUiThread {
                if (res.success) {
                    var model = ""
                    var updated = ""
                    for (line in res.output.lines()) {
                        for (part in line.trim().split("|")) {
                            when {
                                part.startsWith("MODEL=") -> model = part.removePrefix("MODEL=").trim()
                                part.startsWith("NTP=") -> updated = part.removePrefix("NTP=").trim()
                            }
                        }
                    }
                    val devLabel = if (model.isNotEmpty()) "$model • $ip:$port" else (connectedTvLabel ?: "$ip:$port")
                    connectedTvLabel = devLabel
                    tvTvConnection.text = "TV: $devLabel"

                    setBusyState(
                        busy = false,
                        testText = tvTestResult.text.toString(),
                        statusText = "TV Synced -> ${updated.ifEmpty { cleanServer }}"
                    )
                } else {
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

    private fun getOrCreateAdbKeyPair(): Pair<PrivateKey, ByteArray> {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedPriv = prefs.getString(keyPrivDer, null)
        val savedPub = prefs.getString(keyPubWire, null)

        if (savedPriv != null && savedPub != null) {
            try {
                val privBytes = Base64.decode(savedPriv, Base64.NO_WRAP)
                val pubBytes = Base64.decode(savedPub, Base64.NO_WRAP)
                val keyFactory = KeyFactory.getInstance("RSA")
                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                return privKey to pubBytes
            } catch (_: Exception) {
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp: KeyPair = kpg.generateKeyPair()
        val privBytes = kp.private.encoded
        val pubWire = encodeAdbRsaPublicKey(kp.public as RSAPublicKey, "mhtime@phone")

        prefs.edit()
            .putString(keyPrivDer, Base64.encodeToString(privBytes, Base64.NO_WRAP))
            .putString(keyPubWire, Base64.encodeToString(pubWire, Base64.NO_WRAP))
            .apply()

        return kp.private to pubWire
    }

    private fun encodeAdbRsaPublicKey(pubKey: RSAPublicKey, userName: String): ByteArray {
        val nWords = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n = pubKey.modulus
        val r = BigInteger.ONE.shiftLeft(nWords * 32)
        val rr = r.multiply(r).mod(n)
        val n0inv = r32.subtract(n.mod(r32)).modInverse(r32)

        val buf = ByteBuffer.allocate(4 + 4 + nWords * 4 + nWords * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(nWords)
        buf.putInt(n0inv.toInt())

        var tempN = n
        for (i in 0 until nWords) {
            val divRem = tempN.divideAndRemainder(r32)
            buf.putInt(divRem[1].toInt())
            tempN = divRem[0]
        }

        var tempRr = rr
        for (i in 0 until nWords) {
            val divRem = tempRr.divideAndRemainder(r32)
            buf.putInt(divRem[1].toInt())
            tempRr = divRem[0]
        }

        buf.putInt(pubKey.publicExponent.toInt())

        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "$b64 $userName\u0000".toByteArray(StandardCharsets.UTF_8)
    }

    private fun signAdbAuthToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        val sha1Digest = MessageDigest.getInstance("SHA-1").digest(token)
        val digestInfoPrefix = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
        val toEncrypt = ByteArray(digestInfoPrefix.size + sha1Digest.size)
        System.arraycopy(digestInfoPrefix, 0, toEncrypt, 0, digestInfoPrefix.size)
        System.arraycopy(sha1Digest, 0, toEncrypt, digestInfoPrefix.size, sha1Digest.size)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(toEncrypt)
    }

    private fun runAdbShellCommand(ip: String, port: Int, shellCmd: String): AdbShellResult {
        return try {
            Socket().use { socket ->
                socket.soTimeout = 6000
                socket.connect(InetSocketAddress(ip, port), 4000)

                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                val banner = "host::product=mhtime_remote\u0000".toByteArray(StandardCharsets.UTF_8)
                writeAdbPacket(out, CMD_CNXN, 0x01000000, 4096, banner)

                var reply = readAdbPacket(inp) ?: return AdbShellResult(false, "", "No CNXN reply")
                var authSentSignature = false

                while (reply.command == CMD_AUTH) {
                    val (privKey, pubWire) = getOrCreateAdbKeyPair()
                    if (reply.arg0 == AUTH_TOKEN && !authSentSignature) {
                        authSentSignature = true
                        val sig = signAdbAuthToken(privKey, reply.payload)
                        writeAdbPacket(out, CMD_AUTH, AUTH_SIGNATURE, 0, sig)
                    } else {
                        writeAdbPacket(out, CMD_AUTH, AUTH_RSAPUBLICKEY, 0, pubWire)
                        runOnUiThread {
                            tvSyncStatus.text = "Allow ADB prompt on TV screen..."
                        }
                    }
                    reply = readAdbPacket(inp) ?: return AdbShellResult(
                        false,
                        "",
                        "Timed out waiting for TV ADB approval"
                    )
                }

                if (reply.command != CMD_CNXN) {
                    return AdbShellResult(false, "", "ADB handshake rejected")
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

        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
    }
}
