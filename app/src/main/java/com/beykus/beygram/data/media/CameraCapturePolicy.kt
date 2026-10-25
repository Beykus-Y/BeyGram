package com.beykus.beygram.data.media

/** Photo or video preview mode of the in-app camera. Not persisted: every visit starts in photo mode. */
internal enum class CameraCaptureMode { Photo, Video }

/** Countdown choices offered above the shutter. 0 means "no timer". */
internal val cameraTimerOptions = listOf(0, 3, 10)

/** Duration choices for regular videos; the hard Telegram-safe cap stays [CAMERA_VIDEO_HARD_LIMIT_SECONDS]. */
internal val cameraVideoLimitOptions = listOf(15, 60, 600, 3600)
internal const val CAMERA_VIDEO_HARD_LIMIT_SECONDS = 60 * 60
internal const val CAMERA_MIN_VIDEO_DURATION_MS = 1_000L

internal fun cameraNextTimerSeconds(current: Int): Int {
    val index = cameraTimerOptions.indexOf(current).takeIf { it >= 0 } ?: 0
    return cameraTimerOptions[(index + 1) % cameraTimerOptions.size]
}

internal fun cameraVideoLimitSeconds(requested: Int): Int =
    requested.coerceIn(1, CAMERA_VIDEO_HARD_LIMIT_SECONDS)

/** A front camera without a flash unit can only light the scene with the screen; the shutter UI hides the flash button then. */
internal fun cameraFlashAvailable(hasFlashUnit: Boolean): Boolean = hasFlashUnit

/** Clamps a pinch gesture to the range CameraX reports for the currently bound lens. */
internal fun cameraZoomTarget(current: Float, scale: Float, min: Float?, max: Float?): Float {
    val lower = (min ?: 1f).coerceAtLeast(1f)
    val upper = (max ?: lower).coerceAtLeast(lower)
    return (current * scale).coerceIn(lower, upper)
}

internal fun cameraRemainingSeconds(limitSeconds: Int, elapsedMs: Long): Int {
    val remainingMs = limitSeconds * 1000L - elapsedMs.coerceAtLeast(0L)
    return ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()
}

internal fun cameraRecordingProgress(limitSeconds: Int, elapsedMs: Long): Float {
    if (limitSeconds <= 0) return 0f
    return (elapsedMs.toFloat() / (limitSeconds * 1000f)).coerceIn(0f, 1f)
}

internal fun cameraCaptureFileName(mode: CameraCaptureMode, token: String): String {
    val extension = if (mode == CameraCaptureMode.Photo) "jpg" else "mp4"
    return "camera-$token.$extension"
}

/**
 * Same rules as video notes: an error wins over a user cancel, and a recording that was never
 * explicitly stopped (a camera Finalize without a stop request) is not sent as a valid take.
 */
internal enum class CameraTakeOutcome { Send, Discarded, TooShort, Failed }

internal fun cameraTakeOutcome(error: Int, discard: Boolean, durationMs: Long, stopRequested: Boolean): CameraTakeOutcome = when {
    error != 0 -> CameraTakeOutcome.Failed
    !stopRequested -> CameraTakeOutcome.Failed
    discard -> CameraTakeOutcome.Discarded
    durationMs < CAMERA_MIN_VIDEO_DURATION_MS -> CameraTakeOutcome.TooShort
    else -> CameraTakeOutcome.Send
}

/** Android display rotation (Surface.ROTATION_*) in degrees for CameraX target rotation. */
internal fun cameraRotationDegrees(displayRotation: Int): Int = when (displayRotation) {
    1 -> 90 // Surface.ROTATION_90
    2 -> 180 // Surface.ROTATION_180
    3 -> 270 // Surface.ROTATION_270
    else -> 0
}
