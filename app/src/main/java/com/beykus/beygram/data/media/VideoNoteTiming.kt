package com.beykus.beygram.data.media

import kotlin.math.roundToInt

/**
 * Where the expected frame interval came from. A file's own declared rate is preferred; the
 * requested rate is only a fallback, because a 30 FPS file recorded for a 60 FPS request must not
 * silently look like a healthy 60 FPS stream.
 */
enum class VideoNoteTimingSource {
    FILE_FPS,
    TARGET_FPS,
    MEDIAN_GAP,
    UNKNOWN
}

/**
 * Frame timing measured only from presentation timestamps (issue #234, §10/§15).
 *
 * [estimatedDroppedFrames] is a timestamp estimate, not a decoded-frame count: a gap of about two
 * frame intervals is reported as one missing frame. [repeatedTimestamps] counts equal consecutive
 * timestamps; a container can carry duplicate timestamps without duplicate pixels, so the report
 * never calls this visual frame duplication.
 */
internal data class VideoNoteFrameTiming(
    val sampleCount: Int = 0,
    val coveredMs: Double? = null,
    val p50GapMs: Double? = null,
    val p95GapMs: Double? = null,
    val maxGapMs: Double? = null,
    val expectedIntervalMs: Double? = null,
    val timingSource: VideoNoteTimingSource = VideoNoteTimingSource.UNKNOWN,
    val expectedFrames: Int? = null,
    val estimatedDroppedFrames: Int? = null,
    val repeatedTimestamps: Int = 0,
    val reorderedTimestamps: Int = 0,
    val gapsOver50Ms: Int = 0
) {
    val hasTimingData: Boolean get() = sampleCount >= 2 && coveredMs != null
}

/** Snapshot of one file stage kept in a diagnostic report, so compact TXT/JSON stay useful. */
data class VideoNoteStageTiming(
    val frameCount: Int,
    val p50GapMs: Double? = null,
    val p95GapMs: Double? = null,
    val maxGapMs: Double? = null,
    val coveredMs: Double? = null,
    val timingSource: VideoNoteTimingSource? = null,
    val expectedIntervalMs: Double? = null,
    val expectedFrames: Int? = null,
    val estimatedDroppedFrames: Int? = null,
    val repeatedTimestamps: Int? = null,
    val reorderedTimestamps: Int? = null,
    val audioSampleCount: Int? = null,
    val audioGapP50Ms: Double? = null,
    val audioGapP95Ms: Double? = null,
    val audioMaxGapMs: Double? = null,
    val audioRepeatedTimestamps: Int? = null,
    val avStartOffsetMs: Double? = null,
    val avEndOffsetMs: Double? = null,
    val avDriftMs: Double? = null,
    val avVerdict: AvSyncVerdict? = null,
    val exportFrameCount: Int? = null
)

/**
 * Pure timestamp analysis shared by the RAW file, the exported MP4 and the audio track.
 * Null fields mean "not measurable" and must stay unknown in the report.
 */
