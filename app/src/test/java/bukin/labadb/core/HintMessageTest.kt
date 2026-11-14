package bukin.labadb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HintMessageTest {

    /** SYS ADMIN's public test key (protocol v1): the 32 raw bytes 00 01 02 … 1f. */
    private val key = HintKey.parse("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")!!
    private val phone = Ipv4.parse("192.0.2.80")!!
    private val t = 1763150000000L

    @Test
    fun sysAdminVectorWithAPort() {
        val text = "labadb|1|SM-A556E|43999|1763150000000"
        assertEquals(text, HintMessage.signedText("SM-A556E", 43999, t))
        assertEquals("7e61778202541a453a3f66583129d7ff3ff7aad3b05ab05883542787608a51bf", HintMessage.mac(key, text))
    }

    @Test
    fun sysAdminVectorWithPortUnknown() {
        val text = "labadb|1|SM-A556E|0|1763150000000"
        assertEquals(text, HintMessage.signedText("SM-A556E", 0, t))
        assertEquals("a90af48c28e4c24f3d40e2e009efc8f2fbd8ab0496ac3f1ec07478b598baa327", HintMessage.mac(key, text))
    }

    @Test
    fun theKeyIsTheRawBytesNotTheHexText() {
        val hexTextAsKey = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".toByteArray(Charsets.US_ASCII)
        val text = "labadb|1|SM-A556E|43999|1763150000000"
        assertNotEquals(HintMessage.mac(key, text), HintMessage.mac(hexTextAsKey, text))
    }

    @Test
    fun buildsTheExactJsonLine() {
        val expected = "{\"app\":\"labadb\",\"v\":1,\"model\":\"SM-A556E\",\"port\":43999,\"ip\":\"192.0.2.80\"," +
            "\"t\":1763150000000,\"mac\":\"7e61778202541a453a3f66583129d7ff3ff7aad3b05ab05883542787608a51bf\"}"
        assertEquals(expected, String(HintMessage.build(key, "SM-A556E", 43999, phone, t), Charsets.UTF_8))
    }

    @Test
    fun portUnknownIsSentAsZeroAndSignedAsZero() {
        val expected = "{\"app\":\"labadb\",\"v\":1,\"model\":\"SM-A556E\",\"port\":0,\"ip\":\"192.0.2.80\"," +
            "\"t\":1763150000000,\"mac\":\"a90af48c28e4c24f3d40e2e009efc8f2fbd8ab0496ac3f1ec07478b598baa327\"}"
        assertEquals(expected, String(HintMessage.build(key, "SM-A556E", null, phone, t), Charsets.UTF_8))
        assertEquals(expected, String(HintMessage.build(key, "SM-A556E", 0, phone, t), Charsets.UTF_8))
    }

    @Test
    fun everyPacketHasAFreshTimeAndMac() {
        val first = String(HintMessage.build(key, "SM-A556E", 43999, phone, t), Charsets.UTF_8)
        val second = String(HintMessage.build(key, "SM-A556E", 43999, phone, t + 3_000), Charsets.UTF_8)
        assertNotEquals(first, second)
        assertNotEquals(macOf(first), macOf(second))
        assertTrue(second.contains("\"t\":1763150003000,"))
    }

    @Test
    fun onlyAcceptedPortsAreSent() {
        assertEquals(0, HintMessage.portOrUnknown(null))
        assertEquals(0, HintMessage.portOrUnknown(0))
        assertEquals(0, HintMessage.portOrUnknown(80))
        assertEquals(0, HintMessage.portOrUnknown(1023))
        assertEquals(1024, HintMessage.portOrUnknown(1024))
        assertEquals(43999, HintMessage.portOrUnknown(43999))
        assertEquals(65535, HintMessage.portOrUnknown(65535))
        assertEquals(0, HintMessage.portOrUnknown(65536))
        assertEquals(0, HintMessage.portOrUnknown(-1))
    }

    @Test
    fun theModelIsCleanedSoTheJsonNeedsNoEscaping() {
        assertEquals("SM-A556E", HintMessage.cleanModel("SM-A556E"))
        assertEquals("Pixel 9 Pro", HintMessage.cleanModel(" Pixel 9 Pro "))
        assertEquals("ab", HintMessage.cleanModel("a\"b"))
        assertEquals("ab", HintMessage.cleanModel("a\\b"))
        assertEquals("ab", HintMessage.cleanModel("a|b"))
        assertEquals("ab", HintMessage.cleanModel("a\nb\t"))
        assertEquals("Galaxy", HintMessage.cleanModel("Galaxy ✓"))
        assertEquals("unknown", HintMessage.cleanModel(""))
        assertEquals("unknown", HintMessage.cleanModel(null))
        assertEquals("unknown", HintMessage.cleanModel("\"|\\"))
        assertEquals(HintMessage.MAX_MODEL_LENGTH, HintMessage.cleanModel("x".repeat(500)).length)
    }

    @Test
    fun aLongModelStillFitsInOnePacket() {
        val bytes = HintMessage.build(key, "M".repeat(500), 65535, Ipv4.parse("203.0.113.255")!!, Long.MAX_VALUE)
        assertTrue(bytes.size <= HintMessage.MAX_BYTES)
    }

    @Test
    fun theSignedTextUsesTheCleanedModel() {
        val json = String(HintMessage.build(key, "SM-A556E\n", 43999, phone, t), Charsets.UTF_8)
        assertEquals("7e61778202541a453a3f66583129d7ff3ff7aad3b05ab05883542787608a51bf", macOf(json))
    }

    private fun macOf(json: String): String = json.substringAfter("\"mac\":\"").substringBefore('"')
}
