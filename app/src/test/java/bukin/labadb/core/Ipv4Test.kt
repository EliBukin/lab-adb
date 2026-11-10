package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4Test {

    @Test
    fun parsesValidAddresses() {
        assertEquals((192 shl 24) or (2 shl 8) or 1, Ipv4.parse("192.0.2.1"))
        assertEquals(0, Ipv4.parse("0.0.0.0"))
        assertEquals(-1, Ipv4.parse("255.255.255.255"))
        assertEquals((203 shl 24) or (113 shl 8) or 9, Ipv4.parse("203.0.113.9"))
    }

    @Test
    fun rejectsMalformedAddresses() {
        val bad = listOf(
            "", "1.2.3", "1.2.3.4.5", "1..3.4", ".1.2.3", "1.2.3.", "a.b.c.d", "1.2.3.4/24",
            "256.0.0.1", "1.2.3.999", "1234.1.1.1", "-1.2.3.4", "+1.2.3.4", "1.2.3.0x4",
        )
        for (text in bad) assertNull(text, Ipv4.parse(text))
    }

    @Test
    fun rejectsLeadingZeros() {
        for (text in listOf("01.2.3.4", "1.2.3.04", "192.000.2.1", "00.0.0.0")) assertNull(text, Ipv4.parse(text))
    }

    @Test
    fun rejectsSpacesAndNonAsciiDigits() {
        for (text in listOf(" 1.2.3.4", "1.2.3.4 ", "1. 2.3.4", "1.2 .3.4", "١.2.3.4", "1.2.3.٤")) {
            assertNull(text, Ipv4.parse(text))
        }
    }

    @Test
    fun formatsAndRoundTrips() {
        for (text in listOf("0.0.0.0", "192.0.2.1", "203.0.113.255", "255.255.255.255", "198.51.100.1")) {
            assertEquals(text, Ipv4.format(Ipv4.parse(text)!!))
        }
    }

    @Test
    fun readsRawBytes() {
        assertEquals(Ipv4.parse("192.0.2.200"), Ipv4.fromBytes(byteArrayOf(192.toByte(), 0, 2, 200.toByte())))
        assertNull(Ipv4.fromBytes(ByteArray(16)))
        assertNull(Ipv4.fromBytes(ByteArray(3)))
    }
}
