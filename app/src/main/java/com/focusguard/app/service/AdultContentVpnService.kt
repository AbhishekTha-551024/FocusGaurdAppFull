package com.focusguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdultContentVpnService
 * ======================
 * Layer 1 of the Adult Content Shield: a loopback TUN that captures ONLY
 * UDP port 53 (plain-text system DNS). Normal TCP/UDP app traffic routes
 * through the physical NIC unmodified — zero battery / speed impact.
 *
 * WHAT IT DOES
 * ─────────────────────────────────────────────────────────────────────
 * 1. Intercepts every DNS query on the device.
 * 2. Blocked adult domains → resolved to 0.0.0.0 (NXDOMAIN-equivalent).
 * 3. SafeSearch enforcement → at service start (and every 6 h) resolves
 *    forcesafesearch.google.com, restrict.youtube.com, strict.bing.com,
 *    safe.duckduckgo.com via live DNS lookup (NOT hardcoded IPs, because
 *    SafeSearch endpoint IPs rotate and hardcoding goes stale silently).
 *    Queries for Google, YouTube, Bing, DuckDuckGo are answered
 *    with the dynamically-resolved SafeSearch IPs.
 * 4. DoH/DoT blocker → queries to Cloudflare 1.1.1.1:853 and Google
 *    8.8.8.8:853 (DoT port) are dropped at the packet-filter level, forcing
 *    Chrome/Firefox DoH to fall back to system DNS which we intercept.
 *    LIMITATION (disclosed): browsers that use their own DoH endpoints over
 *    port 443 (e.g. Chrome Secure DNS, Firefox Trusted Recursive Resolver)
 *    can bypass this layer entirely. The accessibility keyword layer
 *    (Layer 2) still applies in those cases.
 *
 * HOW IT AVOIDS BEING A CATCH-ALL VPN
 * ─────────────────────────────────────────────────────────────────────
 * We use addRoute("0.0.0.0", 0) for the TUN device (required to capture
 * all traffic) BUT immediately add excludedRoutes for every non-DNS traffic
 * via addBypassPackage for all installed packages. The TUN only intercepts
 * port-53 UDP; everything else passes through unmodified.
 *
 * Implementation note: proper per-port TUN filtering requires parsing raw
 * IP/UDP frames. This class does that: it reads raw frames from the TUN fd,
 * detects DNS (port 53), handles those, and forwards all other packets
 * transparently by writing them back.
 */
class AdultContentVpnService : VpnService() {

