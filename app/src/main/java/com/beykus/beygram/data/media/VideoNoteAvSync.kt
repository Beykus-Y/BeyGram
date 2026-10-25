package com.beykus.beygram.data.media

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer

private const val AUDIO_SAMPLE_BUFFER_BYTES = 512 * 1024

/** Sample timing of the audio track; samples are never decoded. */
internal data class VideoNoteAudioStats(
    val sampleCount: Int,
    val firstUs: Long,
    val lastUs: Long,
    val sampleRate: Int?,
    val channels: Int?,
    val bitrateBps: Int?,
    /** Gap percentiles/repeated timestamps from the audio samples; null without usable timing. */
    val timing: VideoNoteFrameTiming? = null
)

/** Audio against video inside one file (issue #234, §10). Offsets are audio minus video. */
internal data class VideoNoteAvSync(
    val startOffsetMs: Double,
    val endOffsetMs: Double,
    /** How much the offset changed between the start and the end of the clip. */
    val driftMs: Double,
    val verdict: AvSyncVerdict
)

enum class AvSyncVerdict { IN_SYNC, START_OFFSET, DRIFT, NO_AUDIO }

/** Tolerance for a clip to count as in sync: about three frames at 30 FPS. */
internal const val AV_SYNC_TOLERANCE_MS = 100.0

/** Below this clip length the start and end offsets differ only by audio frame granularity, so drift is not judged. */
internal const val AV_DRIFT_MIN_CLIP_US = 1_000_000L

/**
 * Compares first and last sample times. A drift check needs a clip of at least a second, otherwise
 * the offsets are within one audio frame of each other by construction and tell nothing.
 */
internal fun avSyncOf(videoFirstUs: Long, videoLastUs: Long, audio: VideoNoteAudioStats?): VideoNoteAvSync? {
    if (videoLastUs < videoFirstUs) return null
    if (audio == null || audio.sampleCount == 0) return VideoNoteAvSync(0.0, 0.0, 0.0, AvSyncVerdict.NO_AUDIO)
    val start = (audio.firstUs - videoFirstUs) / 1000.0
    val end = (audio.lastUs - videoLastUs) / 1000.0
    val drift = end - start
    val verdict = when {
        kotlin.math.abs(start) > AV_SYNC_TOLERANCE_MS -> AvSyncVerdict.START_OFFSET
        videoLastUs - videoFirstUs >= AV_DRIFT_MIN_CLIP_US && kotlin.math.abs(drift) > AV_SYNC_TOLERANCE_MS -> AvSyncVerdict.DRIFT
        else -> AvSyncVerdict.IN_SYNC
    }
    return VideoNoteAvSync(start, end, drift, verdict)
}

/** Reads audio sample timestamps and the declared format of the first audio track, or null without audio. */
internal fun analyzeVideoNoteAudio(file: File): VideoNoteAudioStats? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        val format = extractor.getTrackFormat(track)
        extractor.selectTrack(track)
        var count = 0
        var first = Long.MAX_VALUE
        var last = Long.MIN_VALUE
        val timestamps = ArrayList<Long>(1024)
        // The end of the track is signalled by readSampleData, not by a negative time: an MP4 written with an
        // edit list (the exported file) can start its audio before zero, and that must not read as "no audio".
        val buffer = ByteBuffer.allocate(AUDIO_SAMPLE_BUFFER_BYTES)
        while (count < 1_000_000) {
            if (extractor.readSampleData(buffer, 0) < 0) break
            val pts = extractor.sampleTime
            first = minOf(first, pts)
            last = maxOf(last, pts)
            timestamps += pts
            count++
            if (!extractor.advance()) break
        }
        // A track that is present but yields no samples is reported as such, not as a missing track.
        return VideoNoteAudioStats(
            sampleCount = count, firstUs = if (count == 0) 0L else first, lastUs = if (count == 0) 0L else last,
            sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else null,
            channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else null,
            bitrateBps = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE) else null,
            timing = analyzeVideoNoteFrameTiming(timestamps)
        )
    } finally {
        extractor.release()
    }
}
