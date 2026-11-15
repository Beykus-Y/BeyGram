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
internal class BgWaveTapProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_PCM_16BIT
    private var channels = 2
    private var sampleRate = 48_000
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported || inputAudioFormat.channelCount < 1) return AudioProcessor.AudioFormat.NOT_SET
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (BgWaveHostTap.isOpen) publish(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        replaceOutputBuffer(bytes).put(inputBuffer).flip()
    }

    private fun publish(buffer: ByteBuffer) {
        val sampleBytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = sampleBytes * channels
        var remaining = buffer.remaining() / frameBytes
        var base = buffer.position()
        if (scratch.size < CHUNK_FRAMES * 2) scratch = FloatArray(CHUNK_FRAMES * 2)
        while (remaining > 0) {
            val count = minOf(remaining, CHUNK_FRAMES)
            for (f in 0 until count) {
                val frameOffset = base + f * frameBytes
                scratch[f * 2] = sampleAt(buffer, frameOffset, 0, sampleBytes)
                scratch[f * 2 + 1] = sampleAt(buffer, frameOffset, minOf(1, channels - 1), sampleBytes)
            }
            BgWaveHostTap.push(scratch, count, sampleRate)
            base += count * frameBytes
            remaining -= count
        }
    }

    private fun sampleAt(buffer: ByteBuffer, frameOffset: Int, channel: Int, sampleBytes: Int): Float {
        val at = frameOffset + channel * sampleBytes
        return if (sampleBytes == 4) {
            buffer.getFloat(at)
        } else {
            buffer.getShort(at) / 32768f
        }
    }

    private companion object {
        const val CHUNK_FRAMES = 4096
    }
}

/** Renderers that route the decoded PCM through [BgWaveTapProcessor] before it reaches the audio track. */
@UnstableApi
internal class BgWaveRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf<AudioProcessor>(BgWaveTapProcessor()))
        .build()
}