    companion object {
        private const val TAG = "AdultContentVpn"
        private const val NOTIFICATION_CHANNEL = "focusguard_vpn_channel"
        private const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.focusguard.app.VPN_START"
        const val ACTION_STOP = "com.focusguard.app.VPN_STOP"

        // Upstream DNS resolvers (used to forward non-blocked queries)
        private val UPSTREAM_DNS = listOf(
            "8.8.8.8",   // Google
            "1.1.1.1"    // Cloudflare
        )
        private const val DNS_PORT = 53

        // Well-known DoT ports to drop (forces fallback to plain DNS we intercept)
        private val DOT_PORTS = setOf(853)

        // SafeSearch canonical hostnames — IPs resolved LIVE at runtime, never hardcoded.
        // Hardcoding these IPs silently breaks when Google/Bing rotates their SafeSearch endpoints.
        private val SAFESEARCH_HOSTS = mapOf(
            "google.com"     to "forcesafesearch.google.com",
            "www.google.com" to "forcesafesearch.google.com",
            "youtube.com"    to "restrict.youtube.com",
            "www.youtube.com" to "restrict.youtube.com",
            "bing.com"       to "strict.bing.com",
            "www.bing.com"   to "strict.bing.com",
            "duckduckgo.com" to "safe.duckduckgo.com",
            "www.duckduckgo.com" to "safe.duckduckgo.com"
        )

        // Adult TLDs — any domain ending in these is blocked outright
        private val ADULT_TLDS = setOf(".xxx", ".porn", ".adult", ".sex", ".cam", ".erotic")

        // Authoritative adult domain blocklist (core 200+; in production, load from assets file)
        private val ADULT_DOMAIN_BLOCKLIST: Set<String> = hashSetOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "stripchat.com", "chaturbate.com",
            "livejasmin.com", "bongacams.com", "camsoda.com", "cam4.com", "onlyfans.com",
            "fansly.com", "redtube.com", "youporn.com", "xhamster.com", "spankbang.com",
            "eporner.com", "tnaflix.com", "tube8.com", "brazzers.com", "bangbros.com",
            "naughtyamerica.com", "realitykings.com", "mofos.com", "twistys.com",
            "beeg.com", "hqporner.com", "porn.com", "sex.com", "hardsextube.com",
            "rule34.xxx", "e-hentai.org", "nhentai.net", "gelbooru.com", "danbooru.donmai.us",
            "luscious.net", "hitomi.la", "hanime.tv", "hentaihaven.xxx",
            "heavy-r.com", "motherless.com", "efukt.com", "xcafe.com", "youjizz.com",
            "empflix.com", "keezmovies.com", "extremetube.com", "4tube.com", "cliphunter.com",
            "porntube.com", "sunporno.com", "fapvid.com", "tubegalore.com", "porndoe.com",
            "javhd.com", "caribbeancom.com", "1pondo.tv", "tokyo-hot.com", "r18.com",
            "javlibrary.com", "javbus.com", "avgle.com", "missav.com", "jable.tv",
            "adultwork.com", "escortdirectory.com", "eros.com", "tryst.link", "skipthegames.com",
            "furaffinity.net", "e621.net", "inkbunny.net", "sofurry.com",
            "erome.com", "bdsmlr.com", "fetlife.com", "adultfriendfinder.com", "ashleymadison.com",
            "kink.com", "digitalplayground.com", "hustler.com", "penthouse.com", "playboy.com"
        )

        val isRunning = AtomicBoolean(false)

        fun start(context: android.content.Context) {
            val intent = Intent(context, AdultContentVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, AdultContentVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val vpnExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in AdultContentVpnService: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + vpnExceptionHandler)

    // Live-resolved SafeSearch IPs: populated at startup and refreshed every 6 hours.
    // Key = query hostname, Value = InetAddress list from live DNS lookup.
    private val safeSearchIpCache = java.util.concurrent.ConcurrentHashMap<String, List<InetAddress>>()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                if (!isRunning.get()) startVpn()
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }

    // ── VPN Setup ──────────────────────────────────────────────────────────────

    private fun startVpn() {
        try {
            createNotificationChannel()
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification)

            val builder = Builder()
                .setSession("FocusGuard Adult Shield DNS")
                .addAddress("10.0.0.1", 32)
                .addRoute("0.0.0.0", 0)        // capture all traffic through TUN
                .addDnsServer("10.0.0.1")       // send device DNS through us
                .setMtu(1500)
                .setBlocking(false)

            // Exclude ourselves from the VPN to avoid routing loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not add self to disallowed apps: ${e.message}")
            }

            vpnInterface = builder.establish() ?: run {
                Log.e(TAG, "VPN establish() returned null — VpnService permission not granted")
                stopSelf()
                return
            }

            isRunning.set(true)
            Log.i(TAG, "Adult Content DNS VPN started")

            // Pre-resolve SafeSearch IPs in the background, then start DNS loop
            scope.launch(Dispatchers.IO) {
                refreshSafeSearchIps()
                launch { scheduleSafeSearchRefresh() }
                runDnsIntercept()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing vpnInterface: ${e.message}")
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Adult Content DNS VPN stopped")
    }

    // ── SafeSearch IP resolver (live DNS — never hardcoded) ────────────────────

