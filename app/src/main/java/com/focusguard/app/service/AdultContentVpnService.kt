package com.focusguard.app.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.focusguard.app.service.vpn.AdultDomainRegistry
import com.focusguard.app.service.vpn.DnsPacketCodec
import com.focusguard.app.service.vpn.SafeSearchEnforcer
import com.focusguard.app.service.vpn.VpnNotificationHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdultContentVpnService
 * ======================
 * Layer 1 Adult Content Shield: loopback TUN capturing UDP port 53 DNS.
 * Normal TCP/UDP app traffic passes through physical NIC unmodified.
 */
class AdultContentVpnService : VpnService() {

    companion object {
        private const val TAG = "AdultContentVpn"
        const val ACTION_START = "com.focusguard.app.VPN_START"
        const val ACTION_STOP = "com.focusguard.app.VPN_STOP"

        private val UPSTREAM_DNS = listOf("8.8.8.8", "1.1.1.1")
        private const val DNS_PORT = 53
        private val DOT_PORTS = setOf(853)

        val isRunning = AtomicBoolean(false)

        fun start(context: Context) {
            val intent = Intent(context, AdultContentVpnService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdultContentVpnService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private val domainRegistry = AdultDomainRegistry()
    private val dnsCodec = DnsPacketCodec()
    private val safeSearchEnforcer = SafeSearchEnforcer()
    private val notificationHelper by lazy { VpnNotificationHelper(this) }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val vpnExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in AdultContentVpnService: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + vpnExceptionHandler)

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (!isRunning.get()) startVpn()
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
            notificationHelper.createNotificationChannel()
            startForeground(VpnNotificationHelper.NOTIFICATION_ID, notificationHelper.buildNotification())

            val builder = Builder()
                .setSession("FocusGuard Adult Shield DNS")
                .addAddress("10.0.0.1", 32)
                .addRoute("0.0.0.0", 0)        // capture all traffic through TUN
                .addDnsServer("10.0.0.1")       // send device DNS through us
                .setMtu(1500)
                .setBlocking(false)

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

            scope.launch(Dispatchers.IO) {
                safeSearchEnforcer.refreshSafeSearchIps()
                launch {
                    safeSearchEnforcer.scheduleSafeSearchRefresh { scope.isActive && isRunning.get() }
                }
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
                    output.write(buffer, 0, length)
                    continue
                }

                val protocol = buffer[9].toInt() and 0xFF
                if (protocol != 17) {
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
                    continue
                }

                if (dstPort != DNS_PORT) {
                    output.write(buffer, 0, length)
                    continue
                }

                val dnsOffset = ipHeaderLen + 8
                val dnsLength = length - dnsOffset
                if (dnsLength < 12) {
                    output.write(buffer, 0, length)
                    continue
                }

                val dnsPayload = buffer.copyOfRange(dnsOffset, dnsOffset + dnsLength)
                val queriedName = dnsCodec.parseDnsQueryName(dnsPayload)
                if (queriedName == null) {
                    forwardDns(dnsPayload, output, buffer, ipHeaderLen, srcPort, dstPort)
                    continue
                }

                Log.v(TAG, "DNS query intercepted")

                // 1. Check adult domain blocklist
                if (domainRegistry.isAdultDomain(queriedName)) {
                    Log.i(TAG, "DNS block: adult domain blocked")
                    val blocked = dnsCodec.buildNxDomainResponse(dnsPayload)
                    writeDnsResponse(blocked, output, buffer, ipHeaderLen, srcPort)
                    continue
                }

                // 2. SafeSearch redirect for major search engines
                val safeSearchIps = safeSearchEnforcer.getSafeSearchIps(queriedName)
                if (safeSearchIps != null && safeSearchIps.isNotEmpty()) {
                    Log.i(TAG, "DNS SafeSearch redirect applied")
                    val redirected = dnsCodec.buildSafeSearchResponse(dnsPayload, safeSearchIps)
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

    // ── Forwarding & Response ──────────────────────────────────────────────────

    private fun forwardDns(
        dnsPayload: ByteArray,
        output: FileOutputStream,
        tunBuffer: ByteArray,
        ipHeaderLen: Int,
        srcPort: Int,
        dstPort: Int
    ) {
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

    private fun writeDnsResponse(
        response: ByteArray,
        output: FileOutputStream,
        tunBuffer: ByteArray,
        ipHeaderLen: Int,
        srcPort: Int
    ) {
        val frame = dnsCodec.createDnsResponseFrame(response, tunBuffer, ipHeaderLen, srcPort, DNS_PORT)
        output.write(frame)
    }
}
