package com.mhtime.tvauto

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Reads UTC time from the HTTP `Date` response header (NTP/UDP-123 is blocked by the ISP). */
object TimeFetcher {
    /** HTTP first: HTTPS cert validation fails when the TV clock is far off. */
    val SOURCES = listOf(
        "http://google.com",
        "https://www.google.com",
        "http://cloudflare.com",
        "http://connectivitycheck.gstatic.com/generate_204",
    )

    private const val MIN_VALID_MS = 1_704_067_200_000L  // 2024-01-01
    private const val MAX_VALID_MS = 4_102_444_800_000L  // 2100-01-01

    /** [epochAtReceiveMs] is valid at monotonic instant [elapsedAtReceiveMs]; extrapolate with [nowEpochMs]. */
    class Result(
        val epochAtReceiveMs: Long,
        val elapsedAtReceiveMs: Long,
        val source: String,
        val rttMs: Long,
    ) {
        fun nowEpochMs(): Long = epochAtReceiveMs + (SystemClock.elapsedRealtime() - elapsedAtReceiveMs)
    }

    fun fetch(log: (String) -> Unit): Result? {
        for (url in SOURCES) {
            try {
                val r = fetchOne(url)
                if (r != null) {
                    log("Time from $url (rtt ${r.rttMs} ms)")
                    return r
                }
                log("$url: no usable Date header")
            } catch (e: Exception) {
                log("$url failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return null
    }

    private fun fetchOne(url: String): Result? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = false      // the first response already carries Date
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            conn.useCaches = false
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) TvTimeSync/1.0")

            conn.connect()                            // TCP (+TLS) handshake excluded from the RTT below
            val t0 = SystemClock.elapsedRealtime()
            conn.responseCode
            val t1 = SystemClock.elapsedRealtime()

            val header = conn.getHeaderField("Date") ?: return null
            val dateMs = parseHttpDate(header) ?: return null
            val rtt = t1 - t0
            // Date has 1 s resolution (true time is within [d, d+1s)) -> +500 ms; server stamped the
            // response about half an RTT before we received it -> +rtt/2.
            val epoch = dateMs + 500 + rtt / 2
            if (epoch !in MIN_VALID_MS..MAX_VALID_MS) return null
            return Result(epoch, t1, url, rtt)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseHttpDate(s: String): Long? = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }.parse(s.trim())?.time
    } catch (e: Exception) {
        null
    }
}