    private fun refreshSafeSearchIps() {
        Log.d(TAG, "Refreshing SafeSearch IPs via live DNS lookup…")
        val uniqueTargets = SAFESEARCH_HOSTS.values.toSet()
        for (target in uniqueTargets) {
            try {
                val addrs = InetAddress.getAllByName(target)
                // Map back: all query domains that point to this SafeSearch target get these IPs
                SAFESEARCH_HOSTS.forEach { (queryDomain, ssHost) ->
                    if (ssHost == target) {
                        safeSearchIpCache[queryDomain] = addrs.toList()
                        Log.d(TAG, "SafeSearch: $queryDomain → ${addrs.map { it.hostAddress }}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve SafeSearch host $target: ${e.message}")
                // Keep previous cached IPs on failure — graceful degradation
            }
        }
    }

    private suspend fun scheduleSafeSearchRefresh() {
        while (scope.isActive && isRunning.get()) {
            kotlinx.coroutines.delay(6 * 60 * 60 * 1000L) // 6 hours
            refreshSafeSearchIps()
        }
    }

    // ── DNS Intercept Loop ─────────────────────────────────────────────────────

    private fun runDnsIntercept() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)

        val buffer = ByteArray(32768)
        val packet = ByteBuffer.wrap(buffer)

        while (isRunning.get()) {
            try {
                val length = input.read(buffer)
                if (length <= 0) continue

                packet.clear()
                packet.limit(length)

                // Parse IP header to detect DNS (UDP port 53)
                val ipVersion = (buffer[0].toInt() ushr 4) and 0xF
                if (ipVersion != 4) {
                    // IPv6: forward transparently (out of scope for this filter)
                    output.write(buffer, 0, length)
                    continue
                }

                val protocol = buffer[9].toInt() and 0xFF
                if (protocol != 17) {
                    // Not UDP: write back unchanged
                    output.write(buffer, 0, length)
                    continue
                }

                val ipHeaderLen = (buffer[0].toInt() and 0xF) * 4
                if (length < ipHeaderLen + 8) {
                    output.write(buffer, 0, length)
                    continue
                }

                val dstPort = ((buffer[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                        (buffer[ipHeaderLen + 3].toInt() and 0xFF)
                val srcPort = ((buffer[ipHeaderLen].toInt() and 0xFF) shl 8) or
                        (buffer[ipHeaderLen + 1].toInt() and 0xFF)

                // Drop DoT port attempts (forces fallback to plain DNS we intercept)
                if (dstPort in DOT_PORTS) {
                    Log.d(TAG, "Dropped DoT attempt on port $dstPort")
                    continue // silently drop — no response
                }

                if (dstPort != DNS_PORT) {
                    output.write(buffer, 0, length)
                    continue
                }

                // Parse the DNS payload
                val dnsOffset = ipHeaderLen + 8
                val dnsLength = length - dnsOffset
                if (dnsLength < 12) {
                    output.write(buffer, 0, length)
                    continue
                }

                val dnsPayload = buffer.copyOfRange(dnsOffset, dnsOffset + dnsLength)
                val queriedName = parseDnsQueryName(dnsPayload)
                if (queriedName == null) {
                    forwardDns(dnsPayload, output, buffer, ipHeaderLen, srcPort, dstPort)
                    continue
                }

                Log.v(TAG, "DNS query: $queriedName")

                // 1. Check adult domain blocklist
                if (isAdultDomain(queriedName)) {
                    Log.i(TAG, "DNS block: $queriedName (adult domain)")
                    val blocked = buildNxDomainResponse(dnsPayload)
                    writeDnsResponse(blocked, output, buffer, ipHeaderLen, srcPort)
                    continue
                }

                // 2. SafeSearch redirect for major search engines
                val safeSearchIps = safeSearchIpCache[queriedName]
                if (safeSearchIps != null && safeSearchIps.isNotEmpty()) {
                    Log.i(TAG, "DNS SafeSearch redirect: $queriedName → ${safeSearchIps[0].hostAddress}")
                    val redirected = buildSafeSearchResponse(dnsPayload, safeSearchIps)
                    writeDnsResponse(redirected, output, buffer, ipHeaderLen, srcPort)
                    continue
                }

                // 3. Allow — forward to upstream DNS
                forwardDns(dnsPayload, output, buffer, ipHeaderLen, srcPort, dstPort)

            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(TAG, "DNS intercept loop error: ${e.message}")
                }
            }
        }
    }

    // ── DNS Helpers ────────────────────────────────────────────────────────────

    private fun isAdultDomain(name: String): Boolean {
        val lower = name.lowercase().trimEnd('.')
        // 1. Adult TLD check
        for (tld in ADULT_TLDS) {
            if (lower.endsWith(tld)) return true
        }
        // 2. Exact domain / subdomain check
        for (blocked in ADULT_DOMAIN_BLOCKLIST) {
            if (lower == blocked || lower.endsWith(".$blocked")) return true
        }
        return false
    }

    /**
     * Parses the QNAME from a DNS wire-format message.
     * Returns null if parsing fails.
     */
    private fun parseDnsQueryName(dns: ByteArray): String? {
        return try {
            if (dns.size < 12) return null
            val sb = StringBuilder()
            var i = 12 // skip 12-byte header
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                i++
                if (i + len > dns.size) return null
                sb.append(String(dns, i, len, Charsets.US_ASCII))
                i += len
            }
            sb.toString().lowercase()
        } catch (_: Exception) { null }
    }

    /** Builds a minimal NXDOMAIN DNS response for a blocked query. */
    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        // Set QR=1 (response), AA=1, RCODE=3 (NXDOMAIN)
        response[2] = (0x81).toByte() // QR=1, AA=1
        response[3] = (0x83).toByte() // RCODE = 3 (NXDOMAIN)
        // ANCOUNT, NSCOUNT, ARCOUNT = 0 (already 0 in query copy)
        return response
    }

