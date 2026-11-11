package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabMatcherTest {

    private fun ip(text: String) = Ipv4.parse(text)!!
    private val subnet = (Cidr.parse("192.0.2.0/24") as Parsed.Ok).value
    private val noGateway = LabConfig(subnet, null)
    private val withGateway = LabConfig(subnet, ip("192.0.2.1"))

    private fun wifi(
        addresses: List<String> = listOf("192.0.2.50"),
        gateways: List<String> = listOf("192.0.2.1"),
        isWifi: Boolean = true,
        isVpn: Boolean = false,
    ) = NetworkSnapshot(100L, isWifi, isVpn, addresses.map(::ip), gateways.map(::ip))

    @Test
    fun matchesAddressInsideSubnet() {
        assertEquals(MatchResult.MATCH, LabMatcher.match(noGateway, wifi()))
    }

    @Test
    fun mustBeWifi() {
        assertEquals(MatchResult.NOT_WIFI, LabMatcher.match(noGateway, wifi(isWifi = false)))
    }

    @Test
    fun vpnIsNeverTheLab() {
        assertEquals(MatchResult.NOT_WIFI, LabMatcher.match(noGateway, wifi(isVpn = true)))
    }

    @Test
    fun localOnlyLinkIsNeverTheLab() {
        // Wi-Fi Direct or hotspot, even with an address inside the lab subnet.
        assertEquals(MatchResult.NOT_WIFI, LabMatcher.match(noGateway, wifi().copy(isLocalOnly = true)))
        assertEquals(MatchResult.NOT_WIFI, LabMatcher.match(withGateway, wifi().copy(isLocalOnly = true)))
    }

    @Test
    fun realWifiMeansJoinedWifiOnly() {
        assertTrue(wifi().isRealWifi)
        assertFalse(wifi(isWifi = false).isRealWifi)
        assertFalse(wifi(isVpn = true).isRealWifi)
        assertFalse(wifi().copy(isLocalOnly = true).isRealWifi)
    }

    @Test
    fun needsAnIpv4Address() {
        assertEquals(MatchResult.NO_IPV4_ADDRESS, LabMatcher.match(noGateway, wifi(addresses = emptyList())))
    }

    @Test
    fun addressOutsideSubnet() {
        assertEquals(MatchResult.ADDRESS_OUTSIDE_SUBNET, LabMatcher.match(noGateway, wifi(addresses = listOf("198.51.100.7"))))
    }

    @Test
    fun anyOneAddressInsideIsEnough() {
        assertEquals(MatchResult.MATCH, LabMatcher.match(noGateway, wifi(addresses = listOf("198.51.100.7", "192.0.2.9"))))
    }

    @Test
    fun gatewayMustMatchWhenSet() {
        assertEquals(MatchResult.MATCH, LabMatcher.match(withGateway, wifi()))
        assertEquals(MatchResult.GATEWAY_MISMATCH, LabMatcher.match(withGateway, wifi(gateways = listOf("192.0.2.254"))))
        assertEquals(MatchResult.GATEWAY_MISMATCH, LabMatcher.match(withGateway, wifi(gateways = emptyList())))
    }

    @Test
    fun gatewayIgnoredWhenNotSet() {
        assertEquals(MatchResult.MATCH, LabMatcher.match(noGateway, wifi(gateways = emptyList())))
    }

    @Test
    fun subnetIsCheckedBeforeGateway() {
        assertEquals(
            MatchResult.ADDRESS_OUTSIDE_SUBNET,
            LabMatcher.match(withGateway, wifi(addresses = listOf("198.51.100.7"), gateways = listOf("192.0.2.1"))),
        )
    }
}
