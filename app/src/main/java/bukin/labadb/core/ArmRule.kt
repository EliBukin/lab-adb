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
     * only while no Wi-Fi is connected. Android fires a new Wi-Fi trigger at once for a Wi-Fi
     * that is already connected and then drops it, so it must be armed for the NEXT join.
     */
    fun plan(enabled: Boolean, config: LabConfig?, networks: List<NetworkSnapshot>): ArmPlan {
        if (!enabled || config == null) return DISARMED
        val wifiConnected = networks.any { it.isWifi && !it.isVpn }
        return ArmPlan(backupJob = true, settingJob = true, wifiTrigger = !wifiConnected)
    }
}
