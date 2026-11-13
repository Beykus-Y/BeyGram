package com.beykus.beygram.media.bgwave

/**
 * What a BG Wave message renders as in the chat. Pure data: the view layer maps [messageKey]
 * to a string resource, so no user-visible text lives here.
 */
sealed interface BgWaveCardState {
    /** A valid invitation from another user, shown as a wave card. */
    data class Invite(val sidHex: String, val expiresAt: Long, val candidateCount: Int) : BgWaveCardState

    /** A valid answer, cancel or end for a session the card already knows about. */
    data class Signal(val type: BgWaveSignalKind, val sidHex: String) : BgWaveCardState

    /** Unknown, expired or damaged wave data, shown as safe plain text with the original link. */
    data class SafeText(val messageKey: String) : BgWaveCardState
}

enum class BgWaveSignalKind { ANSWER, CANCEL, END }

object BgWaveCardKeys {
    const val UNSUPPORTED_VERSION = "bgwave_safe_unsupported_version"
    const val EXPIRED = "bgwave_safe_expired"
    const val NOT_YET_VALID = "bgwave_safe_not_yet_valid"
    const val INVALID = "bgwave_safe_invalid"
    const val UNAVAILABLE = "bgwave_safe_unavailable"
}

/**
 * Maps a decode result to a card state. Returns null for text that is not a wave link at all,
 * so ordinary messages are never touched.
 */
fun bgWaveCardStateFor(result: BgWaveDecodeResult): BgWaveCardState? = when (result) {
    is BgWaveDecodeResult.Ok -> when (result.type) {
        1 -> BgWaveCardState.Invite(result.sidHex, result.expiresAt, result.candidateCount)
        2 -> BgWaveCardState.Signal(BgWaveSignalKind.ANSWER, result.sidHex)
        3 -> BgWaveCardState.Signal(BgWaveSignalKind.CANCEL, result.sidHex)
        4 -> BgWaveCardState.Signal(BgWaveSignalKind.END, result.sidHex)
        else -> BgWaveCardState.SafeText(BgWaveCardKeys.INVALID)
    }
    is BgWaveDecodeResult.Rejected -> when (result.reason) {
        "NOT_WAVE" -> null
        "UNSUPPORTED_VERSION" -> BgWaveCardState.SafeText(BgWaveCardKeys.UNSUPPORTED_VERSION)
        "EXPIRED" -> BgWaveCardState.SafeText(BgWaveCardKeys.EXPIRED)
        "NOT_YET_VALID" -> BgWaveCardState.SafeText(BgWaveCardKeys.NOT_YET_VALID)
        "unavailable" -> BgWaveCardState.SafeText(BgWaveCardKeys.UNAVAILABLE)
        else -> BgWaveCardState.SafeText(BgWaveCardKeys.INVALID)
    }
}
