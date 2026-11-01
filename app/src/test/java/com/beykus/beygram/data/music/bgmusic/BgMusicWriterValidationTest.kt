package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class BgMusicWriterValidationTest {

    /** Asserts the writer throws IllegalArgumentException and leaves the output completely empty. */
    private fun assertRejected(manifest: BgMusicManifest, cover: ByteArray? = null) {
        val out = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { BgMusicWriter.write(manifest, cover, out) }
        assertEquals("invalid input must never produce partial output", 0, out.size())
    }

    @Test
    fun emptyOrControlOnlyPlaylistTitleIsRejected() {
        assertRejected(testManifest(title = ""))
        assertRejected(testManifest(title = "  \u0000\u0007  "))
    }

    @Test
    fun malformedPlaylistIdIsRejected() {
        assertRejected(testManifest().copy(playlistId = "not-a-uuid"))
        assertRejected(testManifest().copy(playlistId = "6f1d2c3a4b5e4f608a7b9c0d1e2f3a4b"))
    }

    @Test
    fun negativeRevisionOrTimestampIsRejected() {
        assertRejected(testManifest(revision = -1L))
        assertRejected(testManifest().copy(updatedAtMs = -1L))
        assertRejected(testManifest().copy(updatedAtMs = BgMusicFormat.MAX_UPDATED_AT_MS + 1))
    }

    @Test
    fun unsupportedFormatVersionsAreRejected() {
        assertRejected(testManifest().copy(formatVersion = 2))
        assertRejected(testManifest().copy(minReaderVersion = 2))
    }

    @Test
    fun duplicateTrackIdsAreRejected() {
        assertRejected(testManifest(tracks = listOf(testTrack(1), testTrack(1))))
    }

    @Test
    fun tooManyTracksAreRejected() {
        val tracks = (1..BgMusicFormat.MAX_TRACKS + 1).map { testTrack(it) }
        assertRejected(testManifest(tracks = tracks))
    }

    @Test
    fun manifestLargerThanOneMebibyteIsRejected() {
        // 5000 tracks with maximal text are about 4 MB of JSON, far above the 1 MiB manifest limit.
        val heavy = (1..BgMusicFormat.MAX_TRACKS).map {
            testTrack(it, title = "t".repeat(256)).copy(artist = "a".repeat(256), album = "b".repeat(256))
        }
        assertRejected(testManifest(tracks = heavy))
    }

    @Test
    fun invalidTrackFieldsAreRejected() {
        assertRejected(testManifest(tracks = listOf(testTrack(1).copy(id = "bad"))))
        assertRejected(testManifest(tracks = listOf(testTrack(1, title = "\u0000 \u0007"))))
        assertRejected(testManifest(tracks = listOf(testTrack(1).copy(durationSec = -1L))))
        assertRejected(testManifest(tracks = listOf(testTrack(1).copy(durationSec = BgMusicFormat.MAX_DURATION_SEC + 1))))
    }

    @Test
    fun invalidAudioHashIsRejected() {
        assertRejected(testManifest(tracks = listOf(testTrack(1).copy(audioHash = "md5:abc"))))
        assertRejected(testManifest(tracks = listOf(testTrack(1).copy(audioHash = "sha256:" + "AB".repeat(32)))))
    }

    @Test
    fun invalidTelegramSourcesAreRejected() {
        val source = { username: String, messageId: Long ->
            testManifest(tracks = listOf(testTrack(1).copy(sources = listOf(BgMusicSourceHint.TelegramPublic(username, messageId)))))
        }
        assertRejected(source("ab", 5L))
        assertRejected(source("1starts_with_digit", 5L))
        assertRejected(source("valid_name", 0L))
        assertRejected(source("valid_name", BgMusicFormat.MAX_TELEGRAM_MESSAGE_ID + 1))
    }

    @Test
    fun tooManySourcesForOneTrackAreRejected() {
        val sources = (1..BgMusicFormat.MAX_SOURCES_PER_TRACK + 1).map {
            BgMusicSourceHint.TelegramPublic("valid_name", it.toLong())
        }
        assertRejected(testManifest(tracks = listOf(testTrack(1).copy(sources = sources))))
    }

    @Test
    fun emptyAuthorNameIsRejected() {
        assertRejected(testManifest(author = BgMusicAuthor("   ")))
    }

    @Test
    fun coverThatIsNotAnAllowedImageIsRejected() {
        assertRejected(testManifest(), cover = "GIF89a".toByteArray())
        assertRejected(testManifest(), cover = ByteArray(0))
    }

    @Test
    fun coverLargerThanOneMebibyteIsRejected() {
        assertRejected(testManifest(), cover = PNG_MAGIC + ByteArray(1024 * 1024 + 1 - PNG_MAGIC.size))
    }
}
