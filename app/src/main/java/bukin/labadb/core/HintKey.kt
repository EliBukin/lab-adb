package bukin.labadb.core

/**
 * The key shared with ANXEL for signing hints: 32 random bytes, kept on the phone as 64 hex
 * characters in a private file. The app only reads it; it never shows, logs or exports it.
 */
object HintKey {

    const val HEX_LENGTH = 64

    /**
     * Whitespace around the text is ignored (for example the newline `cat` leaves). Anything but
     * exactly 64 hex digits (ASCII 0-9, a-f, A-F) means "no key": returns null.
     *
     * Returns the 32 RAW bytes, which is what the HMAC uses, never the 64-character text.
     */
    fun parse(text: String): ByteArray? {
        val hex = text.trim()
        if (hex.length != HEX_LENGTH) return null
        val key = ByteArray(HEX_LENGTH / 2)
        for (i in key.indices) {
            val high = hexValue(hex[2 * i])
            val low = hexValue(hex[2 * i + 1])
            if (high < 0 || low < 0) {
                key.fill(0)
                return null
            }
            key[i] = ((high shl 4) or low).toByte()
        }
        return key
    }

    /** ASCII only: Character.digit would also accept other scripts' digits. */
    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}