internal fun analyzeVideoNoteFrameTiming(
    timestampsUs: List<Long>,
    declaredFps: Int? = null,
    targetFps: Int? = null
): VideoNoteFrameTiming {
    if (timestampsUs.isEmpty()) return VideoNoteFrameTiming()
    // Extraction order is not presentation order. Missing frames must be estimated from sorted
    // timestamps, otherwise an out-of-order sample creates a false gap that hides a frame present
    // later in the list. Reordering itself is still counted in the original order.
    val presentation = timestampsUs.sorted()
    var repeated = 0
    var reordered = 0
    for (index in 1 until timestampsUs.size) {
        val delta = timestampsUs[index] - timestampsUs[index - 1]
        when {
            delta == 0L -> repeated++
            delta < 0L -> reordered++
        }
    }
    val presentationFirst = presentation.first()
    val presentationLast = presentation.last()
    if (timestampsUs.size < 2 || presentationLast <= presentationFirst) {
        return VideoNoteFrameTiming(
            sampleCount = timestampsUs.size,
            coveredMs = if (presentationLast > presentationFirst)
                (presentationLast - presentationFirst) / 1000.0 else null,
            timingSource = VideoNoteTimingSource.UNKNOWN,
            repeatedTimestamps = repeated,
            reorderedTimestamps = reordered
        )
    }
    val gapsUs = ArrayList<Long>(presentation.size - 1)
    for (index in 1 until presentation.size) {
        val delta = presentation[index] - presentation[index - 1]
        if (delta > 0L) gapsUs += delta
    }
    val p50 = videoNoteGapPercentile(gapsUs, 0.5)
    val p95 = videoNoteGapPercentile(gapsUs, 0.95)
    val maxGap = gapsUs.maxOrNull()?.let { it / 1000.0 }
    val coveredMs = (presentationLast - presentationFirst) / 1000.0

    val fileFps = declaredFps?.takeIf { it in 1..240 }
    val requestedFps = targetFps?.takeIf { it in 1..240 }
    val baselineUs = fileFps?.let { 1_000_000.0 / it }
        ?: requestedFps?.let { 1_000_000.0 / it }
    val source = when {
        fileFps != null -> VideoNoteTimingSource.FILE_FPS
        requestedFps != null -> VideoNoteTimingSource.TARGET_FPS
        gapsUs.size >= 3 && p50 != null -> VideoNoteTimingSource.MEDIAN_GAP
        else -> VideoNoteTimingSource.UNKNOWN
    }
    val expectedIntervalMs = baselineUs?.div(1000.0)
        ?: p50.takeIf { source == VideoNoteTimingSource.MEDIAN_GAP }
    // Only a declared/requested rate can be used as a drop baseline. A median-derived baseline
    // would make normal variable-frame-rate delivery look like loss by construction.
    val expectedFrames = baselineUs?.takeIf { it > 0.0 }
        ?.let { interval -> ((presentationLast - presentationFirst) / interval).roundToInt() + 1 }
    val dropped = baselineUs?.takeIf { it > 0.0 && gapsUs.isNotEmpty() }?.let { interval ->
        gapsUs.sumOf { gap ->
            if (gap > interval * 1.5) {
                ((gap / interval).roundToInt() - 1).coerceAtLeast(1)
            } else 0
        }
    }
    return VideoNoteFrameTiming(
        sampleCount = timestampsUs.size,
        coveredMs = coveredMs,
        p50GapMs = p50,
        p95GapMs = p95,
        maxGapMs = maxGap,
        expectedIntervalMs = expectedIntervalMs,
        timingSource = source,
        expectedFrames = expectedFrames,
        estimatedDroppedFrames = dropped,
        repeatedTimestamps = repeated,
        reorderedTimestamps = reordered,
        gapsOver50Ms = gapsUs.count { it > 50_000L }
    )
}

/** Collects the report snapshot for one stage; audio and A/V fields stay null when not measured. */
internal fun videoNoteStageTiming(
    frameCount: Int,
    timing: VideoNoteFrameTiming?,
    audio: VideoNoteAudioStats?,
    sync: VideoNoteAvSync?,
    exportFrameCount: Int? = null
): VideoNoteStageTiming = VideoNoteStageTiming(
    frameCount = frameCount,
    p50GapMs = timing?.p50GapMs,
    p95GapMs = timing?.p95GapMs,
    maxGapMs = timing?.maxGapMs,
    coveredMs = timing?.coveredMs,
    timingSource = timing?.timingSource,
    expectedIntervalMs = timing?.expectedIntervalMs,
    expectedFrames = timing?.expectedFrames,
    estimatedDroppedFrames = timing?.estimatedDroppedFrames,
    repeatedTimestamps = timing?.repeatedTimestamps,
    reorderedTimestamps = timing?.reorderedTimestamps,
    audioSampleCount = audio?.sampleCount,
    audioGapP50Ms = audio?.timing?.p50GapMs,
    audioGapP95Ms = audio?.timing?.p95GapMs,
    audioMaxGapMs = audio?.timing?.maxGapMs,
    audioRepeatedTimestamps = audio?.timing?.repeatedTimestamps,
    avStartOffsetMs = sync?.startOffsetMs,
    avEndOffsetMs = sync?.endOffsetMs,
    avDriftMs = sync?.driftMs,
    avVerdict = sync?.verdict,
    exportFrameCount = exportFrameCount?.takeIf { it > 0 }
)
