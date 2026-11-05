package com.focusguard.app.service.vpn

import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DnsPacketCodec
 * ==============
 * Low-level DNS packet parser, response builder, and IPv4/UDP frame assembler for TUN I/O.
 */
@Singleton
class DnsPacketCodec @Inject constructor() {

    private companion object {
        const val QTYPE_A = 1
    }

    /**
     * Parses the QNAME from a DNS wire-format message.
     * Returns null if parsing fails.
     */
    fun parseDnsQueryName(dns: ByteArray): String? {
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
        } catch (_: Exception) {
            null
        }
    }

    /** Builds a minimal NXDOMAIN DNS response for a blocked query. */
    fun buildNxDomainResponse(query: ByteArray): ByteArray {
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
    fun buildSafeSearchResponse(query: ByteArray, ips: List<InetAddress>): ByteArray {
        val questionEnd = findQuestionEnd(query) ?: return buildNxDomainResponse(query)
        // Only A queries can be redirected. AAAA (and every other type) get NXDOMAIN,
        // otherwise the resolver could fall back to an unfiltered IPv6 address.
        val qtype = ((query[questionEnd - 4].toInt() and 0xFF) shl 8) or
                (query[questionEnd - 3].toInt() and 0xFF)
        if (qtype != QTYPE_A) return buildNxDomainResponse(query)

        val ipv4s = ips.filterIsInstance<Inet4Address>()
        if (ipv4s.isEmpty()) return buildNxDomainResponse(query)

        val ip = ipv4s.first().address
        // Build: header (12) + question (copy from query) + answer (A record)
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
        response[8] = 0; response[9] = 0      // NSCOUNT=0
        response[10] = 0; response[11] = 0    // ARCOUNT=0 (EDNS OPT is not copied into the body)
        System.arraycopy(question, 0, response, 12, question.size)
        System.arraycopy(answer, 0, response, 12 + question.size, answer.size)
        return response
    }

    fun findQuestionEnd(dns: ByteArray): Int? {
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return i + 1 + 4 // skip null label + QTYPE + QCLASS
            i += len + 1
        }
        return null
    }

    /**
     * Wraps a DNS response payload in a valid UDP/IP frame.
     * Src/dst addresses are swapped (10.0.0.1 -> client).
     */
    fun createDnsResponseFrame(
        response: ByteArray,
        tunBuffer: ByteArray,
        ipHeaderLen: Int,
        srcPort: Int,
        dnsPort: Int = 53
    ): ByteArray {
        val totalLen = ipHeaderLen + 8 + response.size
        val frame = ByteArray(totalLen)

        // IP header: copy original, fix addresses and length
        System.arraycopy(tunBuffer, 0, frame, 0, ipHeaderLen)
        // Swap src/dst IP
        System.arraycopy(tunBuffer, ipHeaderLen - 4, frame, 12, 4) // original dst -> new src
        System.arraycopy(tunBuffer, 12, frame, ipHeaderLen - 4, 4) // original src -> new dst
        // Fix total length
        frame[2] = (totalLen shr 8).toByte()
        frame[3] = (totalLen and 0xFF).toByte()
        // UDP header
        frame[ipHeaderLen] = (dnsPort shr 8).toByte()     // src port = 53
        frame[ipHeaderLen + 1] = (dnsPort and 0xFF).toByte()
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

        return frame
    }

    fun computeIpChecksum(frame: ByteArray, ipHeaderLen: Int) {
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
}

