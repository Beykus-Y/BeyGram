package com.beykus.beygram.data.media

import android.media.MediaExtractor
import android.media.MediaFormat
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
    val codec: String
) {
    fun summary(): String = buildString {
        append("${width}x${height}, samples=$frameCount")
        append(", measuredFPS=${measuredFps?.let { String.format(Locale.US, "%.2f", it) } ?: "n/a"}")
        append(", declaredFPS=${declaredFps ?: "n/a"}")
        append(", largestGapMs=${String.format(Locale.US, "%.1f", maximumPositiveFrameGapMs)}")
        append(", gapsOver50ms=$gapsOver50Ms")
        append(", videoCodec=$codec, bytes=$fileBytes")
    }
}

internal fun measuredVideoFps(sampleCount: Int, earliestUs: Long, latestUs: Long): Double? =
    if (sampleCount < 2 || latestUs <= earliestUs) null
    else (sampleCount - 1).toDouble() * 1_000_000.0 / (latestUs - earliestUs)

/** Returns a stage-specific suspicion, never a claim about a camera's physical capabilities. */
internal fun videoNoteFpsFinding(target: Int, raw: Double?, output: Double?): String? {
    if (raw == null || output == null) return null
    return when {
        raw >= 45.0 && output < raw * 0.8 -> "После Media3 частота кадров существенно снизилась; проверьте перекодирование."
        target >= 50 && raw < target * 0.8 -> "RAW уже содержит меньше кадров, чем запрошено; проверьте CameraX, камеру и освещение."
        else -> "Существенной потери FPS между RAW и готовым MP4 не обнаружено."
    }
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
            }
            previousUs = ptsUs
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
            codec = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
        )
    } finally {
        extractor.release()
    }
}
