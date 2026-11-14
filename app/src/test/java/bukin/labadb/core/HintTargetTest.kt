package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintTargetTest {

    @Test
    fun parsesAnAddressAndPort() {
        val target = (HintTarget.parse("192.0.2.10:47113") as Parsed.Ok).value!!
        assertEquals(Ipv4.parse("192.0.2.10"), target.address)
        assertEquals(47113, target.port)
        assertEquals("192.0.2.10:47113", target.toString())
        assertEquals(HintTarget.EXAMPLE, target.toString())
    }

    @Test
    fun trimsSurroundingSpaces() {
        val target = (HintTarget.parse("  198.51.100.7:1 \n") as Parsed.Ok).value!!
        assertEquals("198.51.100.7:1", target.toString())
        assertEquals("203.0.113.9:65535", (HintTarget.parse("203.0.113.9:65535") as Parsed.Ok).value.toString())
    }

    @Test
    fun emptyMeansNotSet() {
        assertNull((HintTarget.parse("") as Parsed.Ok).value)
        assertNull((HintTarget.parse("   ") as Parsed.Ok).value)
    }

    @Test
    fun rejectsAnythingButAnIpv4LiteralAndAPort() {
        val bad = listOf(
            "192.0.2.10", "192.0.2.10:", ":47113", "192.0.2.10:0", "192.0.2.10:65536", "192.0.2.10:047113",
            "192.0.2.10:47113:1", "192.0.2.10: 47113", "192.0.2.10 :47113", "192.0.2.10:+1", "192.0.2.10:-1",
            "192.0.2.10:١٢٣", "192.0.2.10:123456", "anxel.local:47113", "localhost:47113", "[2001:db8::1]:47113",
            "2001:db8::1", "192.0.2:47113", "192.0.2.256:47113", "192.0.2.010:47113", "http://192.0.2.10:47113",
        )
        for (text in bad) assertTrue(text, HintTarget.parse(text) is Parsed.Invalid)
    }

    @Test
    fun rejectsAddressesThatAreNotOneComputer() {
        val bad = listOf(
            "0.0.0.0:47113", "0.1.2.3:47113", "127.0.0.1:47113", "127.255.255.254:47113", "224.0.0.251:5353",
            "239.255.255.250:1900", "240.0.0.1:47113", "255.255.255.255:47113",
        )
        for (text in bad) assertTrue(text, HintTarget.parse(text) is Parsed.Invalid)
    }
}
