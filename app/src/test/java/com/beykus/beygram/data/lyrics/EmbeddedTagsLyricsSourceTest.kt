package com.beykus.beygram.data.lyrics

import com.beykus.beygram.domain.lyrics.LyricsFetchResult
import com.beykus.beygram.domain.lyrics.LyricsSourceId
import com.beykus.beygram.domain.lyrics.LyricsSyncMode
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.music.TrackIdentity
import com.beykus.beygram.playback.PlaybackItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class EmbeddedTagsLyricsSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun readsId3v2UsltLyricsFromMp3Header() = runBlocking {
        val file = tempFolder.newFile("sample.mp3")
        writeId3v2UsltMp3(file, "[00:01.00]Tag line one\n[00:02.00]Tag line two")

        val identity = TrackIdentity.fromPlaybackItem(
            PlaybackItem(
                key = "local|legacy|${file.absolutePath}",
                kind = MediaKind.Audio,
                path = file.absolutePath,
                title = "Sample",
                durationSeconds = 60,
            )
        )
        val source = EmbeddedTagsLyricsSource()
        assertTrue(source.supports(identity))
        val result = source.fetch(identity) as LyricsFetchResult.Found
        assertEquals(LyricsSourceId.EmbeddedTags, result.document.attribution.sourceId)
        assertEquals(LyricsSyncMode.LineSynced, result.document.syncMode)
        assertEquals("Tag line one", result.document.lines[0].text)
        assertFalse(result.document.attribution.isExternalLookup)
    }

    @Test
    fun rejectsSecretMediaForEmbeddedTagReads() {
        val file = tempFolder.newFile("secret.mp3")
        val identity = TrackIdentity.fromPlaybackItem(
            PlaybackItem(
                key = "tg|legacy|chat:1:message:2|Audio|3",
                kind = MediaKind.Audio,
                path = file.absolutePath,
                title = "Secret",
                durationSeconds = 30,
                secret = true,
            )
        )
        assertFalse(EmbeddedTagsLyricsSource().supports(identity))
    }

    private fun writeId3v2UsltMp3(file: File, lyrics: String) {
        val textBytes = lyrics.toByteArray(StandardCharsets.UTF_8)
        val frameBody = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("eng".toByteArray(StandardCharsets.ISO_8859_1))
            write(0) // Empty content descriptor
            write(textBytes)
        }.toByteArray()

        val frameHeader = byteArrayOf(
            'U'.code.toByte(), 'S'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(),
            ((frameBody.size shr 24) and 0xFF).toByte(),
            ((frameBody.size shr 16) and 0xFF).toByte(),
            ((frameBody.size shr 8) and 0xFF).toByte(),
            (frameBody.size and 0xFF).toByte(),
            0, 0,
        )
        val tagSize = frameHeader.size + frameBody.size
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, 0,
            ((tagSize shr 21) and 0x7F).toByte(),
            ((tagSize shr 14) and 0x7F).toByte(),
            ((tagSize shr 7) and 0x7F).toByte(),
            (tagSize and 0x7F).toByte(),
        )
        file.outputStream().use { out ->
            out.write(id3Header)
            out.write(frameHeader)
            out.write(frameBody)
            out.write(ByteArray(32))
        }
    }
}
