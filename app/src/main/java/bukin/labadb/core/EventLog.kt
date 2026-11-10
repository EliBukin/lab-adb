package bukin.labadb.core

/** One line in the event list. [count] > 1 when the same message repeated back to back. */
data class Event(val timeMillis: Long, val message: String, val count: Int = 1)

/**
 * The last [capacity] events, oldest first. A message equal to the newest one is merged into it
 * (newer time, count + 1), so a backup check repeating every 15 minutes doesn't push older events out.
 */
class EventLog(events: List<Event> = emptyList(), val capacity: Int = DEFAULT_CAPACITY) {

    private val items = ArrayDeque(events.takeLast(capacity))

    val size: Int get() = items.size

    fun add(timeMillis: Long, message: String) {
        val clean = clean(message)
        val newest = items.lastOrNull()
        if (newest != null && newest.message == clean) {
            items[items.lastIndex] = Event(timeMillis, clean, newest.count + 1)
            return
        }
        items.addLast(Event(timeMillis, clean))
        while (items.size > capacity) items.removeFirst()
    }

    fun newestFirst(): List<Event> = items.reversed()

    /** One event per line: time, count and message, separated by tabs. */
    fun encode(): String = items.joinToString("\n") { "${it.timeMillis}\t${it.count}\t${it.message}" }

    companion object {
        const val DEFAULT_CAPACITY = 20

        /** Reads what [encode] wrote. Broken lines are skipped, never fatal. */
        fun decode(text: String, capacity: Int = DEFAULT_CAPACITY): EventLog {
            val events = text.lineSequence().mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val time = parts[0].toLongOrNull() ?: return@mapNotNull null
                val count = parts[1].toIntOrNull()?.takeIf { it >= 1 } ?: return@mapNotNull null
                Event(time, parts[2], count)
            }.toList()
            return EventLog(events, capacity)
        }

        /** Tabs and line breaks would break the stored format. */
        private fun clean(message: String): String = message.replace(Regex("[\\t\\r\\n]+"), " ").trim()
    }
}
