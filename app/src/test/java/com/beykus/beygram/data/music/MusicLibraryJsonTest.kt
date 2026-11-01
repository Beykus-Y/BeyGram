package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import org.json.JSONArray
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

    /** A document with the given playlists array and nothing else but the required fields. */
    private fun documentWith(playlists: JSONArray): String =
        JSONObject().put("schemaVersion", 1).put("accountId", TEST_ACCOUNT).put("playlists", playlists).toString()

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
    fun missingOrNonArrayPlaylistsIsCorrupt() {
        assertTrue(MusicLibraryJson.decode("""{"schemaVersion": 1, "accountId": "acc"}""") is MusicLibraryDecodeResult.Corrupt)
        assertTrue(
            MusicLibraryJson.decode("""{"schemaVersion": 1, "accountId": "acc", "playlists": null}""") is MusicLibraryDecodeResult.Corrupt
        )
        assertTrue(
            MusicLibraryJson.decode("""{"schemaVersion": 1, "accountId": "acc", "playlists": {}}""") is MusicLibraryDecodeResult.Corrupt
        )
    }

    @Test
    fun malformedEntriesAreSkippedCountedAndDuplicatesDropped() {
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
        // Two tracks (one without an id, one repeated id) and two playlists (one without an id, one repeated id).
        assertEquals(4, decoded.skippedCount)
    }

    @Test
    fun aSectionOfTheWrongTypeIsCountedAsSkipped() {
        val text = """
            {"schemaVersion": 1, "accountId": "acc", "playlists": [{"id": "p", "title": "T", "tracks": 5}], "stats": "oops"}
        """.trimIndent()
        val decoded = MusicLibraryJson.decode(text) as MusicLibraryDecodeResult.Decoded
        assertTrue(decoded.library.playlists.single().tracks.isEmpty())
        assertEquals(2, decoded.skippedCount)
    }

    @Test
    fun playlistsBeyondTheLimitAreSkippedAndOneSlotStaysFreeForFavorites() {
        val playlists = JSONArray()
        repeat(MUSIC_MAX_PLAYLISTS + 2) { index ->
            playlists.put(JSONObject().put("id", "p$index").put("title", "List $index"))
        }
        val decoded = MusicLibraryJson.decode(documentWith(playlists)) as MusicLibraryDecodeResult.Decoded
        assertEquals(MUSIC_MAX_PLAYLISTS - 1, decoded.library.playlists.size)
        assertEquals(3, decoded.skippedCount)
    }

    @Test
    fun aFullLibraryWithFavoritesDecodesWithoutSkips() {
        val playlists = listOf(favoritesPlaylist(1L)) +
            (1 until MUSIC_MAX_PLAYLISTS).map { MusicPlaylist(id = "p$it", title = "List $it") }
        val text = MusicLibraryJson.encode(MusicLibrary(accountId = TEST_ACCOUNT, playlists = playlists))
        val decoded = MusicLibraryJson.decode(text) as MusicLibraryDecodeResult.Decoded
        assertEquals(MUSIC_MAX_PLAYLISTS, decoded.library.playlists.size)
        assertEquals(0, decoded.skippedCount)
    }

    @Test
    fun tracksBeyondThePerPlaylistLimitAreSkipped() {
        val tracks = JSONArray()
        repeat(MUSIC_MAX_TRACKS_PER_PLAYLIST + 1) { index ->
            tracks.put(JSONObject().put("itemId", "t$index").put("title", "T$index"))
        }
        val playlist = JSONObject().put("id", "p").put("title", "Big").put("tracks", tracks)
        val decoded = MusicLibraryJson.decode(documentWith(JSONArray().put(playlist))) as MusicLibraryDecodeResult.Decoded
        assertEquals(MUSIC_MAX_TRACKS_PER_PLAYLIST, decoded.library.playlists.single().tracks.size)
        assertEquals(1, decoded.skippedCount)
    }

    @Test
    fun statsBeyondTheCapAreEvictedLeastRecentlyPlayedFirstAndCounted() {
        val stats = (1..MUSIC_MAX_STATS + 1).associate { index ->
            val track = MusicTrackRef(trackKey = "item:$index", title = "T$index")
            track.trackKey to MusicListenStats(track = track, playCount = 1, lastPlayedAt = index.toLong())
        }
        val library = MusicLibrary(accountId = TEST_ACCOUNT, playlists = listOf(favoritesPlaylist(1L)), stats = stats)
        val decoded = MusicLibraryJson.decode(MusicLibraryJson.encode(library)) as MusicLibraryDecodeResult.Decoded
        assertEquals(MUSIC_MAX_STATS, decoded.library.stats.size)
        assertFalse(decoded.library.stats.containsKey("item:1"))
        assertTrue(decoded.library.stats.containsKey("item:${MUSIC_MAX_STATS + 1}"))
        assertEquals(1, decoded.skippedCount)
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
