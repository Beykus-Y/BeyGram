package com.beykus.beygram.data.media

/** What was measured at each pipeline stage (issue #234, §18). A null stage was not measured. */
internal data class VideoNoteStageEvidence(
    val targetFps: Int,
    /** Normal AE range of 50+ advertised for the lens; null = not queried. */
    val normalHighRangeAdvertised: Boolean? = null,
    /** Session combination check result; null = not checked. */
    val sessionSupported: Boolean? = null,
    /** Frame rate the sensor delivered (Camera2 check); null = not measured. */
    val sensorFps: Double? = null,
    val useCaseComboFps: Double? = null,
    val rawFps: Double? = null,
    val exportedFps: Double? = null,
    val remoteFps: Double? = null,
    /** True only when the remote file was fetched independently of the local copy. */
    val remoteIndependent: Boolean = false,
    val displayFps: Double? = null
)

internal enum class VideoNoteRootCause {
    NO_LIMIT_FOUND,
    NOT_ADVERTISED_IN_NORMAL_AE,
    SESSION_COMBINATION_UNSUPPORTED,
    SENSOR_OR_AE_OR_HAL_LIMIT,
    USECASE_COMBINATION_OR_CAMERAX_FALLBACK,
    RECORDER_OR_ENCODER_BELOW_TARGET,
    RAW_BELOW_TARGET_UNLOCALIZED,
    EXPORT_DROP_OR_FPS_CAP,
    TELEGRAM_REMOTE_TRANSFORMATION,
    REMOTE_STATUS_UNKNOWN,
    PLAYBACK_DECODER_OR_RENDERING,
    INSUFFICIENT_DATA
}

internal enum class VideoNoteConfidence {
    /** The stage before the limit was measured as good and this one as low. */
    ESTABLISHED,
    /** The stage before the limit was not measured, so the cause is a likely one only. */
    LIKELY,
    /** Nothing to conclude from. */
    NONE
}

internal data class VideoNoteDiagnosis(val cause: VideoNoteRootCause, val confidence: VideoNoteConfidence)

private fun reached(value: Double?, target: Int): Boolean? = value?.let { it >= target * 0.85 }

/**
 * Walks the pipeline from the camera to the display and names the first stage where the frame
 * rate falls below the target. A root cause is [VideoNoteConfidence.ESTABLISHED] only when the
 * previous stage was measured as good; otherwise it is only a likely one. A short burst of frames
 * must be filtered out before this point (a stalled stream is not a measurement).
 */
