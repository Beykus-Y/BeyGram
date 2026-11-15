package com.beykus.beygram.media.bgwave

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Passes ExoPlayer's decoded PCM through unchanged and, while a BG Wave host tap is open, also copies it to the tap
 * as interleaved stereo float at the track's own sample rate. Mono is duplicated, and more than two channels keep the
 * first two. Runs on the render thread, so it allocates only when the chunk buffer is first needed.
 */
@UnstableApi
internal class BgWaveTapProcessor(
    private val isTapActive: () -> Boolean = { BgWaveHostTap.isActiveForExo },
    private val pushTap: (FloatArray, Int, Int) -> Unit = BgWaveHostTap::push,
) : BaseAudioProcessor() {
    private var encoding = C.ENCODING_PCM_16BIT
    private var channels = 2
    private var sampleRate = 48_000
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val sampleBytes = bytesPerSample(inputAudioFormat.encoding)
        if (sampleBytes == 0 || inputAudioFormat.channelCount < 1 || inputAudioFormat.sampleRate <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun onFlush() {
        // Keep scratch allocated across flushes/seeks to avoid render-thread allocations.
    }

    override fun onReset() {
        encoding = C.ENCODING_PCM_16BIT
        channels = 2
        sampleRate = 48_000
        // Keep scratch reusable across track reconfigurations.
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (isTapActive()) publish(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        replaceOutputBuffer(bytes).put(inputBuffer).flip()
    }

    private fun publish(buffer: ByteBuffer) {
        val sampleBytes = bytesPerSample(encoding)
        if (sampleBytes == 0 || channels < 1) return
        val frameBytes = sampleBytes * channels
        var remaining = buffer.remaining() / frameBytes
        if (remaining == 0) return
        var base = buffer.position()
        if (scratch.size < CHUNK_FRAMES * 2) scratch = FloatArray(CHUNK_FRAMES * 2)
        while (remaining > 0) {
            if (!isTapActive()) return
            val count = minOf(remaining, CHUNK_FRAMES)
            for (f in 0 until count) {
                val frameOffset = base + f * frameBytes
                val left = sampleAt(buffer, frameOffset, 0, sampleBytes)
                val right = if (channels > 1) sampleAt(buffer, frameOffset, 1, sampleBytes) else left
                scratch[f * 2] = left
                scratch[f * 2 + 1] = right
            }
            pushTap(scratch, count, sampleRate)
            base += count * frameBytes
            remaining -= count
        }
    }

    private fun sampleAt(buffer: ByteBuffer, frameOffset: Int, channel: Int, sampleBytes: Int): Float {
        val at = frameOffset + channel * sampleBytes
        return when (encoding) {
            C.ENCODING_PCM_8BIT -> ((buffer.get(at).toInt() and 0xFF) - 128) / 128f
            C.ENCODING_PCM_16BIT -> buffer.getShort(at) / 32768f
            C.ENCODING_PCM_24BIT -> {
                val b0 = buffer.get(at).toInt() and 0xFF
                val b1 = buffer.get(at + 1).toInt() and 0xFF
                val b2 = buffer.get(at + 2).toInt()
                ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
            }
            C.ENCODING_PCM_32BIT -> buffer.getInt(at) / 2147483648f
            C.ENCODING_PCM_FLOAT -> buffer.getFloat(at)
            else -> 0f
        }
    }

    private fun bytesPerSample(pcmEncoding: Int): Int = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private companion object {
        const val CHUNK_FRAMES = 4096
    }
}

/** Renderers that route the decoded PCM through [BgWaveTapProcessor] before it reaches the audio track. */
@UnstableApi
internal class BgWaveRenderersFactory(
    context: Context,
    private val tapEnabled: Boolean = true,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .apply {
            if (tapEnabled) setAudioProcessors(arrayOf<AudioProcessor>(BgWaveTapProcessor()))
        }
        .build()
}
