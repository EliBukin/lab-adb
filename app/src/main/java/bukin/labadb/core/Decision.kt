package bukin.labadb.core

/**
 * What started a check. A quiet trigger is listed only when the check wrote the setting;
 * [unlistedResults] are never listed for that trigger, because an earlier event already said it.
 */
enum class Trigger(
    val label: String,
    val quiet: Boolean = false,
    val unlistedResults: Set<CheckResult> = emptySet(),
) {
    WIFI_JOINED("Joined Wi-Fi"),
    BACKUP_CHECK("Backup check"),

    /**
     * adb_wifi_enabled changed and now reads anything but 0, for example our own write of 1.
     * [Decider.effectiveTrigger] turns it into [SETTING_WENT_OFF] when it reads 0.
     */
    SETTING_CHANGED("Setting changed", quiet = true),

    /** Android (or the owner) switched Wireless debugging off. A failed switch-on was already listed. */
    SETTING_WENT_OFF("Wireless debugging went off", unlistedResults = setOf(CheckResult.FAILED_ON_THIS_CONNECTION)),
    RESTART("Restart"),
    APP_UPDATED("App updated"),
    APP_OPENED("App opened", quiet = true),
    SWITCHED_ON("Switch turned on"),
    SETTINGS_SAVED("Settings saved"),
    TEST_NOW("Test now"),
}

/** How a check ended. [wrote] is true when the app wrote the setting during the check. */
enum class CheckResult(val text: String, val wrote: Boolean = false) {
    SWITCHED_ON("switched on", wrote = true),
    DID_NOT_STICK("switch-on didn't stick: approve this access point once (\"Always allow\")", wrote = true),
    SWITCHED_ON_UNVERIFIED("switched on, but couldn't re-read it", wrote = true),
    ALREADY_ON("on lab Wi-Fi, already on"),
    FAILED_ON_THIS_CONNECTION("skipped: switch-on didn't stick on this connection (Test now retries)"),
    NOT_LAB_NETWORK("not the lab network"),
    NO_WIFI("no Wi-Fi"),
    NO_PERMISSION("permission missing"),
    NO_SUBNET("lab subnet not set"),
    SWITCH_OFF("switch is off"),
    CANNOT_READ_SETTING("can't read the Wireless debugging setting"),
}

/** Everything one check looks at, gathered by the Android side. */
data class CheckInputs(
    val enabled: Boolean,
    /** Null when the saved lab settings are missing or invalid. */
    val config: LabConfig?,
    val permissionGranted: Boolean,
    /** The connected networks to consider. Only real Wi-Fi counts ([NetworkSnapshot.isRealWifi]). */
    val networks: List<NetworkSnapshot>,
    /** The current adb_wifi_enabled value, or null when it could not be read. */
    val adbWifiValue: Int?,
    /** The connection where an earlier switch-on didn't stick, if any. */
    val failedHandle: Long?,
    /** True for "Test now": the user asked, so one more try on a failed connection is allowed. */
    val manual: Boolean,
)

sealed interface Decision {
    /** Write 1 while connected to [network], the lab network. */
    data class Write(val network: NetworkSnapshot) : Decision
    data class Skip(val result: CheckResult) : Decision
}

object Decider {

    /**
     * Write only when ALL hold: the switch is on, a lab subnet is saved, the permission is granted,
     * a connected Wi-Fi network is the lab network, the setting reads 0, and no earlier switch-on
     * failed on this same connection (unless the user pressed Test now).
     */
    fun decide(inputs: CheckInputs): Decision {
        if (!inputs.enabled) return Decision.Skip(CheckResult.SWITCH_OFF)
        val config = inputs.config ?: return Decision.Skip(CheckResult.NO_SUBNET)
        if (!inputs.permissionGranted) return Decision.Skip(CheckResult.NO_PERMISSION)
        val wifi = inputs.networks.filter { it.isRealWifi }
        if (wifi.isEmpty()) return Decision.Skip(CheckResult.NO_WIFI)
        val lab = wifi.firstOrNull { LabMatcher.match(config, it) == MatchResult.MATCH }
            ?: return Decision.Skip(CheckResult.NOT_LAB_NETWORK)
        val value = inputs.adbWifiValue ?: return Decision.Skip(CheckResult.CANNOT_READ_SETTING)
        if (value != 0) return Decision.Skip(CheckResult.ALREADY_ON)
        if (!inputs.manual && inputs.failedHandle == lab.handle) {
            return Decision.Skip(CheckResult.FAILED_ON_THIS_CONNECTION)
        }
        return Decision.Write(lab)
    }

    /**
     * The result once the setting is re-read a few seconds after writing 1. Android puts it back
     * to 0 at once when it refuses, for example on an access point not yet approved.
     */
    fun resultAfterWrite(valueAfter: Int?): CheckResult = when (valueAfter) {
        null -> CheckResult.SWITCHED_ON_UNVERIFIED
        0 -> CheckResult.DID_NOT_STICK
        else -> CheckResult.SWITCHED_ON
    }

    /** A setting change that reads 0 means Wireless debugging just went off. */
    fun effectiveTrigger(trigger: Trigger, adbWifiValue: Int?): Trigger =
        if (trigger == Trigger.SETTING_CHANGED && adbWifiValue == 0) Trigger.SETTING_WENT_OFF else trigger

    /** The event text for a check, or null when it should not be listed (see [Trigger]). */
    fun eventText(trigger: Trigger, result: CheckResult): String? = when {
        trigger.quiet && !result.wrote -> null
        result in trigger.unlistedResults -> null
        else -> "${trigger.label}: ${result.text}"
    }
}
