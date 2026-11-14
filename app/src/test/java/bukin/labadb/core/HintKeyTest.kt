package bukin.labadb.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintKeyTest {

    /** SYS ADMIN's public test key (protocol v1). Never a real key. */
    private val testKeyHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val testKeyBytes = ByteArray(32) { it.toByte() }

    @Test
    fun parsesTheHexIntoTheRawBytes() {
        assertArrayEquals(testKeyBytes, HintKey.parse(testKeyHex))
    }

    @Test
    fun acceptsUpperCaseAndSurroundingWhitespace() {
        assertArrayEquals(testKeyBytes, HintKey.parse(testKeyHex.uppercase()))
        assertArrayEquals(testKeyBytes, HintKey.parse("$testKeyHex\n"))
        assertArrayEquals(testKeyBytes, HintKey.parse("  $testKeyHex \r\n"))
    }

    @Test
    fun anythingElseIsNoKey() {
        val bad = listOf(
            "",
            "\n",
            testKeyHex.dropLast(1),
            testKeyHex + "0",
            testKeyHex.replaceRange(10, 11, "g"),
            testKeyHex.replaceRange(30, 32, " 0"),
            testKeyHex.replaceRange(0, 1, "١"),
            testKeyHex.replaceRange(0, 2, "0x"),
            "0x$testKeyHex",
            testKeyHex.substring(0, 32),
        )
        for (text in bad) assertNull(text, HintKey.parse(text))
    }
}
