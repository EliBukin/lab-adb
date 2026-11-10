package bukin.labadb.core

/** The lab network as the user set it in the app. Never hard-coded. */
data class LabConfig(val subnet: Cidr, val gateway: Int?) {

    /** The text saved for the gateway: empty when none is set. */
    val gatewayText: String get() = gateway?.let(Ipv4::format) ?: ""

    companion object {
        /**
         * Checks the two fields from the screen. The gateway is optional: empty means "any".
         * Each error message belongs to its own field, so the screen can show both at once.
         */
        fun parse(subnetText: String, gatewayText: String): ConfigCheck {
            val subnet = Cidr.parse(subnetText)
            val gatewayTrimmed = gatewayText.trim()
            val gateway: Parsed<Int?> = if (gatewayTrimmed.isEmpty()) {
                Parsed.Ok(null)
            } else {
                Ipv4.parse(gatewayTrimmed)?.let { Parsed.Ok(it) }
                    ?: Parsed.Invalid("The gateway must be 4 numbers from 0 to 255, for example 192.0.2.1, or empty.")
            }
            return if (subnet is Parsed.Ok && gateway is Parsed.Ok) {
                ConfigCheck.Valid(LabConfig(subnet.value, gateway.value))
            } else {
                ConfigCheck.Invalid(
                    subnetError = (subnet as? Parsed.Invalid)?.message,
                    gatewayError = (gateway as? Parsed.Invalid)?.message,
                )
            }
        }
    }
}

sealed interface ConfigCheck {
    data class Valid(val config: LabConfig) : ConfigCheck
    data class Invalid(val subnetError: String?, val gatewayError: String?) : ConfigCheck
}
