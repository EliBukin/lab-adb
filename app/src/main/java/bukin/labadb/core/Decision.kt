package bukin.labadb.core

/** What started a check. Quiet triggers log only when the check actually wrote the setting. */
enum class Trigger(val label: String, val quiet: Boolean = false) {
    WIFI_JOINED("Joined Wi-Fi"),

    /** Android re-sent "Wi-Fi available" for a connection already handled (it does on re-arming). */
    WIFI_RECHECK("Wi-Fi re-check", quiet = true),
    BACKUP_CHECK("Backup check"),
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
    /** The connected networks to consider. Non-Wi-Fi entries are ignored. */
    val networks: List<NetworkSnapshot>,
    /** The current adb_wifi_enabled value, or null when it could not be read. */
    val adbWifiValue: Int?,
    /** The connection where an earlier switch-on didn't stick, if any. */
    val failedHandle: Long?,
    /** True for "Test now": the user asked, so one more try on a failed connection is allowed. */
    val manual: Boolean,
)

sealed interface Decision {
    /** The Wi-Fi network the check looked at: the lab network if found, else the first Wi-Fi. */
    val network: NetworkSnapshot?

    data class Write(override val network: NetworkSnapshot) : Decision
    data class Skip(val result: CheckResult, override val network: NetworkSnapshot? = null) : Decision
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
        val wifi = inputs.networks.filter { it.isWifi && !it.isVpn }
        if (wifi.isEmpty()) return Decision.Skip(CheckResult.NO_WIFI)
        val lab = wifi.firstOrNull { LabMatcher.match(config, it) == MatchResult.MATCH }
            ?: return Decision.Skip(CheckResult.NOT_LAB_NETWORK, wifi.first())
        val value = inputs.adbWifiValue ?: return Decision.Skip(CheckResult.CANNOT_READ_SETTING, lab)
        if (value != 0) return Decision.Skip(CheckResult.ALREADY_ON, lab)
        if (!inputs.manual && inputs.failedHandle == lab.handle) {
            return Decision.Skip(CheckResult.FAILED_ON_THIS_CONNECTION, lab)
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

    /** The event text for a check, or null when it should not be logged (quiet trigger, no write). */
    fun eventText(trigger: Trigger, result: CheckResult): String? =
        if (trigger.quiet && !result.wrote) null else "${trigger.label}: ${result.text}"
}
