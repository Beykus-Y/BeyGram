package com.beykus.beygram.data.media

/**
 * Result of the Android 15+ passive Camera2 check for one planned
 * Preview + VideoCapture(encoder) stream combination (issue #234, §2/§6).
 *
 * [UNKNOWN] is deliberately separate from [UNSUPPORTED]: an unavailable API, a missing surface
 * or an encoder error says nothing about whether the camera can run the combination.
 */
internal enum class VideoNoteSessionSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }

internal enum class VideoNoteSessionReason {
    CONFIGURATION_SUPPORTED,
    CONFIGURATION_REJECTED,
    API_TOO_OLD,
    SETUP_CHECK_UNAVAILABLE,
    NO_SURFACE,
    EXCEPTION
}

internal data class VideoNoteSessionCheck(
    val support: VideoNoteSessionSupport,
    val reason: VideoNoteSessionReason,
    val apiLevel: Int,
    val previewWidth: Int? = null,
    val previewHeight: Int? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    /** Class name or short diagnostic code; never a path, message text or file identifier. */
    val detail: String? = null
)

/**
 * Pure decision table so the "unknown vs unsupported" distinction is unit-tested without a device.
 * [setupSupported] false means the ROM cannot answer the query at all (not that the combination failed).
 */
internal fun sessionConfigurationOutcome(
    apiLevel: Int,
    setupSupported: Boolean?,
    configurationSupported: Boolean?,
    error: String? = null
): Pair<VideoNoteSessionSupport, VideoNoteSessionReason> = when {
    apiLevel < 35 -> VideoNoteSessionSupport.UNKNOWN to VideoNoteSessionReason.API_TOO_OLD
    error != null -> VideoNoteSessionSupport.UNKNOWN to VideoNoteSessionReason.EXCEPTION
    setupSupported == false -> VideoNoteSessionSupport.UNKNOWN to VideoNoteSessionReason.SETUP_CHECK_UNAVAILABLE
    configurationSupported == null -> VideoNoteSessionSupport.UNKNOWN to VideoNoteSessionReason.SETUP_CHECK_UNAVAILABLE
    configurationSupported -> VideoNoteSessionSupport.SUPPORTED to VideoNoteSessionReason.CONFIGURATION_SUPPORTED
    else -> VideoNoteSessionSupport.UNSUPPORTED to VideoNoteSessionReason.CONFIGURATION_REJECTED
}
