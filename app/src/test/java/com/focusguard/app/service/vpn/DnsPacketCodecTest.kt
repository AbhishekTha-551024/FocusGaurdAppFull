package com.focusguard.app.service.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class DnsPacketCodecTest {

    private lateinit var codec: DnsPacketCodec

    private fun buildQuery(qtype: Int): ByteArray {
        val header = byteArrayOf(
            0x12, 0x34,
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00
        )
        val qname = byteArrayOf(
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00
        )
        val qtypeQclass = byteArrayOf(0x00, qtype.toByte(), 0x00, 0x01)
        return header + qname + qtypeQclass
    }

    @Before
    fun setUp() {
        codec = DnsPacketCodec()
    }

    @Test
    fun testParseDnsQueryNameForExampleCom() {
        // Hand-built DNS wire-format query for "example.com"
        // 12-byte header: ID=0x1234, Flags=0x0100, QDCOUNT=1, ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
        val header = byteArrayOf(
            0x12, 0x34,
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00
        )
        // QNAME: 7 "example" 3 "com" 0
        val qname = byteArrayOf(
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00
        )
        // QTYPE=A (1), QCLASS=IN (1)
        val qtypeQclass = byteArrayOf(0x00, 0x01, 0x00, 0x01)

        val queryPacket = header + qname + qtypeQclass

        val parsedName = codec.parseDnsQueryName(queryPacket)
        assertEquals("example.com", parsedName)

        val questionEnd = codec.findQuestionEnd(queryPacket)
        assertNotNull(questionEnd)
        assertEquals(queryPacket.size, questionEnd)

        val nxResponse = codec.buildNxDomainResponse(queryPacket)
        assertEquals((0x81).toByte(), nxResponse[2]) // QR=1, AA=1
        assertEquals((0x83).toByte(), nxResponse[3]) // RCODE=3 (NXDOMAIN)
    }

    @Test
    fun testSafeSearchRedirectForAQuery() {
        val query = buildQuery(1)
        val response = codec.buildSafeSearchResponse(query, listOf(InetAddress.getByName("1.2.3.4")))

        assertEquals(0x81.toByte(), response[2])                 // QR=1
        assertEquals(0, response[3].toInt() and 0x0F)            // RCODE=NOERROR
        assertEquals(1, ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)) // ANCOUNT=1
        assertEquals(query.size + 16, response.size)             // question + 16-byte A record
        assertEquals("1.2.3.4", "${response[response.size - 4].toInt() and 0xFF}.${response[response.size - 3].toInt() and 0xFF}.${response[response.size - 2].toInt() and 0xFF}.${response[response.size - 1].toInt() and 0xFF}")
    }

    @Test
    fun testSafeSearchRedirectForAaaaQueryIsNxDomain() {
        val response = codec.buildSafeSearchResponse(
            buildQuery(28),
            listOf(InetAddress.getByName("1.2.3.4"))
        )
        // AAAA must never be answered with an A record, or the resolver falls back to IPv6.
        assertEquals(3, response[3].toInt() and 0x0F)
        assertEquals(0, ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF))
    }

    @Test
    fun testSafeSearchResponseDropsEdnsCounts() {
        val query = buildQuery(1)
        // Simulate an EDNS0 query: ARCOUNT=1 in the header
        query[10] = 0x00; query[11] = 0x01

        val response = codec.buildSafeSearchResponse(query, listOf(InetAddress.getByName("1.2.3.4")))
        assertEquals(0, ((response[8].toInt() and 0xFF) shl 8) or (response[9].toInt() and 0xFF))   // NSCOUNT
        assertEquals(0, ((response[10].toInt() and 0xFF) shl 8) or (response[11].toInt() and 0xFF)) // ARCOUNT
        assertTrue(response.size > query.size) // answer appended
    }
}