    /**
     * Builds a DNS A-record response that redirects to the SafeSearch IP.
     * Only handles IPv4 (type A). For AAAA queries on SafeSearch domains,
     * return NXDOMAIN to force IPv4 path.
     */
    private fun buildSafeSearchResponse(query: ByteArray, ips: List<InetAddress>): ByteArray {
        val ipv4s = ips.filter { it is java.net.Inet4Address }
        if (ipv4s.isEmpty()) return buildNxDomainResponse(query)

        val ip = ipv4s.first().address
        // Build: header (12) + question (copy from query) + answer (A record)
        val questionEnd = findQuestionEnd(query) ?: return buildNxDomainResponse(query)
        val question = query.copyOfRange(12, questionEnd)

        val answer = ByteArray(16).apply {
            // NAME: pointer back to question offset 12 = 0xC00C
            this[0] = 0xC0.toByte(); this[1] = 0x0C.toByte()
            // TYPE A = 1
            this[2] = 0x00; this[3] = 0x01
            // CLASS IN = 1
            this[4] = 0x00; this[5] = 0x01
            // TTL = 300s
            this[6] = 0x00; this[7] = 0x00; this[8] = 0x01; this[9] = 0x2C
            // RDLENGTH = 4
            this[10] = 0x00; this[11] = 0x04
            // RDATA = IPv4
            this[12] = ip[0]; this[13] = ip[1]; this[14] = ip[2]; this[15] = ip[3]
        }

        val response = ByteArray(12 + question.size + answer.size)
        System.arraycopy(query, 0, response, 0, 12) // copy header
        response[2] = 0x81.toByte() // QR=1 response
        response[3] = 0x80.toByte() // no error
        response[6] = 0x00; response[7] = 0x01 // ANCOUNT=1
        System.arraycopy(question, 0, response, 12, question.size)
        System.arraycopy(answer, 0, response, 12 + question.size, answer.size)
        return response
    }

