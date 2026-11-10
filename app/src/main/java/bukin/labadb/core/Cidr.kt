package bukin.labadb.core

/** The result of checking text the user typed: a value, or a message saying what to fix. */
sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Invalid(val message: String) : Parsed<Nothing>
}

/**
 * An IPv4 subnet such as 192.0.2.0/24. [network] always has its host bits cleared.
 * Prefixes run from /8 to /32 only, so a typo can never match every network.
 */
data class Cidr(val network: Int, val prefix: Int) {

    init {
        require(prefix in MIN_PREFIX..MAX_PREFIX) { "prefix out of range" }
        require((network and maskOf(prefix)) == network) { "host bits set" }
    }

    fun contains(address: Int): Boolean = (address and maskOf(prefix)) == network

    override fun toString(): String = "${Ipv4.format(network)}/$prefix"

    companion object {
        const val MIN_PREFIX = 8
        const val MAX_PREFIX = 32

        const val FORM_HINT = "Use the form a.b.c.d/nn, for example 192.0.2.0/24."

        /**
         * Parses "a.b.c.d/nn". Spaces around the whole text are ignored; spaces inside are not
         * allowed. Host bits are cleared, so "192.0.2.7/24" is saved as "192.0.2.0/24".
         */
        fun parse(text: String): Parsed<Cidr> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Parsed.Invalid("Type the lab subnet. $FORM_HINT")
            val parts = trimmed.split('/')
            if (parts.size != 2) return Parsed.Invalid(FORM_HINT)
            val address = Ipv4.parse(parts[0])
                ?: return Parsed.Invalid("The address must be 4 numbers from 0 to 255 with no leading zeros. $FORM_HINT")
            val prefix = parsePrefix(parts[1])
                ?: return Parsed.Invalid("The prefix must be a number from /$MIN_PREFIX to /$MAX_PREFIX.")
            return Parsed.Ok(Cidr(address and maskOf(prefix), prefix))
        }

        private fun parsePrefix(text: String): Int? {
            if (text.isEmpty() || text.length > 2) return null
            if (text.any { it !in '0'..'9' }) return null
            if (text.length > 1 && text[0] == '0') return null
            val value = text.toInt()
            return if (value in MIN_PREFIX..MAX_PREFIX) value else null
        }

        private fun maskOf(prefix: Int): Int = if (prefix == 0) 0 else -1 shl (32 - prefix)
    }
}
