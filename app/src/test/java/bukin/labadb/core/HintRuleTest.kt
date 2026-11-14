package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintRuleTest {

    private val ready = HintInputs(enabled = true, onLab = true, debuggingOn = true, keySet = true, targetSet = true)

    @Test
    fun sendsOnlyWhenEverythingHolds() {
        assertNull(HintRule.block(ready))
        assertEquals(HintBlock.SWITCH_OFF, HintRule.block(ready.copy(enabled = false)))
        assertEquals(HintBlock.NOT_ON_LAB, HintRule.block(ready.copy(onLab = false)))
        assertEquals(HintBlock.DEBUGGING_OFF, HintRule.block(ready.copy(debuggingOn = false)))
        assertEquals(HintBlock.NO_KEY, HintRule.block(ready.copy(keySet = false)))
        assertEquals(HintBlock.NO_ADDRESS, HintRule.block(ready.copy(targetSet = false)))
    }

    @Test
    fun theFirstMissingConditionIsTheReason() {
        val nothing = HintInputs(enabled = false, onLab = false, debuggingOn = false, keySet = false, targetSet = false)
        assertEquals(HintBlock.SWITCH_OFF, HintRule.block(nothing))
        assertEquals(HintBlock.NOT_ON_LAB, HintRule.block(nothing.copy(enabled = true)))
        assertEquals(HintBlock.DEBUGGING_OFF, HintRule.block(nothing.copy(enabled = true, onLab = true)))
        assertEquals(HintBlock.NO_ADDRESS, HintRule.block(ready.copy(keySet = false, targetSet = false)))
    }

    @Test
    fun aConfirmedSwitchOnSendsABurstWhateverTheTrigger() {
        for (trigger in Trigger.entries) {
            assertEquals(trigger.name, HintKind.BURST, HintRule.afterCheck(trigger, CheckResult.SWITCHED_ON))
        }
    }

    @Test
    fun aBackupCheckThatFindsItOnSendsAHeartbeat() {
        assertEquals(HintKind.HEARTBEAT, HintRule.afterCheck(Trigger.BACKUP_CHECK, CheckResult.ALREADY_ON))
        for (trigger in Trigger.entries - Trigger.BACKUP_CHECK) {
            assertNull(trigger.name, HintRule.afterCheck(trigger, CheckResult.ALREADY_ON))
        }
    }

    @Test
    fun nothingElseSends() {
        val others = CheckResult.entries - CheckResult.SWITCHED_ON - CheckResult.ALREADY_ON
        for (trigger in Trigger.entries) {
            for (result in others) assertNull("$trigger $result", HintRule.afterCheck(trigger, result))
        }
    }

    @Test
    fun theBurstIsThreePacketsAt0And3And8Seconds() {
        assertEquals(listOf(0L, 3_000L, 8_000L), HintKind.BURST.offsetsMs)
        assertFalse(HintKind.BURST.waitsForPort)
        assertTrue(HintKind.BURST.offsetsMs.last() < 15_000L)
        for (kind in listOf(HintKind.HEARTBEAT, HintKind.MANUAL)) {
            assertEquals(listOf(0L), kind.offsetsMs)
            assertTrue(kind.waitsForPort)
        }
    }

    @Test
    fun outcomeTexts() {
        assertEquals("Hint sent (port 43999)", HintRule.outcome(sent = 3, lastPort = 43999, failed = false, block = null))
        assertEquals("Hint sent (port unknown)", HintRule.outcome(sent = 1, lastPort = 0, failed = false, block = null))
        assertEquals("Hint send failed", HintRule.outcome(sent = 1, lastPort = 43999, failed = true, block = null))
        assertEquals("Hint send failed", HintRule.outcome(sent = 0, lastPort = 0, failed = true, block = null))
        assertEquals("Hint not sent: no key", HintRule.outcome(sent = 0, lastPort = 0, failed = false, block = HintBlock.NO_KEY))
        assertEquals(
            "Hint not sent: not on the lab Wi-Fi",
            HintRule.outcome(sent = 0, lastPort = 0, failed = false, block = HintBlock.NOT_ON_LAB),
        )
        assertNull(HintRule.outcome(sent = 0, lastPort = 0, failed = false, block = null))
    }

    @Test
    fun aBurstStoppedHalfWayStillSaysWhatWentOut() {
        assertEquals("Hint sent (port 43999)", HintRule.outcome(sent = 2, lastPort = 43999, failed = false, block = HintBlock.NOT_ON_LAB))
    }

    @Test
    fun noEventTextHoldsAnAddress() {
        val texts = HintBlock.entries.map(HintRule::notSentText) +
            listOf(HintRule.sentText(0), HintRule.sentText(65535), HintRule.SEND_FAILED)
        for (text in texts) assertFalse(text, Regex("[0-9]+\\.[0-9]+").containsMatchIn(text))
    }

    @Test
    fun burstsAndSendNowAreAlwaysListed() {
        val text = HintRule.sentText(43999)
        assertEquals(text, HintRule.eventFor(HintKind.BURST, text, lastOutcome = text))
        assertEquals(text, HintRule.eventFor(HintKind.MANUAL, text, lastOutcome = text))
        assertEquals(text, HintRule.eventFor(HintKind.BURST, text, lastOutcome = null))
    }

    @Test
    fun withNoAddressTheHintIsOffAndOnlySendNowSaysSo() {
        val noAddress = HintRule.notSentText(HintBlock.NO_ADDRESS)
        assertEquals("Hint not sent: no ANXEL address", noAddress)
        assertNull(HintRule.eventFor(HintKind.BURST, noAddress, lastOutcome = null))
        assertNull(HintRule.eventFor(HintKind.HEARTBEAT, noAddress, lastOutcome = null))
        assertEquals(noAddress, HintRule.eventFor(HintKind.MANUAL, noAddress, lastOutcome = noAddress))
        val noKey = HintRule.notSentText(HintBlock.NO_KEY)
        assertEquals(noKey, HintRule.eventFor(HintKind.BURST, noKey, lastOutcome = noKey))
    }

    @Test
    fun aHeartbeatIsListedOnlyWhenItsOutcomeChanges() {
        val sent = HintRule.sentText(43999)
        assertNull(HintRule.eventFor(HintKind.HEARTBEAT, sent, lastOutcome = sent))
        assertEquals(sent, HintRule.eventFor(HintKind.HEARTBEAT, sent, lastOutcome = HintRule.sentText(0)))
        assertEquals(sent, HintRule.eventFor(HintKind.HEARTBEAT, sent, lastOutcome = null))
        assertEquals(HintRule.SEND_FAILED, HintRule.eventFor(HintKind.HEARTBEAT, HintRule.SEND_FAILED, lastOutcome = sent))
    }
}
