package bukin.labadb.core

/**
 * IPv4 addresses as 32-bit Ints: a.b.c.d is (a shl 24) or (b shl 16) or (c shl 8) or d.
 * Pure Kotlin, no Android imports, so it is unit-tested on the JVM.
 */
object Ipv4 {

    /**
     * Parses a strict dotted quad such as "192.0.2.1": exactly four decimal numbers from 0 to 255,
     * no leading zeros ("01" could mean octal elsewhere), no signs and no spaces anywhere.
     * Callers trim the user's text first. Returns null when [text] is not a valid address.
     */
    fun parse(text: String): Int? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        var address = 0
        for (part in parts) {
            val octet = parseOctet(part) ?: return null
            address = (address shl 8) or octet
        }
        return address
    }

    /** The 4 raw bytes of an IPv4 address (network order), or null for any other length. */
    fun fromBytes(bytes: ByteArray): Int? {
        if (bytes.size != 4) return null
        return bytes.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
    }

    fun format(address: Int): String =
        listOf(24, 16, 8, 0).joinToString(".") { shift -> ((address ushr shift) and 0xFF).toString() }

    private fun parseOctet(part: String): Int? {
        if (part.isEmpty() || part.length > 3) return null
        if (part.any { it !in '0'..'9' }) return null
        if (part.length > 1 && part[0] == '0') return null
        val value = part.toInt()
        return if (value <= 255) value else null
    }
}
