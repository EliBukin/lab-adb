package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabConfigTest {

    private fun valid(subnet: String, gateway: String): LabConfig {
        val check = LabConfig.parse(subnet, gateway)
        assertTrue("$check", check is ConfigCheck.Valid)
        return (check as ConfigCheck.Valid).config
    }

    @Test
    fun subnetWithGateway() {
        val config = valid("192.0.2.0/24", " 192.0.2.1 ")
        assertEquals("192.0.2.0/24", config.subnet.toString())
        assertEquals(Ipv4.parse("192.0.2.1"), config.gateway)
        assertEquals("192.0.2.1", config.gatewayText)
    }

    @Test
    fun gatewayIsOptional() {
        assertNull(valid("192.0.2.0/24", "").gateway)
        assertNull(valid("192.0.2.0/24", "   ").gateway)
        assertEquals("", valid("192.0.2.0/24", "").gatewayText)
    }

    @Test
    fun badGatewayIsReportedOnItsOwnField() {
        val check = LabConfig.parse("192.0.2.0/24", "192.0.2") as ConfigCheck.Invalid
        assertNull(check.subnetError)
        assertNotNull(check.gatewayError)
    }

    @Test
    fun gatewayMustNotHavePrefix() {
        assertTrue(LabConfig.parse("192.0.2.0/24", "192.0.2.1/24") is ConfigCheck.Invalid)
    }

    @Test
    fun bothErrorsAtOnce() {
        val check = LabConfig.parse("192.0.2.0/7", "300.1.1.1") as ConfigCheck.Invalid
        assertNotNull(check.subnetError)
        assertNotNull(check.gatewayError)
    }

    @Test
    fun emptySubnetIsInvalid() {
        val check = LabConfig.parse("", "") as ConfigCheck.Invalid
        assertNotNull(check.subnetError)
        assertNull(check.gatewayError)
    }
}