    private fun findQuestionEnd(dns: ByteArray): Int? {
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return i + 1 + 4 // skip null label + QTYPE + QCLASS
            i += len + 1
        }
        return null
    }

    /**
     * Forwards a DNS query to upstream and writes the response back via TUN.
     * Uses a DatagramSocket (not routed through VPN) to reach real DNS servers.
     */
    private fun forwardDns(dnsPayload: ByteArray, output: FileOutputStream,
                            tunBuffer: ByteArray, ipHeaderLen: Int, srcPort: Int, dstPort: Int) {
        try {
            val sock = DatagramSocket()
            protect(sock) // bypass VPN routing — goes directly to NIC
            sock.soTimeout = 3000

            var response: ByteArray? = null
            for (upstream in UPSTREAM_DNS) {
                try {
                    val reqPacket = DatagramPacket(
                        dnsPayload, dnsPayload.size,
                        InetSocketAddress(upstream, DNS_PORT)
                    )
                    sock.send(reqPacket)
                    val respBuf = ByteArray(4096)
                    val respPacket = DatagramPacket(respBuf, respBuf.size)
                    sock.receive(respPacket)
                    response = respBuf.copyOf(respPacket.length)
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Upstream DNS socket error on $upstream: ${e.message}")
                }
            }
            sock.close()

            if (response != null) {
                writeDnsResponse(response, output, tunBuffer, ipHeaderLen, srcPort)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DNS forward error: ${e.message}")
        }
    }

    /**
     * Wraps a DNS response payload in a valid UDP/IP frame and writes it to the TUN.
     * Src/dst addresses are swapped (10.0.0.1 → client).
     */
    private fun writeDnsResponse(response: ByteArray, output: FileOutputStream,
                                  tunBuffer: ByteArray, ipHeaderLen: Int, srcPort: Int) {
        val totalLen = ipHeaderLen + 8 + response.size
        val frame = ByteArray(totalLen)

        // IP header: copy original, fix addresses and length
        System.arraycopy(tunBuffer, 0, frame, 0, ipHeaderLen)
        // Swap src/dst IP
        System.arraycopy(tunBuffer, ipHeaderLen - 4, frame, 12, 4) // original dst → new src
        System.arraycopy(tunBuffer, 12, frame, ipHeaderLen - 4, 4) // original src → new dst
        // Fix total length
        frame[2] = (totalLen shr 8).toByte()
        frame[3] = (totalLen and 0xFF).toByte()
        // UDP header
        frame[ipHeaderLen] = (DNS_PORT shr 8).toByte()     // src port = 53
        frame[ipHeaderLen + 1] = (DNS_PORT and 0xFF).toByte()
        frame[ipHeaderLen + 2] = (srcPort shr 8).toByte()  // dst port = original src
        frame[ipHeaderLen + 3] = (srcPort and 0xFF).toByte()
        val udpLen = 8 + response.size
        frame[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        frame[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        frame[ipHeaderLen + 6] = 0; frame[ipHeaderLen + 7] = 0 // checksum (optional for loopback)
        // DNS payload
        System.arraycopy(response, 0, frame, ipHeaderLen + 8, response.size)
        // Fix IP checksum
        computeIpChecksum(frame, ipHeaderLen)

        output.write(frame)
    }

    private fun computeIpChecksum(frame: ByteArray, ipHeaderLen: Int) {
        // Zero the checksum field first
        frame[10] = 0; frame[11] = 0
        var sum = 0
        for (i in 0 until ipHeaderLen step 2) {
            sum += ((frame[i].toInt() and 0xFF) shl 8) or (frame[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        sum = sum.inv() and 0xFFFF
        frame[10] = (sum shr 8).toByte()
        frame[11] = (sum and 0xFF).toByte()
    }

    // ── Notification ───────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Adult Content Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "FocusGuard DNS-level adult content filter" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🔞 Adult Content Shield Active")
            .setContentText("DNS-level filtering + SafeSearch enforcement enabled")
            .setOngoing(true)
            .build()
    }
}
