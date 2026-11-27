package com.beykus.beygram.playback

/**
 * Why ExoPlayer has not handed the current track over to BG Mix yet.
 * Used for both privacy-safe diagnostics (no track titles/paths/IDs logged) and
 * keeping the UI "BG" preparing indicator aligned with real handover eligibility.
 */
internal enum class BgMixHandoverBlockReason {
    Ready,
    Disabled,
    NativeUnavailable,
    NotMusicOrPrivate,
    FormatNotCandidate,
    Retiring,
    TrackRejected,
    AttemptsExhausted,
    NotPlaying,
    SpeedNotNormal,
    WindowTooShort,
    FileNotReady,
}

internal fun bgMixHandoverBlockReason(
    enabled: Boolean,
    nativeAvailable: Boolean,
    isMusic: Boolean,
    systemPrivate: Boolean,
    isCandidate: Boolean,
    isRetiring: Boolean,
    isRejected: Boolean,
    isAttemptsExhausted: Boolean,
    playing: Boolean,
    intentPlaying: Boolean,
    ended: Boolean,
    waitingForNetwork: Boolean,
    hasDeferredSeek: Boolean,
    speed: Float,
    remainingMs: Long?,
    minRemainingMs: Long,
    fileReady: Boolean,
): BgMixHandoverBlockReason = when {
    !enabled -> BgMixHandoverBlockReason.Disabled
    !nativeAvailable -> BgMixHandoverBlockReason.NativeUnavailable
    !isMusic || systemPrivate -> BgMixHandoverBlockReason.NotMusicOrPrivate
    !isCandidate -> BgMixHandoverBlockReason.FormatNotCandidate
    isRetiring -> BgMixHandoverBlockReason.Retiring
    isRejected -> BgMixHandoverBlockReason.TrackRejected
    isAttemptsExhausted -> BgMixHandoverBlockReason.AttemptsExhausted
    !playing || !intentPlaying || ended || waitingForNetwork || hasDeferredSeek -> BgMixHandoverBlockReason.NotPlaying
    speed != 1f -> BgMixHandoverBlockReason.SpeedNotNormal
    remainingMs != null && remainingMs < minRemainingMs -> BgMixHandoverBlockReason.WindowTooShort
    !fileReady -> BgMixHandoverBlockReason.FileNotReady
    else -> BgMixHandoverBlockReason.Ready
}

/**
 * Only show the grey "BG" preparing mark when handover is actively waiting on a local file,
 * waiting for a retiring host, or actively preparing the native session.
 */
internal fun BgMixHandoverBlockReason.showsPreparingIndicator(): Boolean =
    this == BgMixHandoverBlockReason.Ready ||
        this == BgMixHandoverBlockReason.FileNotReady ||
        this == BgMixHandoverBlockReason.Retiring

/** A stable Media3 window UID distinguishes repeated occurrences of the same media ID. */
internal data class BgMixExoOccurrence(val mediaId: String, val windowUid: Any) {
    fun canFinishOn(current: BgMixExoOccurrence?, ended: Boolean): Boolean = !ended && this == current
}
