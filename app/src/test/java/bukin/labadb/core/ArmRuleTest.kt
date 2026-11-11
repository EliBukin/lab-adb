package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmRuleTest {

    private fun ip(text: String) = Ipv4.parse(text)!!
    private val config = LabConfig((Cidr.parse("192.0.2.0/24") as Parsed.Ok).value, null)

    private val labWifi = NetworkSnapshot(1L, isWifi = true, isVpn = false, listOf(ip("192.0.2.50")), emptyList())
    private val otherWifi = NetworkSnapshot(2L, isWifi = true, isVpn = false, listOf(ip("198.51.100.7")), emptyList())
    private val cellular = NetworkSnapshot(3L, isWifi = false, isVpn = false, listOf(ip("203.0.113.9")), emptyList())
    private val vpn = NetworkSnapshot(4L, isWifi = true, isVpn = true, listOf(ip("203.0.113.10")), emptyList())
    private val localLink =
        NetworkSnapshot(5L, isWifi = true, isVpn = false, listOf(ip("192.0.2.1")), emptyList(), isLocalOnly = true)

    private val everything = ArmPlan(backupJob = true, settingJob = true, wifiTrigger = true)
    private val jobsOnly = ArmPlan(backupJob = true, settingJob = true, wifiTrigger = false)

    @Test
    fun noWifiArmsTheWifiTriggerAndBothJobs() {
        assertEquals(everything, ArmRule.plan(true, config, emptyList()))
        assertEquals(everything, ArmRule.plan(true, config, listOf(cellular)))
    }

    @Test
    fun aVpnAloneIsNotWifi() {
        assertEquals(everything, ArmRule.plan(true, config, listOf(vpn, cellular)))
    }

    @Test
    fun aLocalLinkAloneKeepsTheWifiTriggerArmed() {
        // Step 5: the car's Wi-Fi Direct link took the trigger down. It must not.
        assertEquals(everything, ArmRule.plan(true, config, listOf(localLink)))
        assertEquals(everything, ArmRule.plan(true, config, listOf(localLink, cellular)))
    }

    @Test
    fun aLocalLinkBesideRealWifiChangesNothing() {
        assertEquals(jobsOnly, ArmRule.plan(true, config, listOf(localLink, labWifi)))
        assertEquals(jobsOnly, ArmRule.plan(true, config, listOf(localLink, otherWifi)))
    }

    @Test
    fun labWifiConnectedArmsOnlyTheJobs() {
        assertEquals(jobsOnly, ArmRule.plan(true, config, listOf(labWifi)))
    }

    @Test
    fun otherWifiConnectedArmsOnlyTheJobs() {
        assertEquals(jobsOnly, ArmRule.plan(true, config, listOf(otherWifi)))
        assertEquals(jobsOnly, ArmRule.plan(true, config, listOf(cellular, otherWifi)))
    }

    @Test
    fun switchOffDisarmsEverything() {
        for (networks in listOf(emptyList(), listOf(labWifi), listOf(otherWifi), listOf(cellular))) {
            assertEquals(ArmRule.DISARMED, ArmRule.plan(false, config, networks))
        }
    }

    @Test
    fun noLabSubnetDisarmsEverything() {
        assertEquals(ArmRule.DISARMED, ArmRule.plan(true, null, emptyList()))
        assertEquals(ArmRule.DISARMED, ArmRule.plan(true, null, listOf(labWifi)))
    }

    @Test
    fun triggerEventOnlyWhenTheStateChanges() {
        assertNull(ArmRule.triggerEvent(wasArmed = true, plan = everything))
        assertNull(ArmRule.triggerEvent(wasArmed = false, plan = jobsOnly))
        assertNull(ArmRule.triggerEvent(wasArmed = false, plan = ArmRule.DISARMED))
    }

    @Test
    fun triggerEventTexts() {
        assertEquals("Instant trigger armed", ArmRule.triggerEvent(wasArmed = false, plan = everything))
        assertEquals("Instant trigger off: Wi-Fi connected", ArmRule.triggerEvent(wasArmed = true, plan = jobsOnly))
    }

    @Test
    fun switchingTheAppOffIsSilent() {
        // The switch logs its own event.
        assertNull(ArmRule.triggerEvent(wasArmed = true, plan = ArmRule.DISARMED))
    }

    @Test
    fun theStep5TripListsOnlyTwoTriggerEvents() {
        // At the lab, leave, checks without Wi-Fi, the car's link, back at the lab. Each entry is
        // one re-arm (setting job, backup check...), fed through the same rule Arming uses.
        val trip = listOf(
            listOf(labWifi), listOf(labWifi),
            emptyList(), listOf(cellular),
            listOf(localLink, cellular), listOf(localLink),
            listOf(labWifi), listOf(labWifi, cellular),
        )
        var armed = false
        val events = mutableListOf<String>()
        for (networks in trip) {
            val plan = ArmRule.plan(true, config, networks)
            ArmRule.triggerEvent(armed, plan)?.let(events::add)
            armed = plan.wifiTrigger
        }
        assertEquals(listOf(ArmRule.TRIGGER_ARMED, ArmRule.TRIGGER_OFF_WIFI), events)
    }

    @Test
    fun armedFlag() {
        assertTrue(everything.armed)
        assertTrue(jobsOnly.armed)
        assertFalse(ArmRule.DISARMED.armed)
    }
}
