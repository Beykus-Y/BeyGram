package com.beykus.beygram.data.media

import kotlin.math.ceil

/**
 * One CaptureResult sample from the real recording session. Only numbers and state codes are
 * kept: no pixels, no audio and no identifiers (issue #234, §4 and §6).
 */
internal data class AeCaptureSample(
    val frameDurationNs: Long? = null,
    val targetFpsRange: IntRange? = null,
    val aeState: Int? = null,
    val videoStabilizationMode: Int? = null,
    val opticalStabilizationMode: Int? = null
)

/**
 * What the camera itself reported while recording. [dominantTargetRange] is the range the sensor
 * actually used, which may differ from the range handed to CameraX. A null field means the device
 * did not report it, never that the value is zero.
 */
internal data class AeCaptureSummary(
    val dominantTargetRange: IntRange?,
    val distinctTargetRanges: List<IntRange>,
    val frameDurationP50Ms: Double?,
    val frameDurationP95Ms: Double?,
    /** Frames whose reported duration exceeded the target frame budget by more than 5%. */
    val frameDurationOverBudget: Int,
    val aeStates: Map<Int, Int>,
    val videoStabilizationMode: Int?,
    val opticalStabilizationMode: Int?
) {
    val hasData: Boolean
        get() = dominantTargetRange != null || frameDurationP50Ms != null ||
            aeStates.isNotEmpty() || videoStabilizationMode != null || opticalStabilizationMode != null
}

internal fun summarizeAeCapture(samples: List<AeCaptureSample>, targetFps: Int): AeCaptureSummary {
    val ranges = samples.mapNotNull { it.targetFpsRange }
    val rangeCounts = ranges.groupingBy { it }.eachCount()
    // A tie is resolved towards the higher range: the report must not understate what the sensor used.
    val dominant = rangeCounts.entries.maxWithOrNull(
        compareBy<Map.Entry<IntRange, Int>> { it.value }
            .thenBy { it.key.last }
            .thenBy { it.key.first }
    )?.key
    val durations = samples.mapNotNull { it.frameDurationNs }
    return AeCaptureSummary(
        dominantTargetRange = dominant,
        distinctTargetRanges = rangeCounts.keys.sortedWith(compareBy({ it.first }, { it.last })),
        frameDurationP50Ms = sensorDurationPercentileMs(durations, 0.5),
        frameDurationP95Ms = sensorDurationPercentileMs(durations, 0.95),
        frameDurationOverBudget = frameDurationsOverBudget(durations, targetFps),
        aeStates = samples.mapNotNull { it.aeState }.groupingBy { it }.eachCount().toSortedMap(),
        // The final non-null mode is what the session settled on after CameraX applied its defaults.
        videoStabilizationMode = samples.lastOrNull { it.videoStabilizationMode != null }?.videoStabilizationMode,
        opticalStabilizationMode = samples.lastOrNull { it.opticalStabilizationMode != null }?.opticalStabilizationMode
    )
}

/** Percentile of positive sensor frame durations in nanoseconds, converted to milliseconds. */
internal fun sensorDurationPercentileMs(valuesNs: Collection<Long>, percentile: Double): Double? {
    val sorted = valuesNs.filter { it > 0L }.sorted()
    if (sorted.isEmpty()) return null
    val index = ceil((sorted.size - 1) * percentile.coerceIn(0.0, 1.0)).toInt()
    return sorted[index] / 1_000_000.0
}

/** Counts frames whose duration exceeds the 1 / [targetFps] budget by more than 5%. */
internal fun frameDurationsOverBudget(valuesNs: Collection<Long>, targetFps: Int): Int {
    val budgetNs = 1_000_000_000L / targetFps.coerceAtLeast(1)
    val limitNs = budgetNs + budgetNs / 20
    return valuesNs.count { it > limitNs }
}