internal fun diagnoseVideoNoteFps(e: VideoNoteStageEvidence): VideoNoteDiagnosis {
    fun established(cause: VideoNoteRootCause) = VideoNoteDiagnosis(cause, VideoNoteConfidence.ESTABLISHED)
    fun likely(cause: VideoNoteRootCause) = VideoNoteDiagnosis(cause, VideoNoteConfidence.LIKELY)
    val t = e.targetFps
    val sensor = reached(e.sensorFps, t)
    val combo = reached(e.useCaseComboFps, t)
    val raw = reached(e.rawFps, t)
    val exported = reached(e.exportedFps, t)
    val remote = reached(e.remoteFps, t)
    val display = reached(e.displayFps, t)

    if (t >= 50 && e.normalHighRangeAdvertised == false) return established(VideoNoteRootCause.NOT_ADVERTISED_IN_NORMAL_AE)
    if (e.sessionSupported == false) return established(VideoNoteRootCause.SESSION_COMBINATION_UNSUPPORTED)
    if (sensor == false) return established(VideoNoteRootCause.SENSOR_OR_AE_OR_HAL_LIMIT)
    if (combo == false) {
        return if (sensor == true) established(VideoNoteRootCause.USECASE_COMBINATION_OR_CAMERAX_FALLBACK)
        else likely(VideoNoteRootCause.USECASE_COMBINATION_OR_CAMERAX_FALLBACK)
    }
    if (raw == false) {
        return when {
            combo == true -> established(VideoNoteRootCause.RECORDER_OR_ENCODER_BELOW_TARGET)
            sensor == true -> likely(VideoNoteRootCause.RECORDER_OR_ENCODER_BELOW_TARGET)
            else -> likely(VideoNoteRootCause.RAW_BELOW_TARGET_UNLOCALIZED)
        }
    }
    if (exported == false) {
        return if (raw == true) established(VideoNoteRootCause.EXPORT_DROP_OR_FPS_CAP)
        else likely(VideoNoteRootCause.EXPORT_DROP_OR_FPS_CAP)
    }
    if (remote == false) {
        // A cached local copy proves nothing about the server: only an independent fetch counts.
        return if (!e.remoteIndependent) VideoNoteDiagnosis(VideoNoteRootCause.REMOTE_STATUS_UNKNOWN, VideoNoteConfidence.NONE)
        else if (exported == true) established(VideoNoteRootCause.TELEGRAM_REMOTE_TRANSFORMATION)
        else likely(VideoNoteRootCause.TELEGRAM_REMOTE_TRANSFORMATION)
    }
    if (display == false) {
        return if (remote == true && e.remoteIndependent) established(VideoNoteRootCause.PLAYBACK_DECODER_OR_RENDERING)
        else likely(VideoNoteRootCause.PLAYBACK_DECODER_OR_RENDERING)
    }
    val measured = listOf(sensor, combo, raw, exported, remote, display).filterNotNull()
    return when {
        measured.isEmpty() -> VideoNoteDiagnosis(VideoNoteRootCause.INSUFFICIENT_DATA, VideoNoteConfidence.NONE)
        // A remote result that was never fetched independently cannot clear the Telegram stage.
        remote != null && !e.remoteIndependent -> VideoNoteDiagnosis(VideoNoteRootCause.REMOTE_STATUS_UNKNOWN, VideoNoteConfidence.NONE)
        else -> established(VideoNoteRootCause.NO_LIMIT_FOUND)
    }
}

internal fun rootCauseText(context: android.content.Context, diagnosis: VideoNoteDiagnosis): String {
    val cause = context.getString(when (diagnosis.cause) {
        VideoNoteRootCause.NO_LIMIT_FOUND -> com.beykus.beygram.R.string.root_cause_none
        VideoNoteRootCause.NOT_ADVERTISED_IN_NORMAL_AE -> com.beykus.beygram.R.string.root_cause_not_advertised
        VideoNoteRootCause.SESSION_COMBINATION_UNSUPPORTED -> com.beykus.beygram.R.string.root_cause_session
        VideoNoteRootCause.SENSOR_OR_AE_OR_HAL_LIMIT -> com.beykus.beygram.R.string.root_cause_sensor
        VideoNoteRootCause.USECASE_COMBINATION_OR_CAMERAX_FALLBACK -> com.beykus.beygram.R.string.root_cause_usecase
        VideoNoteRootCause.RECORDER_OR_ENCODER_BELOW_TARGET -> com.beykus.beygram.R.string.root_cause_recorder
        VideoNoteRootCause.RAW_BELOW_TARGET_UNLOCALIZED -> com.beykus.beygram.R.string.root_cause_raw
        VideoNoteRootCause.EXPORT_DROP_OR_FPS_CAP -> com.beykus.beygram.R.string.root_cause_export
        VideoNoteRootCause.TELEGRAM_REMOTE_TRANSFORMATION -> com.beykus.beygram.R.string.root_cause_telegram
        VideoNoteRootCause.REMOTE_STATUS_UNKNOWN -> com.beykus.beygram.R.string.root_cause_remote_unknown
        VideoNoteRootCause.PLAYBACK_DECODER_OR_RENDERING -> com.beykus.beygram.R.string.root_cause_playback
        VideoNoteRootCause.INSUFFICIENT_DATA -> com.beykus.beygram.R.string.root_cause_insufficient
    })
    val confidence = context.getString(when (diagnosis.confidence) {
        VideoNoteConfidence.ESTABLISHED -> com.beykus.beygram.R.string.root_confidence_established
        VideoNoteConfidence.LIKELY -> com.beykus.beygram.R.string.root_confidence_likely
        VideoNoteConfidence.NONE -> com.beykus.beygram.R.string.root_confidence_none
    })
    return context.getString(com.beykus.beygram.R.string.root_cause_line, cause, confidence)
}
