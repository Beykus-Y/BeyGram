package com.beykus.beygram.data.media

/**
 * Why the recorder asked the camera for a particular FPS range. The diagnostic report must
 * distinguish "60 FPS was never attempted because of policy" from "the camera delivered less".
 */
internal enum class VideoNoteFpsCause {
    /** The selected range covers the requested maximum. Actual FPS still has to be measured. */
    AS_REQUESTED,
    /** No advertised range reaches the requested maximum (for example Custom 30 on a 24-only lens). */
    CAMERA_RANGE_FALLBACK,
    /** 60 FPS was requested, but no normal (non-high-speed) range reaches 60. 60 FPS is NOT attempted. */
    NO_NORMAL_60,
    /** A normal 60 FPS range exists, but the H.264 encoder rejects 60 FPS at the source size. */
    ENCODER_REJECTS_60,
    /** Nothing compatible was advertised; the camera default is used and must be measured. */
    NO_COMPATIBLE_RANGE
}

internal data class VideoNoteFpsDecision(
    val requested: IntRange,
    val selected: IntRange?,
    val cause: VideoNoteFpsCause,
    /** Ranges usable in a normal recording session. High-speed-only ranges never appear here. */
    val normalCandidates: List<IntRange>,
    val encoderSupports60: Boolean?
) {
    /** True only when a range with an upper bound of at least 50 FPS was actually handed to the camera. */
    val attemptedHighFrameRate: Boolean get() = (selected?.last ?: 0) >= 50
}

/** Never treat high-speed-only ranges as normal CameraX recording support. */
internal fun safeVideoNoteFpsSelection(
    requested: IntRange,
    cameraXRanges: Collection<IntRange>,
    camera2NormalRanges: Collection<IntRange>?,
    encoderSupports60: Boolean?
): VideoNoteFpsDecision {
    val regular = cameraXRanges.filter { range ->
        range.first <= requested.last && (camera2NormalRanges == null ||
            camera2NormalRanges.any { it.first <= range.first && it.last >= range.last })
    }
    val wants60 = requested.last >= 50
    val normal60 = regular.any { it.last >= 60 }
    val can60 = wants60 && normal60 && encoderSupports60 != false
    val available = if (wants60 && !can60) regular.filter { it.last <= 30 }
        .ifEmpty { regular.filter { it.last < 50 } }
    else regular
    // Preserve Custom 24 FPS and the lower bound used for low-light capture.
    // Only lower the maximum when a requested 60 FPS cannot be confirmed.
    val target = if (wants60 && !can60) requested.first.coerceAtMost(30)..30 else requested
    val picked = chooseSupportedFrameRateRange(target, available)
    val cause = when {
        picked == null -> VideoNoteFpsCause.NO_COMPATIBLE_RANGE
        wants60 && !can60 && normal60 -> VideoNoteFpsCause.ENCODER_REJECTS_60
        wants60 && !can60 -> VideoNoteFpsCause.NO_NORMAL_60
        picked.last < requested.last -> VideoNoteFpsCause.CAMERA_RANGE_FALLBACK
        else -> VideoNoteFpsCause.AS_REQUESTED
    }
    return VideoNoteFpsDecision(requested, picked, cause, regular, encoderSupports60)
}

/**
 * Preview and VideoCapture share one camera session, so the preview must not ask for a different
 * range: a mismatch can make the camera settle on the lower of the two. Reuse the recording range
 * when the (possibly switched) lens advertises it, otherwise take the closest advertised range.
 */
internal fun previewFrameRateFor(captureRange: IntRange?, previewSupported: Collection<IntRange>): IntRange? = when {
    captureRange == null -> null
    captureRange in previewSupported -> captureRange
    // Never let the preview ask for a higher maximum than the recording.
    else -> chooseSupportedFrameRateRange(captureRange, previewSupported.filter { it.last <= captureRange.last })
        ?: chooseSupportedFrameRateRange(captureRange, previewSupported)
}

/** The pipeline stage that limited the measured frame rate. Never a claim about the sensor itself. */
internal enum class VideoNoteFpsLimitStage {
    /** RAW and final file are close to what was asked for. */
    NONE,
    /** 60 FPS was requested but never handed to the camera (see [VideoNoteFpsCause]). */
    POLICY,
    /** A high range was requested, but the RAW recording has noticeably fewer frames. */
    CAMERA_RUNTIME,
    /** RAW had the frames, the processed file lost them. */
    EXPORT,
    /** Not enough data (no measurement or no explicit range was requested). */
    UNKNOWN
}

internal fun videoNoteFpsLimitStage(target: Int, attemptedMax: Int?, raw: Double?, output: Double?): VideoNoteFpsLimitStage = when {
    raw == null || output == null -> VideoNoteFpsLimitStage.UNKNOWN
    raw >= 20.0 && output < raw * 0.8 -> VideoNoteFpsLimitStage.EXPORT
    target >= 50 && attemptedMax != null && attemptedMax < 50 -> VideoNoteFpsLimitStage.POLICY
    attemptedMax != null && raw < attemptedMax * 0.8 -> VideoNoteFpsLimitStage.CAMERA_RUNTIME
    attemptedMax == null && raw < target * 0.8 -> VideoNoteFpsLimitStage.UNKNOWN
    else -> VideoNoteFpsLimitStage.NONE
}
