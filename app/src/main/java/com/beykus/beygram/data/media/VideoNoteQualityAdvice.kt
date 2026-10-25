package com.beykus.beygram.data.media

import android.content.Context
import com.beykus.beygram.R

/**
 * Why the recorder picked a certain frame-rate range, expressed as advice the settings screen can
 * show. No branch promises 60 FPS: a planned high rate still needs a measurement after recording.
 */
internal enum class VideoNoteQualityAdvice {
    HIGH_FPS_NOT_YET_MEASURED,
    AS_REQUESTED,
    NORMAL_60_UNAVAILABLE,
    ENCODER_LIMIT,
    STREAM_LIMIT,
    RANGE_FALLBACK,
    NO_RANGE,
    EXPORT_DROP,
    CAMERA_BELOW_TARGET,
    UNKNOWN
}

/**
 * Pure mapping from the recorded/planned cause and stage to a user-facing explanation.
 * [stage] wins over [cause] for measured runtime results, because the plan alone cannot explain
 * why a 60 FPS request produced 30 FPS on a particular take.
 */
internal fun videoNoteQualityAdvice(
    cause: VideoNoteFpsCause?,
    requestedFps: Int,
    stage: VideoNoteFpsLimitStage? = null
): VideoNoteQualityAdvice = when {
    stage == VideoNoteFpsLimitStage.EXPORT -> VideoNoteQualityAdvice.EXPORT_DROP
    stage == VideoNoteFpsLimitStage.CAMERA_RUNTIME -> VideoNoteQualityAdvice.CAMERA_BELOW_TARGET
    cause == null -> VideoNoteQualityAdvice.UNKNOWN
    cause == VideoNoteFpsCause.AS_REQUESTED && requestedFps >= 50 -> VideoNoteQualityAdvice.HIGH_FPS_NOT_YET_MEASURED
    cause == VideoNoteFpsCause.AS_REQUESTED -> VideoNoteQualityAdvice.AS_REQUESTED
    cause == VideoNoteFpsCause.NO_NORMAL_60 -> VideoNoteQualityAdvice.NORMAL_60_UNAVAILABLE
    cause == VideoNoteFpsCause.ENCODER_REJECTS_60 -> VideoNoteQualityAdvice.ENCODER_LIMIT
    cause == VideoNoteFpsCause.STREAM_REJECTS_60 -> VideoNoteQualityAdvice.STREAM_LIMIT
    cause == VideoNoteFpsCause.CAMERA_RANGE_FALLBACK -> VideoNoteQualityAdvice.RANGE_FALLBACK
    cause == VideoNoteFpsCause.NO_COMPATIBLE_RANGE -> VideoNoteQualityAdvice.NO_RANGE
    else -> VideoNoteQualityAdvice.UNKNOWN
}

internal fun videoNoteQualityAdviceText(context: Context, advice: VideoNoteQualityAdvice): String =
    context.getString(when (advice) {
        VideoNoteQualityAdvice.HIGH_FPS_NOT_YET_MEASURED -> R.string.quality_advice_measure_high
        VideoNoteQualityAdvice.AS_REQUESTED -> R.string.quality_advice_as_requested
        VideoNoteQualityAdvice.NORMAL_60_UNAVAILABLE -> R.string.quality_advice_no_normal_60
        VideoNoteQualityAdvice.ENCODER_LIMIT -> R.string.quality_advice_encoder_limit
        VideoNoteQualityAdvice.STREAM_LIMIT -> R.string.quality_advice_stream_limit
        VideoNoteQualityAdvice.RANGE_FALLBACK -> R.string.quality_advice_range_fallback
        VideoNoteQualityAdvice.NO_RANGE -> R.string.quality_advice_no_range
        VideoNoteQualityAdvice.EXPORT_DROP -> R.string.quality_advice_export_drop
        VideoNoteQualityAdvice.CAMERA_BELOW_TARGET -> R.string.quality_advice_camera_runtime
        VideoNoteQualityAdvice.UNKNOWN -> R.string.quality_advice_unknown
    })

/** Advice for a report restored after a restart, where only the stable cause key was kept. */
internal fun videoNoteQualityAdvice(context: Context, causeKey: String?, requestedFps: Int, stage: VideoNoteFpsLimitStage): String {
    val cause = causeKey?.let { key -> runCatching { VideoNoteFpsCause.valueOf(key) }.getOrNull() }
    return videoNoteQualityAdviceText(context, videoNoteQualityAdvice(cause, requestedFps, stage))
}
