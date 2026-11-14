package bukin.labadb.core

/**
 * Where hints go: ANXEL's IPv4 address and UDP port, typed in the app as `a.b.c.d:port`.
 * An address literal only, never a host name, so nothing is ever looked up. Empty by default.
 */
data class HintTarget(val address: Int, val port: Int) {

    override fun toString(): String = "${Ipv4.format(address)}:$port"

    companion object {
        const val EXAMPLE = "192.0.2.10:47113"
        const val FORM_HINT = "Use the form a.b.c.d:port, for example $EXAMPLE."

        /**
         * Checks the "ANXEL address" field. Spaces around the text are ignored. Empty means
         * "not set" (Ok with null): then the app sends nothing.
         *
         * Only a single unicast address counts: not 0.x.x.x, not loopback (127.x.x.x), not
         * multicast or broadcast (224.0.0.0 and up), so a hint can never go to the whole Wi-Fi.
         */
        fun parse(text: String): Parsed<HintTarget?> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Parsed.Ok(null)
            val parts = trimmed.split(':')
            if (parts.size != 2) return Parsed.Invalid(FORM_HINT)
            val address = Ipv4.parse(parts[0])
                ?: return Parsed.Invalid("The address must be 4 numbers from 0 to 255, no host names. $FORM_HINT")
            if (!isSingleHost(address)) {
                return Parsed.Invalid("Use one computer's address: not 0.x, 127.x, multicast or broadcast.")
            }
            val port = parsePort(parts[1]) ?: return Parsed.Invalid("The port must be a number from 1 to 65535.")
            return Parsed.Ok(HintTarget(address, port))
        }

        private fun isSingleHost(address: Int): Boolean {
            val first = address ushr 24
            return first != 0 && first != 127 && first < 224
        }

        private fun parsePort(text: String): Int? {
            if (text.isEmpty() || text.length > 5) return null
            if (text.any { it !in '0'..'9' }) return null
            if (text[0] == '0') return null
            val value = text.toInt()
            return if (value in 1..65535) value else null
        }
    }
}
