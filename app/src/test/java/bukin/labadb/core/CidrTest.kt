package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CidrTest {

    private fun ok(text: String): Cidr = when (val parsed = Cidr.parse(text)) {
        is Parsed.Ok -> parsed.value
        is Parsed.Invalid -> throw AssertionError("'$text' rejected: ${parsed.message}")
    }

    private fun invalid(text: String): String = when (val parsed = Cidr.parse(text)) {
        is Parsed.Ok -> throw AssertionError("'$text' accepted as ${parsed.value}")
        is Parsed.Invalid -> parsed.message
    }

    private fun ip(text: String) = Ipv4.parse(text)!!

    @Test
    fun parsesTypicalSubnet() {
        val cidr = ok("192.0.2.0/24")
        assertEquals(24, cidr.prefix)
        assertEquals("192.0.2.0/24", cidr.toString())
    }

    @Test
    fun acceptsPrefixBounds() {
        assertEquals("198.0.0.0/8", ok("198.0.0.0/8").toString())
        assertEquals("192.0.2.7/32", ok("192.0.2.7/32").toString())
    }

    @Test
    fun rejectsPrefixesOutsideEightToThirtyTwo() {
        for (text in listOf("198.0.0.0/7", "0.0.0.0/0", "192.0.2.0/1", "192.0.2.0/33", "192.0.2.0/99", "192.0.2.0/100")) {
            assertTrue(text, invalid(text).contains("/8 to /32"))
        }
    }

    @Test
    fun rejectsMissingOrBrokenPrefix() {
        for (text in listOf("192.0.2.0", "192.0.2.0/", "192.0.2.0/24/8", "192.0.2.0/a", "192.0.2.0/-8", "/24")) {
            invalid(text)
        }
    }

    @Test
    fun rejectsLeadingZeros() {
        invalid("192.0.02.0/24")
        invalid("192.0.2.0/024")
        invalid("192.0.2.0/08")
    }

    @Test
    fun trimsOuterSpacesButRejectsInnerSpaces() {
        assertEquals("192.0.2.0/24", ok("  192.0.2.0/24 \t").toString())
        for (text in listOf("192.0.2.0 /24", "192.0.2.0/ 24", "192.0.2 .0/24", "192.0.2.0/2 4")) invalid(text)
    }

    @Test
    fun emptyTextAsksForTheSubnet() {
        assertTrue(invalid("").startsWith("Type the lab subnet"))
        assertTrue(invalid("   ").startsWith("Type the lab subnet"))
    }

    @Test
    fun clearsHostBits() {
        assertEquals("192.0.2.0/24", ok("192.0.2.77/24").toString())
        assertEquals("198.51.100.128/25", ok("198.51.100.200/25").toString())
        assertEquals("198.0.0.0/8", ok("198.51.100.40/8").toString())
    }

    @Test
    fun containsChecksTheWholePrefix() {
        val cidr = ok("192.0.2.0/24")
        assertTrue(cidr.contains(ip("192.0.2.0")))
        assertTrue(cidr.contains(ip("192.0.2.255")))
        assertFalse(cidr.contains(ip("192.0.3.0")))
        assertFalse(cidr.contains(ip("192.0.1.255")))
        assertFalse(cidr.contains(ip("198.51.100.1")))
    }

    @Test
    fun containsWorksForHighAddresses() {
        // 203.x.x.x is negative as a signed Int: the mask math must not care.
        val cidr = ok("203.0.113.0/24")
        assertTrue(cidr.contains(ip("203.0.113.5")))
        assertFalse(cidr.contains(ip("203.0.114.5")))
        assertFalse(cidr.contains(ip("75.0.113.5")))
    }

    @Test
    fun slash32MatchesOneAddressOnly() {
        val cidr = ok("192.0.2.7/32")
        assertTrue(cidr.contains(ip("192.0.2.7")))
        assertFalse(cidr.contains(ip("192.0.2.6")))
        assertFalse(cidr.contains(ip("192.0.2.8")))
    }

    @Test
    fun slash8Boundaries() {
        val cidr = ok("198.0.0.0/8")
        assertTrue(cidr.contains(ip("198.255.255.255")))
        assertFalse(cidr.contains(ip("199.0.0.0")))
        assertFalse(cidr.contains(ip("197.255.255.255")))
    }

    @Test
    fun constructorRefusesUnnormalisedValues() {
        try {
            Cidr(ip("192.0.2.1"), 24)
            fail("host bits accepted")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            Cidr(0, 7)
            fail("/7 accepted")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
