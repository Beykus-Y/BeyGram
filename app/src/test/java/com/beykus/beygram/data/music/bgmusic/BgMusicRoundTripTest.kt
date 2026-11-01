package com.beykus.beygram.data.music.bgmusic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgMusicRoundTripTest {

    @Test
    fun roundTripPreservesEveryFieldAndTrackOrder() {
        val tracks = listOf(
            testTrack(9).copy(
                audioHash = "sha256:" + "ab".repeat(32),
                sources = listOf(BgMusicSourceHint.TelegramPublic("my_channel", 42L)),
            ),
            testTrack(2),
            testTrack(5).copy(durationSec = 0L, artist = "", album = ""),
        )
        val manifest = testManifest(tracks = tracks, author = BgMusicAuthor("Ilya"))

        val result = assertSuccess(readAll(writeToBytes(manifest)))

        assertEquals(manifest, result.manifest)
        assertNull(result.cover)
    }

    @Test
    fun unicodeEmojiAndRightToLeftTextSurviveTheRoundTrip() {
        val titles = listOf(
            "Ночная смесь 🎧",
            "שיר ישן",
            "مرحبا بالعالم",
            "👨\u200D👩\u200D👧 family",
            "🇷🇺 flag pair",
            "é combining accent",
            "mixed \u200F RLM mark",
        )
        for (title in titles) {
            val manifest = testManifest(tracks = listOf(testTrack(1, title = title)), title = title)
            val result = assertSuccess(readAll(writeToBytes(manifest))).manifest
            assertEquals(title, result.title)
            assertEquals(title, result.tracks.single().title)
        }
    }

    @Test
    fun longTextIsCappedAtTheSpecLimits() {
        val manifest = testManifest(
            tracks = listOf(testTrack(1, title = "c".repeat(400)).copy(artist = "x".repeat(300), album = "y".repeat(300))),
            title = "a".repeat(300),
            description = "b".repeat(1500),
        )

        val result = assertSuccess(readAll(writeToBytes(manifest))).manifest

        assertEquals(128, result.title.length)
        assertEquals(1000, result.description.length)
        assertEquals(256, result.tracks.single().title.length)
        assertEquals(256, result.tracks.single().artist.length)
        assertEquals(256, result.tracks.single().album.length)
    }

    @Test
    fun capCountsCodePointsAndNeverSplitsAnEmoji() {
        val emojiTitle = "😀".repeat(200) // 200 code points, 400 UTF-16 units
        val title = assertSuccess(readAll(writeToBytes(testManifest(title = emojiTitle)))).manifest.title

        assertEquals(128, Character.codePointCount(title, 0, title.length))
        assertEquals(256, title.length)
    }

    @Test
    fun controlCharactersAndBidiOverridesAreRemoved() {
        val dirty = "\u0000Mix\u0007 \u202Eevil\u2069\nname\t "

        assertEquals("Mix evilname", BgMusicFormat.sanitizeText(dirty, 128))
        assertEquals("Mix evilname", assertSuccess(readAll(writeToBytes(testManifest(title = dirty)))).manifest.title)
    }

    @Test
    fun unpairedSurrogatesAreDroppedSoTheOutputStaysValidUtf8() {
        assertEquals("abcd", BgMusicFormat.sanitizeText("ab\uD800cd", 128))
        assertEquals("ab😀", BgMusicFormat.sanitizeText("ab😀\uDC00", 128))
    }

    @Test
    fun coverRoundTripsForEveryAllowedImageType() {
        val covers = listOf(
            "cover.png" to (PNG_MAGIC + ByteArray(64) { 1.toByte() }),
            "cover.jpg" to (JPEG_MAGIC + ByteArray(64) { 2.toByte() }),
            "cover.webp" to webpBytes(payload = 64),
        )
        for ((expectedName, bytes) in covers) {
            val archive = writeToBytes(testManifest(), cover = bytes)

            assertEquals(listOf("manifest.json", expectedName), entryNames(archive))
            assertArrayEquals(bytes, assertSuccess(readAll(archive)).cover)
        }
    }

    @Test
    fun coverIsOmittedWhenNotProvided() {
        val archive = writeToBytes(testManifest())

        assertEquals(listOf("manifest.json"), entryNames(archive))
        assertNull(assertSuccess(readAll(archive)).cover)
    }

    @Test
    fun outputIsDeterministicAndManifestComesFirst() {
        val manifest = testManifest(tracks = listOf(testTrack(1), testTrack(2)))
        val first = writeToBytes(manifest, PNG_MAGIC + ByteArray(8))
        val second = writeToBytes(manifest, PNG_MAGIC + ByteArray(8))

        assertArrayEquals(first, second)
        assertEquals("manifest.json", entryNames(first).first())
    }

    @Test
    fun writerNeverWritesNullTextAndOmitsAbsentAuthor() {
        val manifest = testManifest(
            tracks = listOf(testTrack(1).copy(artist = "", album = "")),
            description = "",
            author = null,
        )

        val json = entryText(writeToBytes(manifest), BgMusicFormat.MANIFEST_ENTRY)

        assertFalse("null must never be written as text", json.contains("null"))
        assertFalse(json.contains("\"author\""))
        assertTrue(json.contains("\"description\":\"\""))
    }

    @Test
    fun authorIsWrittenOnlyWhenProvided() {
        val json = entryText(writeToBytes(testManifest(author = BgMusicAuthor("Ilya"))), BgMusicFormat.MANIFEST_ENTRY)

        assertTrue(json.contains("\"author\":{\"name\":\"Ilya\"}"))
        assertEquals(
            BgMusicAuthor("Ilya"),
            assertSuccess(readAll(writeToBytes(testManifest(author = BgMusicAuthor("Ilya"))))).manifest.author,
        )
    }

    @Test
    fun telegramSourceUsernameIsNormalizedAndMessageIdKept() {
        val track = testTrack(1).copy(
            sources = listOf(BgMusicSourceHint.TelegramPublic("@Music_Channel", 77L)),
        )

        val result = assertSuccess(readAll(writeToBytes(testManifest(tracks = listOf(track))))).manifest

        assertEquals(listOf(BgMusicSourceHint.TelegramPublic("Music_Channel", 77L)), result.tracks.single().sources)
    }

    @Test
    fun validAudioHashRoundTrips() {
        val hash = "sha256:" + "0123456789abcdef".repeat(4)
        val track = testTrack(1).copy(audioHash = hash)

        val result = assertSuccess(readAll(writeToBytes(testManifest(tracks = listOf(track))))).manifest

        assertEquals(hash, result.tracks.single().audioHash)
    }

    @Test
    fun fiveThousandTracksRoundTripInOrder() {
        val tracks = (1..BgMusicFormat.MAX_TRACKS).map { testTrack(it) }

        val result = assertSuccess(readAll(writeToBytes(testManifest(tracks = tracks)))).manifest

        assertEquals(tracks, result.tracks)
    }
}
