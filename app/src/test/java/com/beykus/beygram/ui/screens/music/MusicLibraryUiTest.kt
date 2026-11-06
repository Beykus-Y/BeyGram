package com.beykus.beygram.ui.screens.music

import com.beykus.beygram.data.music.MusicHistoryEntry
import com.beykus.beygram.data.music.MusicLibrary
import com.beykus.beygram.data.music.MusicListenStats
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.MusicTrackRef
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.data.music.favoritesPlaylist
import com.beykus.beygram.data.music.musicTrackKey
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryUiTest {
    private val account = "acc"
    private val day = 24L * 60 * 60 * 1000

    private fun origin(message: Long) = PlaybackOrigin(chatId = 1L, messageId = message, accountId = account)

    private fun track(
        message: Long,
        addedAt: Long = 0L,
        duration: Int = 180,
        itemId: String = "item-$message"
    ) = PlaylistTrack(
        itemId = itemId,
        title = "T$message",
        performer = "P",
        album = "A",
        durationSeconds = duration,
        origin = origin(message),
        addedAt = addedAt
    )

    private fun playlist(id: String, vararg tracks: PlaylistTrack) = MusicPlaylist(id = id, title = id, tracks = tracks.toList())

    private fun stat(message: Long, playCount: Int, lastPlayedAt: Long?): MusicListenStats {
        val ref = MusicTrackRef(
            trackKey = musicTrackKey(origin(message), null, "x"),
            title = "T$message",
            origin = origin(message)
        )
        return MusicListenStats(track = ref, playCount = playCount, lastPlayedAt = lastPlayedAt)
    }

    private fun library(
        playlists: List<MusicPlaylist> = emptyList(),
        stats: List<MusicListenStats> = emptyList(),
        recent: List<MusicHistoryEntry> = emptyList()
    ): MusicLibrary = MusicLibrary(
        accountId = account,
        playlists = listOf(favoritesPlaylist(0L)) + playlists,
        stats = stats.associateBy { it.trackKey },
        recent = recent
    )

    private fun item(path: String): PlaybackItem =
        PlaybackItem(key = path, kind = MediaKind.Audio, path = path, title = "t", durationSeconds = 1)

    @Test fun collectionIdsRoundTripAndStoredIdsAreNotCollections() {
        MusicCollectionKind.entries.forEach { kind ->
            assertTrue(kind.id.startsWith(MUSIC_COLLECTION_ID_PREFIX))
            assertEquals(kind, MusicCollectionKind.fromId(kind.id))
        }
        assertNull(MusicCollectionKind.fromId("1b4e28ba-2fa1-4d1e-8e3a-4c0c3c1f2a10"))
    }

    @Test fun chatCollectionIdsRoundTripAndAreNotOtherCollections() {
        assertEquals(12345L, chatIdOfCollection(chatCollectionId(12345L)))
        assertEquals(-100L, chatIdOfCollection(chatCollectionId(-100L)))
        assertNull(chatIdOfCollection(MusicCollectionKind.Recent.id))
        assertNull(chatIdOfCollection("1b4e28ba-2fa1-4d1e-8e3a-4c0c3c1f2a10"))
        assertNull(chatIdOfCollection(MUSIC_CHAT_COLLECTION_PREFIX))
        assertNull(chatIdOfCollection(MUSIC_CHAT_COLLECTION_PREFIX + "x"))
        assertNull(MusicCollectionKind.fromId(chatCollectionId(7L)))
    }

    @Test fun clockDurationShowsMinutesAndHours() {
        assertEquals("", formatClockDuration(0))
        assertEquals("", formatClockDuration(-4))
        assertEquals("0:05", formatClockDuration(5))
        assertEquals("1:05", formatClockDuration(65))
        assertEquals("1:02:03", formatClockDuration(3723))
    }

    @Test fun totalDurationRoundsPartialMinutesUp() {
        assertEquals(MusicDurationParts(0, 0), durationParts(0))
        assertEquals(MusicDurationParts(0, 1), durationParts(1))
        assertEquals(MusicDurationParts(0, 1), durationParts(60))
        assertEquals(MusicDurationParts(0, 2), durationParts(61))
        assertEquals(MusicDurationParts(1, 0), durationParts(3600))
        assertEquals(MusicDurationParts(1, 12), durationParts(4320))
    }

    @Test fun totalDurationSumsTrackLengths() {
        assertEquals(300L, totalDurationSeconds(listOf(track(1, duration = 120), track(2, duration = 180))))
        assertEquals(0L, totalDurationSeconds(listOf(track(3, duration = -5))))
    }

    @Test fun libraryTracksKeepTheNewestEntryOnceAndSkipUnresolvableOnes() {
        val older = track(1, addedAt = 10L, itemId = "a")
        val newer = track(1, addedAt = 20L, itemId = "b")
        val other = track(2, addedAt = 15L, itemId = "c")
        val imported = PlaylistTrack(
            itemId = "d",
            title = "x",
            performer = "",
            album = "",
            durationSeconds = 1,
            origin = null,
            addedAt = 99L,
            audioHash = "hash"
        )
        val tracks = libraryTracks(
            library(playlists = listOf(playlist("p1", older, other), playlist("p2", newer, imported)))
        )
        assertEquals(listOf("b", "c"), tracks.map { it.itemId })
    }

    @Test fun recentTracksFollowHistoryAndSkipMissingMetadata() {
        val lib = library(
            stats = listOf(stat(1, 1, 10L), stat(2, 1, 20L)),
            recent = listOf(
                MusicHistoryEntry(trackKey = musicTrackKey(origin(2), null, "x"), playedAt = 30L),
                MusicHistoryEntry(trackKey = "tg:acc:1:404", playedAt = 40L),
                MusicHistoryEntry(trackKey = musicTrackKey(origin(1), null, "x"), playedAt = 50L)
            )
        )
        assertEquals(listOf("T2", "T1"), recentTracks(lib).map { it.title })
    }

    @Test fun downloadedKeepsOnlyCandidatesWithALocalFile() {
        val candidates = listOf(track(1, itemId = "a"), track(2, itemId = "b"), track(3, itemId = "c"))
        val onDisk = listOf(true, false, false)
        assertEquals(listOf("a"), downloadedTracks(candidates, onDisk).map { it.itemId })
    }

    @Test fun unavailableFlagsAreEmptyUntilTheLookupFinishes() {
        assertEquals(listOf(false, false), unavailableFlags(2, null))
        assertEquals(listOf(false, true, false), unavailableFlags(3, listOf(item("/a"), null, item("/c"))))
        assertEquals(listOf(true), unavailableFlags(1, emptyList()))
    }

    @Test fun smartCollectionsWithDataAreVisibleAndEmptyOnesHidden() {
        val now = 100 * day
        assertTrue(visibleSmartCollections(library(), now).isEmpty())

        // Track 1 was started twice, recently; track 2 once, long ago.
        val stats = listOf(stat(1, 2, now - day), stat(2, 1, now - 60 * day))
        assertEquals(
            listOf(MusicCollectionKind.Frequent, MusicCollectionKind.Lately),
            visibleSmartCollections(library(stats = stats), now)
        )

        val withAddition = library(playlists = listOf(playlist("p", track(3, addedAt = now))), stats = stats)
        assertEquals(
            listOf(MusicCollectionKind.Frequent, MusicCollectionKind.Added, MusicCollectionKind.Lately),
            visibleSmartCollections(withAddition, now)
        )
    }

    @Test fun libraryOfAnotherAccountIsNotShown() {
        val lib = library()
        assertEquals(lib, libraryFor(account, lib))
        assertNull(libraryFor("other", lib))
    }

    @Test fun containsTrackMatchesByIdentityNotByEntryId() {
        val saved = track(5, itemId = "entry-1")
        val list = playlist("p", saved)
        assertTrue(containsTrack(list, track(5, itemId = "another-entry")))
        assertFalse(containsTrack(list, track(6)))
        assertFalse(containsTrack(null, saved))
    }

    @Test fun userPlaylistsLeaveOutFavorites() {
        val lib = library(playlists = listOf(playlist("p1"), playlist("p2")))
        assertEquals(listOf("p1", "p2"), userPlaylists(lib).map { it.id })
    }

    @Test fun moveLimitsStayInsideTheList() {
        assertFalse(canMoveUp(0))
        assertTrue(canMoveUp(1))
        assertTrue(canMoveDown(0, 2))
        assertFalse(canMoveDown(1, 2))
        assertFalse(canMoveDown(0, 0))
    }

    @Test fun coverDecodeSampleKeepsTheLongSideAboveTheLimit() {
        assertEquals(1, coverSampleSize(1000, MUSIC_COVER_MAX_SIDE_PX))
        assertEquals(2, coverSampleSize(4000, MUSIC_COVER_MAX_SIDE_PX))
        assertEquals(4, coverSampleSize(5000, MUSIC_COVER_MAX_SIDE_PX))
    }
}
