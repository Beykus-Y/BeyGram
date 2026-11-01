package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryJsonTest {
    private val emojiTitle = "🎧 Любимые 🎶 — «кавычки» \"двойные\" back\\slash\tтаб\nновая строка"

    private fun sampleLibrary(): MusicLibrary {
        val tagged = PlaylistTrack(
            itemId = "item-1",
            title = emojiTitle,
            performer = "Исполнитель 🎤",
            album = "Альбом",
            durationSeconds = 245,
            origin = PlaybackOrigin(chatId = -1001234567890L, messageId = 4_294_967_296L, accountId = TEST_ACCOUNT),
            addedAt = 1_700_000_000_000L
        )
        val hashed = PlaylistTrack(
            itemId = "item-2",
            title = "🎵".repeat(MUSIC_MAX_TITLE_CHARS),
            performer = "",
            album = "",
            durationSeconds = 0,
            origin = null,
            addedAt = 5L,
            audioHash = "abc123"
        )
        val playlist = MusicPlaylist(
            id = "playlist-1",
            title = emojiTitle,
            description = "описание\n" + "x".repeat(900),
            coverPath = "/data/user/0/app/files/music_covers/cover.jpg",
            tracks = listOf(tagged, hashed),
            createdAt = 10L,
            updatedAt = 20L,
            revision = 7L,
            sourcePlaylistId = "import-source",
            sortMode = MusicSortMode.Title
        )
        val stats = MusicListenStats(
            track = tagged.toTrackRef(),
            playCount = 3,
            lastPlayedAt = 30L,
            firstAddedAt = 10L
        )
        return MusicLibrary(
            accountId = TEST_ACCOUNT,
            playlists = listOf(favoritesPlaylist(1L), playlist),
            stats = mapOf(stats.trackKey to stats),
            recent = listOf(MusicHistoryEntry(stats.trackKey, 30L))
        )
    }

    @Test
    fun roundTripKeepsUnicodeEmojiLongTitlesAndAllFields() {
        val original = sampleLibrary()
        val decoded = MusicLibraryJson.decode(MusicLibraryJson.encode(original))
        assertEquals(MusicLibraryDecodeResult.Decoded(original), decoded)
    }

    @Test
    fun encodedDocumentCarriesTheSchemaVersionAndAccount() {
        val json = JSONObject(MusicLibraryJson.encode(sampleLibrary()))
        assertEquals(MUSIC_LIBRARY_SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals(TEST_ACCOUNT, json.getString("accountId"))
    }

    @Test
    fun newerSchemaIsReportedAsUnsupportedAndNotDecoded() {
        val text = """{"schemaVersion": 2, "accountId": "acc", "playlists": []}"""
        assertEquals(MusicLibraryDecodeResult.UnsupportedVersion(2), MusicLibraryJson.decode(text))
    }

    @Test
    fun missingOrInvalidSchemaVersionIsCorrupt() {
        assertTrue(MusicLibraryJson.decode("""{"accountId": "acc", "playlists": []}""") is MusicLibraryDecodeResult.Corrupt)
        assertTrue(MusicLibraryJson.decode("""{"schemaVersion": 0, "accountId": "acc"}""") is MusicLibraryDecodeResult.Corrupt)
        assertTrue(MusicLibraryJson.decode("""{"schemaVersion": "one", "accountId": "acc"}""") is MusicLibraryDecodeResult.Corrupt)
    }

    @Test
    fun brokenDocumentIsCorrupt() {
        assertTrue(MusicLibraryJson.decode("{not json") is MusicLibraryDecodeResult.Corrupt)
        assertTrue(MusicLibraryJson.decode("") is MusicLibraryDecodeResult.Corrupt)
        assertTrue(MusicLibraryJson.decode("""{"schemaVersion": 1, "accountId": ""}""") is MusicLibraryDecodeResult.Corrupt)
    }

    @Test
    fun malformedEntriesAreSkippedAndDuplicatesDropped() {
        val text = """
            {"schemaVersion": 1, "accountId": "acc", "playlists": [
              {"id": "p1", "title": "Good", "tracks": [
                {"itemId": "t1", "title": "ok", "addedAt": 1},
                {"title": "no id"},
                {"itemId": "t1", "title": "same id again", "addedAt": 3},
                {"itemId": "t2", "title": "ok two", "addedAt": 2}
              ]},
              {"title": "no id playlist"},
              {"id": "p1", "title": "duplicate id"}
            ]}
        """.trimIndent()
        val decoded = MusicLibraryJson.decode(text) as MusicLibraryDecodeResult.Decoded
        assertEquals(listOf("p1"), decoded.library.playlists.map { it.id })
        assertEquals("Good", decoded.library.playlists.single().title)
        assertEquals(listOf("t1", "t2"), decoded.library.playlists.single().tracks.map { it.itemId })
    }

    @Test
    fun nullOptionalFieldsStayNullInsteadOfTheTextNull() {
        val text = """
            {"schemaVersion": 1, "accountId": "acc", "playlists": [
              {"id": "p", "title": "T", "coverPath": null, "sourcePlaylistId": null,
               "tracks": [{"itemId": "i", "title": "x", "origin": null, "audioHash": null, "addedAt": 0}]}
            ]}
        """.trimIndent()
        val playlist = (MusicLibraryJson.decode(text) as MusicLibraryDecodeResult.Decoded).library.playlists.single()
        assertNull(playlist.coverPath)
        assertNull(playlist.sourcePlaylistId)
        assertNull(playlist.tracks.single().origin)
        assertNull(playlist.tracks.single().audioHash)
    }

    @Test
    fun missingOptionalFieldsGetDefaults() {
        val text = """
            {"schemaVersion": 1, "accountId": "acc", "playlists": [{"id": "p", "title": "Only title"}]}
        """.trimIndent()
        val playlist = (MusicLibraryJson.decode(text) as MusicLibraryDecodeResult.Decoded).library.playlists.single()
        assertEquals("", playlist.description)
        assertEquals(MusicSortMode.Manual, playlist.sortMode)
        assertEquals(0L, playlist.revision)
        assertTrue(playlist.tracks.isEmpty())
        assertFalse(playlist.isFavorites)
    }
}
