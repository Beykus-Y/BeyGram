package com.beykus.beygram.media.bgwave

import java.util.Base64

/**
 * BG Wave v1 signaling envelope (issue #799, contract shared with BG-Media #67).
 *
 * The packing format below is provisional: the issue asks to pick the final compact
 * format after measuring real SDP/ICE sizes. Only [WaveCodec] knows the layout, so the
 * format can change without touching callers.
 *
 * Payload (ASCII, before base64url):
 * `v=1;t=offer;s=<32 hex sid>;i=<iat>;e=<exp>;c=<caps,comma>;n=<32 hex nonce>;g=<base64url signal>`
 */
object WaveProtocol {
    const val VERSION = 1
    const val SID_HEX_LENGTH = 32
    const val NONCE_HEX_LENGTH = 32
    const val MAX_TTL_SECONDS = 600L
    const val MAX_CLOCK_SKEW_SECONDS = 300L
    const val MAX_SIGNAL_BYTES = 1024
    const val MAX_PAYLOAD_BYTES = 1536
    /** Provisional. The real Telegram text-url limit must be measured in E0. */
    const val MAX_LINK_CHARS = 2048
    const val MAX_CAPS = 8
    const val INTERNAL_PREFIX = "bg://wave/v1/"
    const val TEXT_LINK_PREFIX = "https://beygram.beykus.fun/#bgwave-v1."
}

enum class WaveMessageType(val wire: String) {
    OFFER("offer"),
    ANSWER("answer"),
    CANCEL("cancel"),
    END("end");

    companion object {
        fun fromWire(value: String): WaveMessageType? = values().firstOrNull { it.wire == value }
    }
}

data class WaveEnvelope(
    val type: WaveMessageType,
    val sidHex: String,
    val nonceHex: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val caps: List<String>,
    val signal: String,
)

enum class WaveRejectReason {
    NOT_WAVE,
    TOO_LARGE,
    MALFORMED,
    UNSUPPORTED_VERSION,
    BAD_FIELD,
    TTL_TOO_LONG,
    NOT_YET_VALID,
    EXPIRED,
}

sealed interface WaveDecodeResult {
    data class Ok(val envelope: WaveEnvelope) : WaveDecodeResult
    data class Rejected(val reason: WaveRejectReason) : WaveDecodeResult
}

object WaveCodec {
    private val hexRegex = Regex("[0-9a-f]+")
    private val capRegex = Regex("[a-z0-9.-]{1,16}")
    private val digitsRegex = Regex("[0-9]{1,12}")
    private val base64UrlRegex = Regex("[A-Za-z0-9_-]*")
    private val requiredKeys = setOf("v", "t", "s", "i", "e", "c", "n", "g")

    /** Returns the reason a field is out of range, or null when the envelope is well formed. */
    fun validateFields(envelope: WaveEnvelope): WaveRejectReason? {
        if (envelope.sidHex.length != WaveProtocol.SID_HEX_LENGTH || !hexRegex.matches(envelope.sidHex)) {
            return WaveRejectReason.BAD_FIELD
        }
        if (envelope.nonceHex.length != WaveProtocol.NONCE_HEX_LENGTH || !hexRegex.matches(envelope.nonceHex)) {
            return WaveRejectReason.BAD_FIELD
        }
        if (envelope.caps.size > WaveProtocol.MAX_CAPS || envelope.caps.any { !capRegex.matches(it) }) {
            return WaveRejectReason.BAD_FIELD
        }
        if (envelope.issuedAt < 0L || envelope.expiresAt <= envelope.issuedAt) {
            return WaveRejectReason.BAD_FIELD
        }
        if (envelope.expiresAt - envelope.issuedAt > WaveProtocol.MAX_TTL_SECONDS) {
            return WaveRejectReason.TTL_TOO_LONG
        }
        if (envelope.signal.length > WaveProtocol.MAX_SIGNAL_BYTES) return WaveRejectReason.TOO_LARGE
        if (envelope.signal.any { !isWireTextChar(it.code) }) return WaveRejectReason.BAD_FIELD
        return null
    }

    /** Encodes the payload string. Throws [IllegalArgumentException] for an invalid envelope. */
    fun encodePayload(envelope: WaveEnvelope): String {
        val reason = validateFields(envelope)
        require(reason == null) { "invalid wave envelope: $reason" }
        val signalB64 = base64Url(envelope.signal.toByteArray(Charsets.US_ASCII))
        val payload = "v=${WaveProtocol.VERSION};" +
            "t=${envelope.type.wire};" +
            "s=${envelope.sidHex};" +
            "i=${envelope.issuedAt};" +
            "e=${envelope.expiresAt};" +
            "c=${envelope.caps.joinToString(",")};" +
            "n=${envelope.nonceHex};" +
            "g=$signalB64"
        require(payload.length <= WaveProtocol.MAX_PAYLOAD_BYTES) { "wave payload too large" }
        return payload
    }

