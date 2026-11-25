package com.beykus.beygram.media.audio

import android.media.AudioFormat
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioDetailsProbeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun clearCache() {
        AudioDetailsProbe.clearCacheForTests()
    }

    @Test
    fun mapsMimeTypesContainersBitDepthAndLosslessAccurately() {
        assertEquals("MP3", AudioDetailsProbe.mimeToCodecLabel("audio/mpeg"))
        assertEquals("AAC", AudioDetailsProbe.mimeToCodecLabel("audio/mp4a-latm"))
        assertEquals("Opus", AudioDetailsProbe.mimeToCodecLabel("audio/opus"))
        assertEquals("FLAC", AudioDetailsProbe.mimeToCodecLabel("audio/flac"))

        assertEquals("MP4 / M4A", AudioDetailsProbe.extensionToContainerLabel("m4a"))
        assertEquals("Ogg", AudioDetailsProbe.extensionToContainerLabel("ogg"))
        assertEquals("FLAC", AudioDetailsProbe.extensionToContainerLabel("flac"))
        assertNull(AudioDetailsProbe.extensionToContainerLabel(""))

        assertEquals(16, AudioDetailsProbe.pcmEncodingToBitDepth(AudioFormat.ENCODING_PCM_16BIT))
        assertEquals(24, AudioDetailsProbe.pcmEncodingToBitDepth(AudioFormat.ENCODING_PCM_24BIT_PACKED))
        assertEquals(32, AudioDetailsProbe.pcmEncodingToBitDepth(AudioFormat.ENCODING_PCM_FLOAT))
        assertNull(AudioDetailsProbe.pcmEncodingToBitDepth(-1))

        assertTrue(AudioDetailsProbe.isLosslessCodec("audio/flac"))
        assertTrue(AudioDetailsProbe.isLosslessCodec("audio/alac"))
        assertFalse(AudioDetailsProbe.isLosslessCodec("audio/mpeg"))

        assertEquals("48 kHz", AudioDetailsProbe.formatSampleRateKhz(48_000))
        assertEquals("44.1 kHz", AudioDetailsProbe.formatSampleRateKhz(44_100))
    }

    @Test
    fun resolvesOriginsAndHandlesMissingStreamingFileWithoutFabricatingFields() = runBlocking {
        val yandex = PlaybackItem(
            key = "ym:123",
            kind = MediaKind.Audio,
            path = "",
            title = "Stream",
            durationSeconds = 200,
        )
        val yandexDetails = AudioDetailsProbe.probe(yandex)
        assertEquals(AudioSourceOrigin.YandexMusic, yandexDetails.sourceOrigin)
        assertFalse(yandexDetails.fileAvailable)
        assertNull(yandexDetails.codec)
        assertNull(yandexDetails.bitrateKbps)
        assertNull(yandexDetails.sampleRateHz)
        assertNull(yandexDetails.bitDepth)

        val tgFile = tempFolder.newFile("voice.ogg").apply {
            writeBytes(ByteArray(40_000) { 1 })
        }
        val telegram = PlaybackItem(
            key = "tg:1:2",
            kind = MediaKind.Audio,
            path = tgFile.absolutePath,
            title = "Voice",
            durationSeconds = 10,
            origin = PlaybackOrigin(chatId = 1L, messageId = 2L),
        )
        val tgDetails = AudioDetailsProbe.probe(telegram)
        assertEquals(AudioSourceOrigin.Telegram, tgDetails.sourceOrigin)
        assertTrue(tgDetails.fileAvailable)
        assertEquals("Ogg", tgDetails.container)
        // MediaExtractor cannot parse synthetic zeroed bytes, so bitrate falls back to honest estimate:
        // 40_000 * 8 / 10_000 = 32 kbps (estimated)
        assertEquals(32, tgDetails.bitrateKbps)
        assertTrue(tgDetails.bitrateEstimated)
        assertEquals(40_000L, tgDetails.fileSizeBytes)
        assertNull(tgDetails.bitDepth)
    }

    @Test
    fun hidesFileSizeForPrivateMedia() = runBlocking {
        val secretFile = tempFolder.newFile("secret.mp3").apply {
            writeBytes(ByteArray(16_000) { 2 })
        }
        val secretItem = PlaybackItem(
            key = "tg:secret",
            kind = MediaKind.Audio,
            path = secretFile.absolutePath,
            title = "Secret",
            durationSeconds = 8,
            secret = true,
        )
        val details = AudioDetailsProbe.probe(secretItem)
        assertNull(details.fileSizeBytes)
    }
}
