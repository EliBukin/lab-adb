package bukin.labadb.core

/** Which triggers should be armed right now. */
data class ArmPlan(
    /** The 15-minute periodic backup check. */
    val backupJob: Boolean,
    /** The job that runs whenever adb_wifi_enabled changes. */
    val settingJob: Boolean,
    /** Android's one-shot "Wi-Fi available" trigger. */
    val wifiTrigger: Boolean,
) {
    val armed: Boolean get() = backupJob || settingJob || wifiTrigger
}

object ArmRule {

    val DISARMED = ArmPlan(backupJob = false, settingJob = false, wifiTrigger = false)

    /**
     * Switch off or no lab subnet: disarm everything. Otherwise both jobs, plus the Wi-Fi trigger
     * only while no real Wi-Fi is connected (a local-only link doesn't count). Android fires a new
     * Wi-Fi trigger at once for a Wi-Fi that is already connected and then drops it, so it must be
     * armed for the NEXT join.
     */
    fun plan(enabled: Boolean, config: LabConfig?, networks: List<NetworkSnapshot>): ArmPlan {
        if (!enabled || config == null) return DISARMED
        val wifiConnected = networks.any { it.isRealWifi }
        return ArmPlan(backupJob = true, settingJob = true, wifiTrigger = !wifiConnected)
    }

    const val TRIGGER_ARMED = "Instant trigger armed"
    const val TRIGGER_OFF_WIFI = "Instant trigger off: Wi-Fi connected"

    /**
     * The event for the instant ("Wi-Fi joined") trigger, listed only when its armed state changes,
     * so the 15-minute checks don't repeat it. Disarming everything (switch off) is silent: the
     * switch logs its own event.
     */
    fun triggerEvent(wasArmed: Boolean, plan: ArmPlan): String? = when {
        plan.wifiTrigger == wasArmed -> null
        plan.wifiTrigger -> TRIGGER_ARMED
        plan.armed -> TRIGGER_OFF_WIFI
        else -> null
    }
}
