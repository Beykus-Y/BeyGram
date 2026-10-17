package com.beykus.beygram.data.media

import kotlin.math.abs

/** Requested AE frame rate range. Custom FPS remains the upper bound of its own profile. */
fun VideoMessageSettings.requestedFrameRateRange(): IntRange {
    val maximum = when (preset) {
        VideoQualityPreset.Standard, VideoQualityPreset.High -> 30
        VideoQualityPreset.Maximum -> 60
        VideoQualityPreset.Custom -> customFps
    }
    val minimum = if (preset == VideoQualityPreset.Maximum || preset == VideoQualityPreset.Custom) {
        frameRatePriority.minimumFps.coerceAtMost(maximum)
    } else {
        maximum
    }
    return minimum..maximum
}

/**
 * Prefer actual camera-advertised ranges over arbitrary Range(min, max) requests.
 * First preserve the upper limit, then avoid dropping below the desired minimum.
 */
fun chooseSupportedFrameRateRange(requested: IntRange, supported: Collection<IntRange>): IntRange? =
    supported.minWithOrNull(
        compareBy<IntRange>(
            { (requested.last - it.last).coerceAtLeast(0) },
            { (requested.first - it.first).coerceAtLeast(0) },
            { abs(it.first - requested.first) + abs(it.last - requested.last) }
        )
    )
