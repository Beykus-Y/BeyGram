package com.beykus.beygram.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import com.beykus.beygram.R
import java.io.File
import java.util.Locale

/**
 * Reads encoded video sample presentation timestamps. No pixels or audio are decoded or retained.
 * The measurement is performed only for opt-in diagnostics, off the main thread.
 */
internal data class VideoNoteFrameStats(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val measuredFps: Double?,
    val declaredFps: Int?,
    val maximumPositiveFrameGapMs: Double,
    val gapsOver50Ms: Int,
    val fileBytes: Long,
    val codec: String,
    val medianGapMs: Double? = null,
    val p95GapMs: Double? = null,
    val duplicateOrReorderedPts: Int = 0,
    val fpsWindows: List<Int> = emptyList(),
    val bitrateBps: Int? = null,
    val audioCodec: String? = null,
    val audioDurationUs: Long? = null,
    val videoDurationUs: Long? = null,
    val codecProfile: Int? = null,
    val codecLevel: Int? = null,
    val firstVideoUs: Long = 0L,
    val lastVideoUs: Long = 0L
) {
    /** Localized one-line summary for reports shown in the app. */
    fun summary(context: Context): String {
        fun f(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "?"
        val avDelta = if (audioDurationUs != null && videoDurationUs != null) ((audioDurationUs - videoDurationUs) / 1000).toString() else "?"
        return context.getString(
            R.string.vn_frame_summary, width, height, frameCount,
            measuredFps?.let { String.format(Locale.US, "%.2f", it) } ?: "?", declaredFps?.toString() ?: "?",
            f(medianGapMs), f(p95GapMs), f(maximumPositiveFrameGapMs), gapsOver50Ms, duplicateOrReorderedPts,
            fpsWindows.joinToString(" "), codec, codecProfile?.toString() ?: "?", codecLevel?.toString() ?: "?",
            bitrateBps?.toString() ?: "?", audioCodec ?: context.getString(R.string.camera_report_none), avDelta, fileBytes
        )
    }
}

internal fun measuredVideoFps(sampleCount: Int, earliestUs: Long, latestUs: Long): Double? =
    if (sampleCount < 2 || latestUs <= earliestUs) null
    else (sampleCount - 1).toDouble() * 1_000_000.0 / (latestUs - earliestUs)

/** Bitrate from the encoded sample sizes; containers rarely declare one, so "?" would hide a real number. */
internal fun measuredVideoBitrateBps(videoBytes: Long, sampleCount: Int, earliestUs: Long, latestUs: Long): Int? {
    if (sampleCount < 2 || latestUs <= earliestUs || videoBytes <= 0L) return null
    val spanUs = (latestUs - earliestUs) * sampleCount.toDouble() / (sampleCount - 1)
    return (videoBytes * 8.0 * 1_000_000.0 / spanUs).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Timestamp interval percentiles use measured sample timing, not nominal metadata. */
internal fun videoNoteGapPercentile(gapsUs: List<Long>, percentile: Double): Double? {
    if (gapsUs.isEmpty()) return null
    val sorted = gapsUs.sorted()
    val index = kotlin.math.ceil((sorted.size - 1) * percentile.coerceIn(0.0, 1.0)).toInt()
    return sorted[index] / 1000.0
}

internal fun analyzeVideoNoteFrameSamples(file: File): VideoNoteFrameStats {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: error("В файле нет видеодорожки")
        val format = extractor.getTrackFormat(track)
        extractor.selectTrack(track)

        var count = 0
        var firstUs = Long.MAX_VALUE
        var lastUs = Long.MIN_VALUE
        var previousUs = -1L
        var maxGapUs = 0L
        var gapsOver50Ms = 0
        var nonIncreasingPts = 0
        var videoBytes = 0L
        val gaps = ArrayList<Long>()
        val frameBuckets = linkedMapOf<Long, Int>()
        val audioFormat = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }?.let(extractor::getTrackFormat)
        // Up to ~83 minutes at 60fps. Avoid unbounded work on a malformed file.
        while (count < 300_000) {
            val ptsUs = extractor.sampleTime
            if (ptsUs < 0L) break
            firstUs = minOf(firstUs, ptsUs)
            lastUs = maxOf(lastUs, ptsUs)
            if (previousUs >= 0 && ptsUs > previousUs) {
                val gap = ptsUs - previousUs
                maxGapUs = maxOf(maxGapUs, gap)
                if (gap > 50_000L) gapsOver50Ms++
                gaps += gap
            } else if (previousUs >= 0L) nonIncreasingPts++
            frameBuckets[ptsUs / 1_000_000L] = (frameBuckets[ptsUs / 1_000_000L] ?: 0) + 1
            previousUs = ptsUs
            videoBytes += extractor.sampleSize.coerceAtLeast(0L)
            count++
            if (!extractor.advance()) break
        }
        return VideoNoteFrameStats(
            width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0,
            height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0,
            frameCount = count,
            measuredFps = if (count > 1) measuredVideoFps(count, firstUs, lastUs) else null,
            declaredFps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else null,
            maximumPositiveFrameGapMs = maxGapUs / 1000.0,
            gapsOver50Ms = gapsOver50Ms,
            fileBytes = file.length(),
            codec = format.getString(MediaFormat.KEY_MIME) ?: "unknown",
            medianGapMs = videoNoteGapPercentile(gaps, 0.5),
            p95GapMs = videoNoteGapPercentile(gaps, 0.95),
            duplicateOrReorderedPts = nonIncreasingPts,
            fpsWindows = frameBuckets.values.take(12),
            bitrateBps = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE)
                else measuredVideoBitrateBps(videoBytes, count, firstUs, lastUs),
            audioCodec = audioFormat?.getString(MediaFormat.KEY_MIME),
            audioDurationUs = audioFormat?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION),
            videoDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else null,
            codecProfile = if (format.containsKey(MediaFormat.KEY_PROFILE)) format.getInteger(MediaFormat.KEY_PROFILE) else null,
            codecLevel = if (format.containsKey(MediaFormat.KEY_LEVEL)) format.getInteger(MediaFormat.KEY_LEVEL) else null,
            firstVideoUs = if (count > 0) firstUs else 0L,
            lastVideoUs = if (count > 0) lastUs else 0L
        )
    } finally {
        extractor.release()
    }
}
