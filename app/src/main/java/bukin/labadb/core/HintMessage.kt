package bukin.labadb.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The "I'm here" hint for ANXEL (protocol v1): one UDP datagram holding one line of UTF-8 JSON,
 * at most [MAX_BYTES] bytes:
 *
 * `{"app":"labadb","v":1,"model":"…","port":…,"ip":"…","t":…,"mac":"…"}`
 *
 * `mac` is HMAC-SHA256 over the ASCII text `labadb|1|<model>|<port>|<t>`, as 64 lowercase hex
 * digits. Every packet is built with a fresh `t` (milliseconds) and so a fresh `mac`: ANXEL drops
 * a byte-identical packet as a replay.
 */
object HintMessage {

    const val APP = "labadb"
    const val VERSION = 1
    const val MAX_BYTES = 512
    const val MAX_MODEL_LENGTH = 64

    /** Port 0 tells ANXEL "port unknown": it then checks only the sender's address. */
    const val UNKNOWN_PORT = 0

    /** The ports ANXEL accepts besides 0. adbd never gets one below 1024. */
    val VALID_PORTS = 1024..65535

    private const val HMAC = "HmacSHA256"

    /**
     * The phone model (Build.MODEL) cut down to printable ASCII without `"`, `\` and `|`, at most
     * [MAX_MODEL_LENGTH] characters. So the JSON needs no escaping and the signed text can't be
     * read two ways. ANXEL compares it with the model it expects.
     */
    fun cleanModel(raw: String?): String {
        val clean = raw.orEmpty()
            .filter { it in ' '..'~' && it != '"' && it != '\\' && it != '|' }
            .trim()
            .take(MAX_MODEL_LENGTH)
            .trim()
        return clean.ifEmpty { "unknown" }
    }

    /** The port to send: a port ANXEL accepts, or 0 ("unknown") for none or anything else. */
    fun portOrUnknown(port: Int?): Int = if (port != null && port in VALID_PORTS) port else UNKNOWN_PORT

    /** The exact text the mac signs: decimal numbers, no spaces. */
    fun signedText(model: String, port: Int, timeMillis: Long): String = "$APP|$VERSION|$model|$port|$timeMillis"

    /** HMAC-SHA256 of [text] (ASCII) with the 32 raw key bytes, as lowercase hex. */
    fun mac(key: ByteArray, text: String): String {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        return toHex(mac.doFinal(text.toByteArray(Charsets.US_ASCII)))
    }

    /** Lowercase hex by hand: String.format would follow the phone's locale. */
    private fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    /** The JSON line, in the field order of the protocol. [model] must already be clean. */
    fun payload(model: String, port: Int, ip: Int, timeMillis: Long, mac: String): String =
        "{\"app\":\"$APP\",\"v\":$VERSION,\"model\":\"$model\",\"port\":$port," +
            "\"ip\":\"${Ipv4.format(ip)}\",\"t\":$timeMillis,\"mac\":\"$mac\"}"

    /**
     * One signed datagram for these values. Call it again for every packet, with a new
     * [timeMillis]. [port] goes through [portOrUnknown], the model through [cleanModel].
     */
    fun build(key: ByteArray, rawModel: String?, port: Int?, ip: Int, timeMillis: Long): ByteArray {
        val model = cleanModel(rawModel)
        val sentPort = portOrUnknown(port)
        val mac = mac(key, signedText(model, sentPort, timeMillis))
        val bytes = payload(model, sentPort, ip, timeMillis, mac).toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_BYTES) { "hint too long" }
        return bytes
    }
}
