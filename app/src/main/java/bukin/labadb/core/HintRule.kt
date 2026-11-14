package bukin.labadb.core

/**
 * Why a hint is sent.
 * @param offsetsMs when each packet goes out, from the start. Each packet gets its own t and mac.
 * @param waitsForPort true when the one packet waits (a few seconds at most) for the port first.
 */
enum class HintKind(val offsetsMs: List<Long>, val waitsForPort: Boolean) {
    /** Right after the app's own confirmed switch-on: 3 packets, because UDP can drop one. */
    BURST(listOf(0L, 3_000L, 8_000L), waitsForPort = false),

    /** At a 15-minute backup check on the lab Wi-Fi with Wireless debugging on. */
    HEARTBEAT(listOf(0L), waitsForPort = true),

    /** The Send now button. */
    MANUAL(listOf(0L), waitsForPort = true),
}

/** What stops a hint, checked in this order. */
enum class HintBlock(val text: String) {
    SWITCH_OFF("switch is off"),
    NOT_ON_LAB("not on the lab Wi-Fi"),
    DEBUGGING_OFF("Wireless debugging is off"),
    NO_KEY("no key"),
    NO_ADDRESS("no ANXEL address"),
}

/** Everything one hint packet depends on, gathered fresh before each packet. */
data class HintInputs(
    val enabled: Boolean,
    /** A connected real Wi-Fi is the lab network (and, mid-burst, still the same connection). */
    val onLab: Boolean,
    val debuggingOn: Boolean,
    val keySet: Boolean,
    val targetSet: Boolean,
)

object HintRule {

    const val SEND_FAILED = "Hint send failed"

    /** Send only when ALL hold: switch on, lab Wi-Fi, Wireless debugging on, key set, address set. */
    fun block(inputs: HintInputs): HintBlock? = when {
        !inputs.enabled -> HintBlock.SWITCH_OFF
        !inputs.onLab -> HintBlock.NOT_ON_LAB
        !inputs.debuggingOn -> HintBlock.DEBUGGING_OFF
        !inputs.keySet -> HintBlock.NO_KEY
        !inputs.targetSet -> HintBlock.NO_ADDRESS
        else -> null
    }

    /**
     * What a check leads to: a burst after the app's own confirmed switch-on (whichever trigger
     * did it), a heartbeat when a backup check finds Wireless debugging already on at the lab.
     * Nothing else sends on its own.
     */
    fun afterCheck(trigger: Trigger, result: CheckResult): HintKind? = when {
        result == CheckResult.SWITCHED_ON -> HintKind.BURST
        result == CheckResult.ALREADY_ON && trigger == Trigger.BACKUP_CHECK -> HintKind.HEARTBEAT
        else -> null
    }

    fun sentText(port: Int): String =
        if (port == HintMessage.UNKNOWN_PORT) "Hint sent (port unknown)" else "Hint sent (port $port)"

    fun notSentText(block: HintBlock): String = "Hint not sent: ${block.text}"

    /**
     * How one hint run ended, as its event text, or null when it was stopped before it sent or
     * found anything to say (a newer run replaced it). [lastPort] is the port in the last packet
     * sent. Never an address: the event list stores no IPs.
     */
    fun outcome(sent: Int, lastPort: Int, failed: Boolean, block: HintBlock?): String? = when {
        failed -> SEND_FAILED
        sent > 0 -> sentText(lastPort)
        block != null -> notSentText(block)
        else -> null
    }

    /**
     * The event to list, if any: one per burst and per Send now. A heartbeat is listed only
     * when its outcome differs from the last one, so the 15-minute heartbeats don't push other
     * events out of the list (they would alternate with the backup check's own event).
     */
    fun eventFor(kind: HintKind, outcome: String, lastOutcome: String?): String? =
        if (kind == HintKind.HEARTBEAT && outcome == lastOutcome) null else outcome
}
