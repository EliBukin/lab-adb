package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionTest {

    private fun ip(text: String) = Ipv4.parse(text)!!
    private val config = LabConfig((Cidr.parse("192.0.2.0/24") as Parsed.Ok).value, null)

    private val lab = NetworkSnapshot(501L, isWifi = true, isVpn = false, listOf(ip("192.0.2.50")), listOf(ip("192.0.2.1")))
    private val otherWifi = NetworkSnapshot(502L, isWifi = true, isVpn = false, listOf(ip("198.51.100.7")), emptyList())
    private val cellular = NetworkSnapshot(503L, isWifi = false, isVpn = false, listOf(ip("192.0.2.60")), emptyList())
    private val vpnOverWifi = NetworkSnapshot(504L, isWifi = true, isVpn = true, listOf(ip("192.0.2.70")), emptyList())

    /** Every condition for a write holds. Each test breaks one. */
    private val ready = CheckInputs(
        enabled = true,
        config = config,
        permissionGranted = true,
        networks = listOf(lab),
        adbWifiValue = 0,
        failedHandle = null,
        manual = false,
    )

    private fun skipResult(inputs: CheckInputs): CheckResult {
        val decision = Decider.decide(inputs)
        assertTrue("expected a skip, got $decision", decision is Decision.Skip)
        return (decision as Decision.Skip).result
    }

    @Test
    fun writesWhenEverythingHolds() {
        assertEquals(Decision.Write(lab), Decider.decide(ready))
    }

    @Test
    fun switchOffSkipsEvenWhenAllElseHolds() {
        assertEquals(CheckResult.SWITCH_OFF, skipResult(ready.copy(enabled = false)))
    }

    @Test
    fun missingSubnetSkips() {
        assertEquals(CheckResult.NO_SUBNET, skipResult(ready.copy(config = null)))
    }

    @Test
    fun missingPermissionSkips() {
        assertEquals(CheckResult.NO_PERMISSION, skipResult(ready.copy(permissionGranted = false)))
    }

    @Test
    fun noWifiSkips() {
        assertEquals(CheckResult.NO_WIFI, skipResult(ready.copy(networks = emptyList())))
        assertEquals(CheckResult.NO_WIFI, skipResult(ready.copy(networks = listOf(cellular))))
        assertEquals(CheckResult.NO_WIFI, skipResult(ready.copy(networks = listOf(vpnOverWifi))))
    }

    @Test
    fun otherWifiSkipsAndReportsThatNetwork() {
        val decision = Decider.decide(ready.copy(networks = listOf(otherWifi, cellular)))
        assertEquals(Decision.Skip(CheckResult.NOT_LAB_NETWORK, otherWifi), decision)
    }

    @Test
    fun picksTheLabAmongSeveralNetworks() {
        assertEquals(Decision.Write(lab), Decider.decide(ready.copy(networks = listOf(cellular, otherWifi, lab))))
    }

    @Test
    fun alreadyOnSkips() {
        assertEquals(Decision.Skip(CheckResult.ALREADY_ON, lab), Decider.decide(ready.copy(adbWifiValue = 1)))
        assertEquals(CheckResult.ALREADY_ON, skipResult(ready.copy(adbWifiValue = 2)))
    }

    @Test
    fun alreadyOnWinsOverAnEarlierFailure() {
        assertEquals(CheckResult.ALREADY_ON, skipResult(ready.copy(adbWifiValue = 1, failedHandle = lab.handle)))
    }

    @Test
    fun unreadableSettingSkips() {
        assertEquals(CheckResult.CANNOT_READ_SETTING, skipResult(ready.copy(adbWifiValue = null)))
    }

    @Test
    fun noRetryOnTheConnectionWhereItFailed() {
        assertEquals(
            Decision.Skip(CheckResult.FAILED_ON_THIS_CONNECTION, lab),
            Decider.decide(ready.copy(failedHandle = lab.handle)),
        )
    }

    @Test
    fun newConnectionGetsANewTry() {
        assertEquals(Decision.Write(lab), Decider.decide(ready.copy(failedHandle = lab.handle + 1)))
    }

    @Test
    fun testNowRetriesOnceOnAFailedConnection() {
        assertEquals(Decision.Write(lab), Decider.decide(ready.copy(failedHandle = lab.handle, manual = true)))
    }

    @Test
    fun testNowDoesNotBypassAnyOtherRule() {
        val manual = ready.copy(manual = true)
        assertEquals(CheckResult.SWITCH_OFF, skipResult(manual.copy(enabled = false)))
        assertEquals(CheckResult.NO_PERMISSION, skipResult(manual.copy(permissionGranted = false)))
        assertEquals(CheckResult.NOT_LAB_NETWORK, skipResult(manual.copy(networks = listOf(otherWifi))))
        assertEquals(CheckResult.ALREADY_ON, skipResult(manual.copy(adbWifiValue = 1)))
    }

    @Test
    fun rulesAreCheckedInOrder() {
        val allWrong = CheckInputs(false, null, false, emptyList(), null, null, false)
        assertEquals(CheckResult.SWITCH_OFF, skipResult(allWrong))
        assertEquals(CheckResult.NO_SUBNET, skipResult(allWrong.copy(enabled = true)))
        assertEquals(CheckResult.NO_PERMISSION, skipResult(allWrong.copy(enabled = true, config = config)))
        assertEquals(
            CheckResult.NO_WIFI,
            skipResult(allWrong.copy(enabled = true, config = config, permissionGranted = true)),
        )
    }

    @Test
    fun resultAfterWrite() {
        assertEquals(CheckResult.SWITCHED_ON, Decider.resultAfterWrite(1))
        assertEquals(CheckResult.DID_NOT_STICK, Decider.resultAfterWrite(0))
        assertEquals(CheckResult.SWITCHED_ON_UNVERIFIED, Decider.resultAfterWrite(null))
    }

    @Test
    fun quietTriggersLogOnlyWrites() {
        assertNull(Decider.eventText(Trigger.APP_OPENED, CheckResult.ALREADY_ON))
        assertNull(Decider.eventText(Trigger.WIFI_RECHECK, CheckResult.NOT_LAB_NETWORK))
        assertEquals("App opened: switched on", Decider.eventText(Trigger.APP_OPENED, CheckResult.SWITCHED_ON))
    }

    @Test
    fun otherTriggersLogEverything() {
        assertEquals("Joined Wi-Fi: switched on", Decider.eventText(Trigger.WIFI_JOINED, CheckResult.SWITCHED_ON))
        assertEquals(
            "Backup check: on lab Wi-Fi, already on",
            Decider.eventText(Trigger.BACKUP_CHECK, CheckResult.ALREADY_ON),
        )
        assertEquals("Test now: not the lab network", Decider.eventText(Trigger.TEST_NOW, CheckResult.NOT_LAB_NETWORK))
    }

    @Test
    fun eventTextsHoldNoAddresses() {
        val digits = Regex("[0-9]")
        for (trigger in Trigger.entries) assertFalse(trigger.label, digits.containsMatchIn(trigger.label))
        for (result in CheckResult.entries) assertFalse(result.text, digits.containsMatchIn(result.text))
    }
}
