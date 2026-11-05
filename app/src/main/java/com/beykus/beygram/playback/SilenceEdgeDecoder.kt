package com.beykus.beygram.playback

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Measures the silence at both ends of a complete local audio file with [MediaExtractor] and [MediaCodec].
 * Only the first and the last [SILENCE_SCAN_MS] are decoded, so the cost does not grow with the track length.
 * Nothing is thrown to the caller. A decode that fails or runs out of time gives null, which is transient and must not
 * be cached. A file without an audio track gives [SilenceEdges.UNKNOWN], which is final.
 */
internal object SilenceEdgeDecoder {
    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val SCAN_US = SILENCE_SCAN_MS * 1_000L

    /** Upper bound for one file, so a stuck codec cannot keep the analysis thread busy. */
    private const val MAX_ANALYSIS_NANOS = 8_000_000_000L

    fun analyze(path: String): SilenceEdges? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            val track = audioTrackIndex(extractor) ?: return SilenceEdges.UNKNOWN
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return SilenceEdges.UNKNOWN
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val deadline = System.nanoTime() + MAX_ANALYSIS_NANOS
            // Each end is independent: a failure at one end leaves the other end measurable.
            val head = runCatching {
                decode(extractor, format, mime, seekUs = 0L, keepLast = false, deadline = deadline)
            }.getOrNull()
            // Without a duration the end of the track cannot be located, so the trailing edge stays unknown.
            val tail = if (durationUs > 0L) {
                runCatching {
                    decode(
                        extractor,
                        format,
                        mime,
                        seekUs = (durationUs - SCAN_US).coerceAtLeast(0L),
                        keepLast = true,
                        deadline = deadline
                    )
                }.getOrNull()
            } else {
                null
            }
            // A decode that should have run and failed is transient, so nothing is returned that would be cached.
            if (head == null || (durationUs > 0L && tail == null)) return null
            silenceEdgesFromPeaks(head = head, tail = tail, durationMs = durationUs.coerceAtLeast(0L) / 1_000L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun audioTrackIndex(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return index
        }
        return null
    }

    /**
     * Decodes one part of the track. The head stops at the first audible window. The tail reads to the end of the track
     * and keeps its last windows. Returns null when the codec fails or the deadline passes.
     */
    private fun decode(
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
        seekUs: Long,
        keepLast: Boolean,
        deadline: Long
    ): FloatArray? {
        extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val codec = MediaCodec.createDecoderByType(mime)
        return try {
            codec.configure(format, null, null, 0)
            codec.start()
            pump(extractor, codec, keepLast, deadline)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun pump(
        extractor: MediaExtractor,
        codec: MediaCodec,
        keepLast: Boolean,
        deadline: Long
    ): FloatArray? {
        val info = MediaCodec.BufferInfo()
        var meter: SilenceWindowMeter? = null
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var inputDone = false
        while (true) {
            if (System.nanoTime() > deadline) return null
            if (!inputDone) inputDone = queueInput(extractor, codec)
            val outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val output = codec.outputFormat
                    encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                    meter = SilenceWindowMeter(
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                        capacity = SILENCE_SCAN_WINDOWS,
                        keepLast = keepLast
                    )
                }
                outIndex >= 0 -> {
                    val active = meter
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (active != null && buffer != null && info.size > 0) {
                        feed(buffer, info, encoding, active)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return active?.finish()
                    if (active != null && !keepLast && (active.isFull || active.hasAudibleWindow)) return active.finish()
                }
                // INFO_TRY_AGAIN_LATER and INFO_OUTPUT_BUFFERS_CHANGED need no action here.
            }
        }
    }

    /** Queues one compressed sample. Returns true once the end of the track has been queued. */
    private fun queueInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (index < 0) return false
        val buffer = codec.getInputBuffer(index) ?: throw IllegalStateException("codec has no input buffer")
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, extractor.sampleFlags)
        extractor.advance()
        return false
    }

    private fun feed(buffer: ByteBuffer, info: MediaCodec.BufferInfo, encoding: Int, meter: SilenceWindowMeter) {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.nativeOrder())
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                val chunk = FloatArray(floats.remaining())
                floats.get(chunk)
                meter.addFloat(chunk, chunk.size)
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = buffer.asShortBuffer()
                val chunk = ShortArray(shorts.remaining())
                shorts.get(chunk)
                meter.addPcm16(chunk, chunk.size)
            }
            else -> throw IllegalStateException("unsupported PCM encoding $encoding")
        }
    }
}
