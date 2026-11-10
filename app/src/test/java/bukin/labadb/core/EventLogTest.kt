package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EventLogTest {

    @Test
    fun keepsOnlyTheLastTwenty() {
        val log = EventLog()
        for (i in 1..25) log.add(i.toLong(), "event $i")
        assertEquals(20, log.size)
        val newest = log.newestFirst()
        assertEquals("event 25", newest.first().message)
        assertEquals("event 6", newest.last().message)
    }

    @Test
    fun newestComesFirst() {
        val log = EventLog()
        log.add(1, "a")
        log.add(2, "b")
        assertEquals(listOf("b", "a"), log.newestFirst().map { it.message })
    }

    @Test
    fun repeatedMessageIsMergedIntoTheNewest() {
        val log = EventLog()
        log.add(1, "Backup check: no Wi-Fi")
        log.add(2, "Backup check: no Wi-Fi")
        log.add(3, "Backup check: no Wi-Fi")
        assertEquals(listOf(Event(3, "Backup check: no Wi-Fi", 3)), log.newestFirst())
    }

    @Test
    fun onlyBackToBackRepeatsAreMerged() {
        val log = EventLog()
        log.add(1, "a")
        log.add(2, "b")
        log.add(3, "a")
        assertEquals(listOf("a", "b", "a"), log.newestFirst().map { it.message })
    }

    @Test
    fun repeatsDoNotPushOlderEventsOut() {
        val log = EventLog()
        for (i in 1..19) log.add(i.toLong(), "event $i")
        for (i in 1..50) log.add(100L + i, "Backup check: on lab Wi-Fi, already on")
        assertEquals(20, log.size)
        assertEquals("event 1", log.newestFirst().last().message)
        assertEquals(50, log.newestFirst().first().count)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val log = EventLog()
        log.add(1_700_000_000_000, "Joined Wi-Fi: switched on")
        log.add(1_700_000_100_000, "Backup check: on lab Wi-Fi, already on")
        log.add(1_700_000_200_000, "Backup check: on lab Wi-Fi, already on")
        val decoded = EventLog.decode(log.encode())
        assertEquals(log.newestFirst(), decoded.newestFirst())
    }

    @Test
    fun decodeSkipsBrokenLines() {
        val text = "garbage\n12\t1\tgood\nx\t1\tbad time\n13\t0\tbad count\n14\t2\talso good\n\n"
        val decoded = EventLog.decode(text)
        assertEquals(listOf(Event(14, "also good", 2), Event(12, "good", 1)), decoded.newestFirst())
    }

    @Test
    fun decodeEmptyText() {
        assertEquals(0, EventLog.decode("").size)
    }

    @Test
    fun decodeKeepsTheNewestWhenOverCapacity() {
        val text = (1..30).joinToString("\n") { "$it\t1\tevent $it" }
        val decoded = EventLog.decode(text)
        assertEquals(20, decoded.size)
        assertEquals("event 11", decoded.newestFirst().last().message)
    }

    @Test
    fun tabsAndLineBreaksAreCleanedOut() {
        val log = EventLog()
        log.add(1, "two\tparts\nand a line")
        assertEquals("two parts and a line", log.newestFirst().single().message)
        assertEquals(1, EventLog.decode(log.encode()).size)
    }

    @Test
    fun smallerCapacity() {
        val log = EventLog(capacity = 3)
        for (i in 1..5) log.add(i.toLong(), "e$i")
        assertEquals(listOf("e5", "e4", "e3"), log.newestFirst().map { it.message })
    }
}
