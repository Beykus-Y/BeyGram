package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaFileState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class GrowingAudioPlaybackTest {
    @Test fun directAudioContainersAreProgressive() {
        listOf("audio/mpeg", "audio/ogg", "audio/opus", "audio/aac", "audio/flac", "audio/webm", "audio/3gpp")
            .forEach { mime -> assertTrue(mime, canPlayAudioProgressively(mime, path = null, availableBytes = 0)) }
    }

    @Test fun unknownContainersStayOnTheFullDownloadFallback() {
        assertFalse(canPlayAudioProgressively("audio/x-unknown", "/tmp/file", 100))
        assertFalse(canPlayAudioProgressively("application/octet-stream", null, 0))
    }

    @Test fun mp4AudioNeedsAFastStartIndexAndAudioCodec() {
        val file = tempMp4(mp4Audio(fastStart = true))
        assertTrue(canPlayAudioProgressively("audio/mp4", file.absolutePath, file.length()))
        assertFalse(canPlayAudioProgressively("audio/mp4", file.absolutePath, 8))
        val end = tempMp4(mp4Audio(fastStart = false))
        assertFalse(canPlayAudioProgressively("audio/m4a", end.absolutePath, end.length()))
    }

    @Test fun completedFileAlwaysUsesItsLocalPath() {
        val state = MediaFileState(1, size = 100, localPath = "/done", downloadPath = "/partial", downloadedPrefixBytes = 40)
        assertEquals("/done", partialAudioPlaybackPath(state, "application/octet-stream"))
    }

    @Test fun readablePartialVoicePathIsEligible() {
        val state = MediaFileState(2, size = 100, downloadPath = "/partial", downloadedPrefixBytes = 40, isDownloading = true)
        assertEquals("/partial", partialAudioPlaybackPath(state, "audio/ogg"))
        assertNull(partialAudioPlaybackPath(state.copy(downloadPath = null), "audio/ogg"))
        assertNull(partialAudioPlaybackPath(state.copy(downloadedPrefixBytes = 0), "audio/ogg"))
        assertNull(partialAudioPlaybackPath(state, "audio/x-unknown"))
    }

    @Test fun growingSourceReadsLivePrefixAndCompletion() {
        val state = MutableStateFlow(
            MediaFileState(3, size = 100, downloadPath = "/partial", downloadedPrefixBytes = 20, isDownloading = true)
        )
        var requested = 0
        val source = growingAudioSource(state.value, 3, "audio/ogg", { state }) { requested++ }
        assertNotNull(source)
        source!!
        assertEquals(100L, source.totalSize)
        assertEquals(20L, source.readablePrefix())
        assertTrue(source.isDownloading())
        assertFalse(source.isComplete())
        state.value = state.value.copy(downloadedPrefixBytes = 60)
        assertEquals(60L, source.readablePrefix())
        source.onNeedMore()
        assertEquals(1, requested)
        state.value = state.value.copy(localPath = "/done")
        assertTrue(source.isComplete())
    }

    @Test fun growingSourceRejectsCompleteOrUnknownFiles() {
        val completed = MediaFileState(4, size = 100, localPath = "/done", downloadPath = "/done", downloadedPrefixBytes = 100)
        assertNull(growingAudioSource(completed, 4, "audio/ogg", { MutableStateFlow(completed) }) {})
        val unknown = MediaFileState(5, size = 100, downloadPath = "/partial", downloadedPrefixBytes = 20, isDownloading = true)
        assertNull(growingAudioSource(unknown, 5, "audio/x-unknown", { MutableStateFlow(unknown) }) {})
    }

    private fun tempMp4(bytes: ByteArray): File = File.createTempFile("audio-probe", ".m4a").apply {
        writeBytes(bytes)
        deleteOnExit()
    }

    private fun mp4Audio(fastStart: Boolean): ByteArray {
        val ftyp = box("ftyp", "M4A ".toByteArray(Charsets.US_ASCII) + ByteArray(4))
        val moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd("mp4a"))))))
        val mdat = box("mdat", ByteArray(16))
        return if (fastStart) concat(ftyp, moov, mdat) else concat(ftyp, mdat, moov)
    }

    private fun stsd(vararg codecs: String): ByteArray {
        val entries = ByteArrayOutputStream()
        codecs.forEach { codec -> entries.write(box(codec, ByteArray(78))) }
        val payload = ByteArrayOutputStream()
        payload.write(ByteArray(4))
        payload.write(uint32(codecs.size.toLong()))
        payload.write(entries.toByteArray())
        return box("stsd", payload.toByteArray())
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(uint32((8 + payload.size).toLong()))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun uint32(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFFL).toByte(),
        ((value shr 16) and 0xFFL).toByte(),
        ((value shr 8) and 0xFFL).toByte(),
        (value and 0xFFL).toByte()
    )
}
