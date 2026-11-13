package com.beykus.beygram.media.bgwave

/** Result of decoding a BG Wave link with the BG-Media codec (issue #799). */
sealed interface BgWaveDecodeResult {
    data class Ok(
        val type: Int,
        val sidHex: String,
        val issuedAt: Long,
        val expiresAt: Long,
        val caps: Long,
        val nonce: Long,
        val candidateCount: Int,
    ) : BgWaveDecodeResult

    data class Rejected(val reason: String) : BgWaveDecodeResult
}

/**
 * Thin wrapper over the BG-Media `WaveSignaling` decoder, loaded from the same library as BG Mix.
 * BeyGram does not implement the wire format itself.
 */
object BgWaveNative {
    private val loaded: Boolean = try {
        System.loadLibrary("bgbeat_jni")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    fun decodeUri(uri: String, nowSeconds: Long): BgWaveDecodeResult {
        if (!loaded) return BgWaveDecodeResult.Rejected("unavailable")
        return parseDecodeResult(nativeDecodeUri(uri, nowSeconds))
    }

    private external fun nativeDecodeUri(uri: String, nowSeconds: Long): String
}

/** Parses the flat ASCII summary produced by bgwave_jni.cpp. Never throws. */
internal fun parseDecodeResult(raw: String?): BgWaveDecodeResult {
    if (raw == null) return BgWaveDecodeResult.Rejected("null")
    val parts = raw.split('|')
    if (parts.isEmpty()) return BgWaveDecodeResult.Rejected("malformed")
    if (parts[0] == "err") return BgWaveDecodeResult.Rejected(parts.drop(1).joinToString("|").ifEmpty { "unknown" })
    if (parts[0] != "ok" || parts.size != 8) return BgWaveDecodeResult.Rejected("malformed")
    return try {
        BgWaveDecodeResult.Ok(
            type = parts[1].toInt(),
            sidHex = parts[2],
            issuedAt = parts[3].toLong(),
            expiresAt = parts[4].toLong(),
            caps = parts[5].toLong(),
            nonce = parts[6].toLong(),
            candidateCount = parts[7].toInt(),
        )
    } catch (e: NumberFormatException) {
        BgWaveDecodeResult.Rejected("malformed")
    }
}
