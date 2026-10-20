package com.beykus.beygram.data.media

import kotlin.math.ceil

/**
 * Pure planning and measurement rules for the opt-in "real FPS" check. Kept free of Android
 * types so the decisions (what is opened, what is only listed, how a result is judged) are unit-tested.
 */
internal data class ProbeSize(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}

internal enum class ProbeFacing { FRONT, BACK, EXTERNAL, UNKNOWN }

/** Which lenses the user asked to check. */
internal enum class ProbeScope { FRONT, BACK, ALL }

/** Everything the planner needs from one camera ID. Collected passively, without opening the camera. */
internal data class ProbeCameraCaps(
    val cameraId: String,
    val facing: ProbeFacing,
    /** Normal AE target ranges. High-speed ranges must never be mixed in. */
    val normalRanges: List<IntRange>,
    /** Sizes a preview-like (opaque) stream can use in a normal session. */
    val streamSizes: List<ProbeSize>,
    /** Minimum frame duration per stream size, in nanoseconds; 0 or missing means unknown. */
    val minFrameDurationNs: Map<ProbeSize, Long>,
    /** Constrained high-speed configurations, listed separately and not opened by the probe. */
    val highSpeed: Map<ProbeSize, List<IntRange>> = emptyMap()
)

internal enum class ProbeModeStatus {
    /** Will be opened and measured. */
    PLANNED,
    /** No normal range reaches this frame rate: the camera is not opened for it. */
    NOT_ADVERTISED,
    /** The stream of this size is slower than the range asks for, so the pair is not compatible. */
    STREAM_TOO_SLOW,
    /** Only a constrained high-speed session offers it: no sound, special session. Listed, not opened. */
    HIGH_SPEED_ONLY,
    /** Compatible, but the per-run limit was reached to avoid heating the phone. */
    SKIPPED_LIMIT
}

internal data class ProbeMode(
    val cameraId: String,
    val facing: ProbeFacing,
    val size: ProbeSize,
    /** Target frame rate the row is about (24, 30, 60…). */
    val targetFps: Int,
    /** Range handed to the camera; null when the row is not opened. */
    val range: IntRange?,
    val status: ProbeModeStatus,
    /** 1e9 / minFrameDuration: a ceiling of the stream, not a promise of the achieved rate. */
    val streamCeilingFps: Double?
)

/** Square sizes come first only if a lens advertises them; recordings are cropped to a circle anyway. */
internal val PROBE_CANDIDATE_SIZES = listOf(
    ProbeSize(1920, 1080), ProbeSize(1280, 720), ProbeSize(720, 720),
    ProbeSize(640, 640), ProbeSize(640, 480)
)

internal const val PROBE_MAX_OPENED_MODES = 16

internal fun ProbeFacing.matches(scope: ProbeScope): Boolean = when (scope) {
    ProbeScope.ALL -> true
    ProbeScope.FRONT -> this == ProbeFacing.FRONT
    ProbeScope.BACK -> this == ProbeFacing.BACK
}

/**
 * Rows for every lens in [scope] and every advertised candidate size: one row per normal frame
 * rate worth checking (the best range up to 30 FPS and every range reaching 50+ FPS) plus an explicit
 * "not advertised" 60 FPS row, so the report never silently omits 60. Unadvertised sizes are skipped
 * entirely: the probe never tries to force a size the lens did not list.
 */
internal fun planVideoNoteProbe(cameras: List<ProbeCameraCaps>, scope: ProbeScope): List<ProbeMode> {
    val rows = mutableListOf<ProbeMode>()
    var opened = 0
    for (camera in cameras.filter { it.facing.matches(scope) }) {
        val sizes = PROBE_CANDIDATE_SIZES.filter { it in camera.streamSizes }
        for (size in sizes) {
            val ceiling = camera.minFrameDurationNs[size]?.takeIf { it > 0 }?.let { 1_000_000_000.0 / it }
            val targets = probeTargets(camera.normalRanges)
            for ((target, range) in targets) {
                val tooSlow = ceiling != null && ceiling < range.last * 0.95
                val status = when {
                    tooSlow -> ProbeModeStatus.STREAM_TOO_SLOW
                    opened >= PROBE_MAX_OPENED_MODES -> ProbeModeStatus.SKIPPED_LIMIT
                    else -> ProbeModeStatus.PLANNED
                }
                if (status == ProbeModeStatus.PLANNED) opened++
                rows += ProbeMode(camera.cameraId, camera.facing, size, target,
                    range.takeIf { status == ProbeModeStatus.PLANNED }, status, ceiling)
            }
            if (targets.none { it.first >= 50 }) {
                val highSpeed = camera.highSpeed[size].orEmpty().any { it.last >= 60 }
                rows += ProbeMode(camera.cameraId, camera.facing, size, 60, null,
                    if (highSpeed) ProbeModeStatus.HIGH_SPEED_ONLY else ProbeModeStatus.NOT_ADVERTISED, ceiling)
            }
        }
        // High-speed sizes that are not among the candidates are still listed, never opened.
        camera.highSpeed.filterKeys { it !in sizes }.forEach { (size, ranges) ->
            val best = ranges.maxOfOrNull { it.last } ?: return@forEach
            rows += ProbeMode(camera.cameraId, camera.facing, size, best, null, ProbeModeStatus.HIGH_SPEED_ONLY, null)
        }
    }
    return rows
}

