package com.focusguard.app.service.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DnsPacketCodecTest {

    private lateinit var codec: DnsPacketCodec

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
}

