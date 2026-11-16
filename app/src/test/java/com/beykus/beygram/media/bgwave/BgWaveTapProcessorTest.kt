package com.beykus.beygram.media.bgwave

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.isWaveCaptureEligible
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class BgWaveTapProcessorTest {
    private data class CapturedChunk(val samples: FloatArray, val frames: Int, val sampleRate: Int)

    private val defaultCloseTap = BgWaveHostTap.closeNativeTap
    private val defaultPushTap = BgWaveHostTap.pushNativeTap

    @Before
    fun setUp() {
        resetHostTap()
    }

    @After
    fun tearDown() {
        resetHostTap()
    }

    private fun resetHostTap() {
        BgWaveHostTap.closeNativeTap = {}
        BgWaveHostTap.close()
        BgWaveHostTap.setCaptureEligible(false)
        BgWaveHostTap.exoSuppressedForBgMix = false
        BgWaveHostTap.closeNativeTap = defaultCloseTap
        BgWaveHostTap.pushNativeTap = defaultPushTap
    }

    private fun newProcessor(
        active: () -> Boolean = { true },
        captured: MutableList<CapturedChunk>,
    ): BgWaveTapProcessor = BgWaveTapProcessor(
        isTapActive = active,
        pushTap = { pcm, frames, sampleRate ->
            captured += CapturedChunk(pcm.copyOf(frames * 2), frames, sampleRate)
        },
    )

    private fun assertPassthrough(input: ByteBuffer, output: ByteBuffer) {
        val inCopy = input.duplicate()
        inCopy.position(0)
        val expected = ByteArray(inCopy.remaining()).also { inCopy.get(it) }
        val actual = ByteArray(output.remaining()).also { output.get(it) }
        assertArrayEquals(expected, actual)
    }

    @Test
    fun pcm8BitUnsignedDecodesAndPassesThrough() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_8BIT))
        processor.flush()

        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x00.toByte())
            put(0x80.toByte())
            put(0xFF.toByte())
            put(0x40.toByte())
            flip()
        }
        processor.queueInput(input.duplicate())
        assertPassthrough(input, processor.output)

        assertEquals(1, captured.size)
        assertEquals(2, captured[0].frames)
        assertEquals(44_100, captured[0].sampleRate)
        assertArrayEquals(
            floatArrayOf(-1.0f, 0.0f, 127f / 128f, -64f / 128f),
            captured[0].samples,
            1e-6f,
        )
    }

    @Test
    fun pcm16BitSignedDecodesAndPassesThrough() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val input = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(Short.MIN_VALUE)
            putShort(0)
            putShort(Short.MAX_VALUE)
            putShort(16_384)
            flip()
        }
        processor.queueInput(input.duplicate())
        assertPassthrough(input, processor.output)

        assertEquals(1, captured.size)
        assertEquals(2, captured[0].frames)
        assertArrayEquals(
            floatArrayOf(-1.0f, 0.0f, 32767f / 32768f, 0.5f),
            captured[0].samples,
            1e-6f,
        )
    }

    @Test
    fun pcm24BitSignedLittleEndianDecodesCorrectly() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_24BIT))
        processor.flush()

        fun ByteBuffer.putInt24LE(value: Int) {
            put((value and 0xFF).toByte())
            put(((value ushr 8) and 0xFF).toByte())
            put(((value ushr 16) and 0xFF).toByte())
        }

        val input = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt24LE(-8_388_608)
            putInt24LE(0)
            putInt24LE(8_388_607)
            putInt24LE(-1)
            putInt24LE(4_194_304)
            putInt24LE(-4_194_304)
            flip()
        }
        processor.queueInput(input.duplicate())
        assertPassthrough(input, processor.output)

        assertEquals(1, captured.size)
        assertEquals(3, captured[0].frames)
        assertEquals(96_000, captured[0].sampleRate)
        assertArrayEquals(
            floatArrayOf(-1.0f, 0.0f, 8_388_607f / 8_388_608f, -1f / 8_388_608f, 0.5f, -0.5f),
            captured[0].samples,
            1e-7f,
        )
    }

    @Test
    fun pcm32BitSignedLittleEndianDecodesCorrectly() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(192_000, 2, C.ENCODING_PCM_32BIT))
        processor.flush()

        val input = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(Int.MIN_VALUE)
            putInt(0)
            putInt(Int.MAX_VALUE)
            putInt(-1_073_741_824)
            flip()
        }
        processor.queueInput(input.duplicate())
        assertPassthrough(input, processor.output)

        assertEquals(1, captured.size)
        assertEquals(2, captured[0].frames)
        assertEquals(192_000, captured[0].sampleRate)
        assertArrayEquals(
            floatArrayOf(-1.0f, 0.0f, Int.MAX_VALUE / 2147483648f, -0.5f),
            captured[0].samples,
            1e-7f,
        )
    }

    @Test
    fun pcmFloatDecodesAndPassesThrough() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val input = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0.25f)
            putFloat(-0.75f)
            putFloat(1.0f)
            putFloat(-1.0f)
            flip()
        }
        processor.queueInput(input.duplicate())
        assertPassthrough(input, processor.output)

        assertEquals(1, captured.size)
        assertArrayEquals(floatArrayOf(0.25f, -0.75f, 1.0f, -1.0f), captured[0].samples, 1e-6f)
    }

    @Test
    fun monoDuplicatesToStereoAndMultiChannelKeepsFirstTwo() {
        val monoCaptured = mutableListOf<CapturedChunk>()
        val mono = newProcessor(captured = monoCaptured)
        mono.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        mono.flush()
        val monoInput = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(16_384)
            putShort(-16_384)
            flip()
        }
        mono.queueInput(monoInput)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, -0.5f, -0.5f), monoCaptured[0].samples, 1e-6f)

        val quadCaptured = mutableListOf<CapturedChunk>()
        val quad = newProcessor(captured = quadCaptured)
        quad.configure(AudioProcessor.AudioFormat(48_000, 4, C.ENCODING_PCM_16BIT))
        quad.flush()
        val quadInput = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(16_384)
            putShort(-16_384)
            putShort(Short.MAX_VALUE)
            putShort(Short.MIN_VALUE)
            flip()
        }
        quad.queueInput(quadInput)
        assertEquals(1, quadCaptured[0].frames)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), quadCaptured[0].samples, 1e-6f)
    }

    @Test
    fun unsupportedEncodingOrInvalidFormatReturnsNotSet() {
        val processor = newProcessor(captured = mutableListOf())
        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_AC3)),
        )
        assertFalse(processor.isActive)

        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            processor.configure(AudioProcessor.AudioFormat(48_000, 0, C.ENCODING_PCM_16BIT)),
        )
        assertFalse(processor.isActive)

        assertEquals(
            AudioProcessor.AudioFormat.NOT_SET,
            processor.configure(AudioProcessor.AudioFormat(0, 2, C.ENCODING_PCM_16BIT)),
        )
        assertFalse(processor.isActive)
    }

    @Test
    fun chunksLargeBuffersAt4096Frames() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val totalFrames = 5000
        val input = ByteBuffer.allocate(totalFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalFrames) {
            input.putShort(16_384)
            input.putShort(-16_384)
        }
        input.flip()

        processor.queueInput(input)
        assertEquals(2, captured.size)
        assertEquals(4096, captured[0].frames)
        assertEquals(904, captured[1].frames)
    }

    @Test
    fun flushAndResetKeepProcessorReusable() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(captured = captured)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT))
        processor.flush()

        val first = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(0x00, 0x00, 0x40, 0x00, 0x00, 0xC0.toByte()))
            flip()
        }
        processor.queueInput(first)
        processor.flush()
        processor.reset()

        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_32BIT))
        processor.flush()
        val second = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(1_073_741_824)
            putInt(-1_073_741_824)
            flip()
        }
        processor.queueInput(second)

        assertEquals(2, captured.size)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), captured[0].samples, 1e-6f)
        assertEquals(44_100, captured[1].sampleRate)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), captured[1].samples, 1e-6f)
    }

    @Test
    fun inactiveTapPassesThroughWithoutPushing() {
        val captured = mutableListOf<CapturedChunk>()
        val processor = newProcessor(active = { false }, captured = captured)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val input = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1234)
            putShort(-5678)
            flip()
        }
        processor.queueInput(input.duplicate())
        assertPassthrough(input, processor.output)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun hostTapPrivacySuppressionAndReopenLifecycle() {
        val closedIds = mutableListOf<Long>()
        val pushedIds = mutableListOf<Long>()
        BgWaveHostTap.closeNativeTap = { id -> closedIds += id }
        BgWaveHostTap.pushNativeTap = { id, _, _, _ ->
            pushedIds += id
            true
        }

        BgWaveHostTap.open(101L)
        assertTrue(BgWaveHostTap.isOpen)
        assertFalse(BgWaveHostTap.isActiveForExo)

        val pcm = floatArrayOf(0.5f, -0.5f)
        BgWaveHostTap.push(pcm, 1, 48_000)
        assertTrue(pushedIds.isEmpty())

        BgWaveHostTap.setCaptureEligible(true)
        assertTrue(BgWaveHostTap.isActiveForExo)
        BgWaveHostTap.push(pcm, 1, 48_000)
        assertEquals(listOf(101L), pushedIds)

        BgWaveHostTap.exoSuppressedForBgMix = true
        assertFalse(BgWaveHostTap.isActiveForExo)
        BgWaveHostTap.push(pcm, 1, 48_000)
        assertEquals(listOf(101L), pushedIds)

        BgWaveHostTap.exoSuppressedForBgMix = false
        BgWaveHostTap.open(202L)
        assertEquals(listOf(101L), closedIds)
        assertEquals(202L, BgWaveHostTap.tapId)

        BgWaveHostTap.push(pcm, 1, 48_000)
        assertEquals(listOf(101L, 202L), pushedIds)

        BgWaveHostTap.close()
        assertEquals(listOf(101L, 202L), closedIds)
        assertFalse(BgWaveHostTap.isOpen)
        assertFalse(BgWaveHostTap.isActiveForExo)
    }

    @Test
    fun isWaveCaptureEligibleBlocksPrivateAndNonMusicButAllowsYandex() {
        fun item(
            key: String = "tg|acc|chat:1:message:2|Audio|7",
            kind: MediaKind = MediaKind.Audio,
            secret: Boolean = false,
            isSecretChat: Boolean = false,
            viewOnce: Boolean = false,
        ) = PlaybackItem(
            key = key,
            kind = kind,
            path = "/music.flac",
            title = "Track",
            durationSeconds = 180,
            secret = secret,
            isSecretChat = isSecretChat,
            viewOnce = viewOnce,
        )

        assertFalse(isWaveCaptureEligible(null))
        assertTrue(isWaveCaptureEligible(item()))
        assertFalse(isWaveCaptureEligible(item(kind = MediaKind.Voice)))
        assertFalse(isWaveCaptureEligible(item(kind = MediaKind.VideoNote)))
        assertFalse(isWaveCaptureEligible(item(secret = true)))
        assertFalse(isWaveCaptureEligible(item(isSecretChat = true)))
        assertFalse(isWaveCaptureEligible(item(viewOnce = true)))
        assertTrue(isWaveCaptureEligible(item(key = "${YANDEX_TRACK_KEY_PREFIX}123456")))
        assertFalse(isWaveCaptureEligible(item(key = "${YANDEX_TRACK_KEY_PREFIX}123456", kind = MediaKind.Voice)))
    }

    private class PassThroughProcessor : BaseAudioProcessor() {
        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
            inputAudioFormat

        override fun queueInput(inputBuffer: ByteBuffer) {
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
        }
    }

    private class FakeAudioProcessorChain(
        private val processors: Array<AudioProcessor>,
    ) : AudioProcessorChain {
        var lastPlaybackParameters: PlaybackParameters? = null
        var lastSkipSilence: Boolean? = null
        var lastPlayoutDuration: Long? = null

        override fun getAudioProcessors(): Array<AudioProcessor> = processors

        override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
            lastPlaybackParameters = playbackParameters
            return PlaybackParameters(1.5f)
        }

        override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
            lastSkipSilence = skipSilenceEnabled
            return !skipSilenceEnabled
        }

        override fun getMediaDuration(playoutDuration: Long): Long {
            lastPlayoutDuration = playoutDuration
            return playoutDuration * 2
        }

        override fun getSkippedOutputFrameCount(): Long = 42L
    }

    @Test
    fun tapChainRunsTapLastAndDelegatesEverythingElse() {
        val first = PassThroughProcessor()
        val second = PassThroughProcessor()
        val delegate = FakeAudioProcessorChain(arrayOf<AudioProcessor>(first, second))
        val tap = newProcessor(captured = mutableListOf())
        val chain = BgWaveTapProcessorChain(tap, delegate)

        val processors = chain.getAudioProcessors()
        assertEquals(3, processors.size)
        assertSame(first, processors[0])
        assertSame(second, processors[1])
        assertSame(tap, processors[2])
        assertSame(processors, chain.getAudioProcessors())

        val requested = PlaybackParameters(1.25f)
        assertEquals(PlaybackParameters(1.5f), chain.applyPlaybackParameters(requested))
        assertEquals(requested, delegate.lastPlaybackParameters)

        assertFalse(chain.applySkipSilenceEnabled(true))
        assertEquals(true, delegate.lastSkipSilence)

        assertEquals(2_000L, chain.getMediaDuration(1_000L))
        assertEquals(1_000L, delegate.lastPlayoutDuration)
        assertEquals(42L, chain.getSkippedOutputFrameCount())
    }
}