    fun encodeInternal(envelope: WaveEnvelope): String =
        WaveProtocol.INTERNAL_PREFIX + base64Url(encodePayload(envelope).toByteArray(Charsets.US_ASCII))

    fun encodeTextLink(envelope: WaveEnvelope): String =
        WaveProtocol.TEXT_LINK_PREFIX + base64Url(encodePayload(envelope).toByteArray(Charsets.US_ASCII))

    /**
     * Decodes either the internal `bg://wave/v1/` form or the `https://beygram.beykus.fun/#bgwave-v1.`
     * text link. Never throws: any problem is reported as a [WaveDecodeResult.Rejected] reason.
     */
    fun decode(rawText: String, nowSeconds: Long): WaveDecodeResult {
        val text = rawText.trim()
        val body = when {
            text.startsWith(WaveProtocol.INTERNAL_PREFIX) -> text.substring(WaveProtocol.INTERNAL_PREFIX.length)
            text.startsWith(WaveProtocol.TEXT_LINK_PREFIX) -> text.substring(WaveProtocol.TEXT_LINK_PREFIX.length)
            else -> return WaveDecodeResult.Rejected(WaveRejectReason.NOT_WAVE)
        }
        if (text.length > WaveProtocol.MAX_LINK_CHARS) return WaveDecodeResult.Rejected(WaveRejectReason.TOO_LARGE)
        if (!base64UrlRegex.matches(body)) return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        val bytes = decodeBase64Url(body) ?: return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        if (bytes.size > WaveProtocol.MAX_PAYLOAD_BYTES) return WaveDecodeResult.Rejected(WaveRejectReason.TOO_LARGE)
        if (bytes.any { (it.toInt() and 0xff) !in 0x20..0x7e }) {
            return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        }

        val fields = HashMap<String, String>()
        for (part in String(bytes, Charsets.US_ASCII).split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
            if (fields.put(part.substring(0, eq), part.substring(eq + 1)) != null) {
                return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
            }
        }

        // Check the version before strict parsing, so newer versions degrade to a safe message.
        val version = fields["v"] ?: return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        if (version != WaveProtocol.VERSION.toString()) {
            return WaveDecodeResult.Rejected(WaveRejectReason.UNSUPPORTED_VERSION)
        }
        if (fields.keys.any { it !in requiredKeys } || !requiredKeys.all { it in fields }) {
            return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        }

        val type = WaveMessageType.fromWire(fields.getValue("t"))
            ?: return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        val issuedAt = parseSeconds(fields.getValue("i"))
            ?: return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        val expiresAt = parseSeconds(fields.getValue("e"))
            ?: return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        val capsRaw = fields.getValue("c")
        val caps = if (capsRaw.isEmpty()) emptyList() else capsRaw.split(',')
        val signalB64 = fields.getValue("g")
        if (!base64UrlRegex.matches(signalB64)) return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        val signalBytes = decodeBase64Url(signalB64) ?: return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        if (signalBytes.size > WaveProtocol.MAX_SIGNAL_BYTES) return WaveDecodeResult.Rejected(WaveRejectReason.TOO_LARGE)
        if (signalBytes.any { !isWireTextChar(it.toInt() and 0xff) }) {
            return WaveDecodeResult.Rejected(WaveRejectReason.MALFORMED)
        }

        val envelope = WaveEnvelope(
            type = type,
            sidHex = fields.getValue("s"),
            nonceHex = fields.getValue("n"),
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            caps = caps,
            signal = String(signalBytes, Charsets.US_ASCII),
        )
        validateFields(envelope)?.let { return WaveDecodeResult.Rejected(it) }
        if (issuedAt > nowSeconds + WaveProtocol.MAX_CLOCK_SKEW_SECONDS) {
            return WaveDecodeResult.Rejected(WaveRejectReason.NOT_YET_VALID)
        }
        if (nowSeconds > expiresAt + WaveProtocol.MAX_CLOCK_SKEW_SECONDS) {
            return WaveDecodeResult.Rejected(WaveRejectReason.EXPIRED)
        }
        return WaveDecodeResult.Ok(envelope)
    }

    private fun isWireTextChar(code: Int): Boolean =
        code in 0x20..0x7e || code == 0x0a || code == 0x0d

    private fun parseSeconds(value: String): Long? =
        if (digitsRegex.matches(value)) value.toLong() else null

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeBase64Url(value: String): ByteArray? =
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            null
        }
}