/**
 * The best fixed-ish range for each frame rate worth checking: the highest maximum up to 30,
 * and every maximum of at least 50. A fixed range (60..60) is preferred over a variable one (15..60)
 * because a variable range lets auto-exposure lower the rate in dim light, which is what we measure.
 */
internal fun probeTargets(normalRanges: List<IntRange>): List<Pair<Int, IntRange>> {
    val maxima = normalRanges.map { it.last }.distinct()
    val chosen = buildList {
        maxima.filter { it <= 30 }.maxOrNull()?.let(::add)
        addAll(maxima.filter { it >= 50 }.sorted())
    }
    return chosen.map { max ->
        max to normalRanges.filter { it.last == max }.maxBy { it.first }
    }
}

internal data class ProbeFrameStats(
    val frames: Int,
    val measuredFps: Double?,
    val p50GapMs: Double?,
    val p95GapMs: Double?,
    val maxGapMs: Double?,
    /** Gaps longer than 1.5× the expected interval: frames the sensor skipped or delivered late. */
    val longGaps: Int,
    val nonIncreasing: Int,
    val medianExposureMs: Double?
)

/** Sensor timestamps (ns) of delivered frames, in arrival order, plus exposure times when the camera reports them. */
internal fun measureProbeFrames(timestampsNs: List<Long>, exposuresNs: List<Long>, targetFps: Int): ProbeFrameStats {
    val gaps = ArrayList<Long>()
    var nonIncreasing = 0
    for (i in 1 until timestampsNs.size) {
        val gap = timestampsNs[i] - timestampsNs[i - 1]
        if (gap > 0) gaps += gap else nonIncreasing++
    }
    // Only increasing intervals count: a repeated or reordered timestamp must not inflate the rate
    // (duplicated 30 FPS results would otherwise read as 60).
    val span = gaps.sum()
    val fps = if (span > 0) gaps.size * 1_000_000_000.0 / span else null
    val expectedNs = 1_000_000_000.0 / targetFps.coerceAtLeast(1)
    val sorted = gaps.sorted()
    fun pct(p: Double): Double? = if (sorted.isEmpty()) null
        else sorted[ceil((sorted.size - 1) * p).toInt()] / 1_000_000.0
    val exposures = exposuresNs.filter { it > 0 }.sorted()
    return ProbeFrameStats(
        frames = timestampsNs.size,
        measuredFps = fps,
        p50GapMs = pct(0.5),
        p95GapMs = pct(0.95),
        maxGapMs = sorted.lastOrNull()?.let { it / 1_000_000.0 },
        longGaps = gaps.count { it > expectedNs * 1.5 },
        nonIncreasing = nonIncreasing,
        medianExposureMs = exposures.getOrNull(exposures.size / 2)?.let { it / 1_000_000.0 }
    )
}

internal enum class ProbeVerdict {
    /** At least 90% of the target rate was delivered. */
    REACHED,
    /** Exposure time is about as long as a frame interval: dim light caps the rate. */
    LOW_LIGHT,
    /** The phone reported severe heating during the check. */
    THERMAL,
    /** Fewer frames than asked for and no measured reason. Reported as "cause not established". */
    BELOW_UNEXPLAINED,
    /** The camera opened but delivered no usable frames. */
    NO_FRAMES
}

/** Android PowerManager.THERMAL_STATUS_SEVERE. */
internal const val PROBE_THERMAL_SEVERE = 3

internal fun classifyProbe(stats: ProbeFrameStats, targetFps: Int, thermalStatus: Int?): ProbeVerdict {
    val fps = stats.measuredFps ?: return ProbeVerdict.NO_FRAMES
    if (stats.frames < 3) return ProbeVerdict.NO_FRAMES
    if (fps >= targetFps * 0.9) return ProbeVerdict.REACHED
    val intervalMs = 1000.0 / targetFps
    val exposure = stats.medianExposureMs
    return when {
        exposure != null && exposure >= intervalMs * 0.9 -> ProbeVerdict.LOW_LIGHT
        thermalStatus != null && thermalStatus >= PROBE_THERMAL_SEVERE -> ProbeVerdict.THERMAL
        else -> ProbeVerdict.BELOW_UNEXPLAINED
    }
}
