package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSmartCollectionsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun stat(message: Long, playCount: Int, lastPlayedAt: Long?, chat: Long = 1L): MusicListenStats {
        val origin = PlaybackOrigin(chatId = chat, messageId = message, accountId = TEST_ACCOUNT)
        return MusicListenStats(
            track = MusicTrackRef(trackKey = musicTrackKey(origin, null, "x"), title = "T$message", origin = origin),
            playCount = playCount,
            lastPlayedAt = lastPlayedAt
        )
    }

    private fun libraryWith(
        stats: List<MusicListenStats> = emptyList(),
        playlists: List<MusicPlaylist> = emptyList()
    ): MusicLibrary = MusicLibrary(
        accountId = TEST_ACCOUNT,
        playlists = listOf(favoritesPlaylist(0L)) + playlists,
        stats = stats.associateBy { it.trackKey }
    )

    private fun entry(itemId: String, message: Long, addedAt: Long, chat: Long = 1L) = PlaylistTrack(
        itemId = itemId,
        title = "T$message",
        performer = "",
        album = "",
        durationSeconds = 0,
        origin = PlaybackOrigin(chatId = chat, messageId = message, accountId = TEST_ACCOUNT),
        addedAt = addedAt
    )

    private fun playlist(id: String, vararg tracks: PlaylistTrack) = MusicPlaylist(
        id = id,
        title = id,
        tracks = tracks.toList()
    )

    @Test
    fun frequentlyPlayedNeedsTwoStartsAndSortsByCount() {
        val library = libraryWith(
            stats = listOf(
                stat(1, playCount = 1, lastPlayedAt = now),
                stat(2, playCount = 5, lastPlayedAt = null),
                stat(3, playCount = 2, lastPlayedAt = 10L),
                stat(4, playCount = 2, lastPlayedAt = 20L)
            )
        )
        assertEquals(listOf(2L, 4L, 3L), frequentlyPlayed(library).map { it.origin!!.messageId })
    }

    @Test
    fun frequentlyPlayedIsLimitedToFifty() {
        val library = libraryWith(stats = (1L..60L).map { stat(it, playCount = 2, lastPlayedAt = it) })
        assertEquals(MUSIC_SMART_LIMIT, frequentlyPlayed(library).size)
    }

    @Test
    fun notPlayedLatelyNeedsAStartOlderThanThirtyDays() {
        val library = libraryWith(
            stats = listOf(
                stat(1, playCount = 3, lastPlayedAt = now - 31 * day),
                stat(2, playCount = 1, lastPlayedAt = now - 29 * day),
                stat(3, playCount = 0, lastPlayedAt = null),
                stat(4, playCount = 2, lastPlayedAt = now - 30 * day)
            )
        )
        assertEquals(listOf(1L), notPlayedLately(library, now).map { it.origin!!.messageId })
    }

    @Test
    fun recentlyAddedUsesTheNewestAdditionOfEachTrackAcrossPlaylists() {
        val library = libraryWith(
            playlists = listOf(
                playlist("first", entry("a", 1, addedAt = 10), entry("b", 2, addedAt = 30)),
                playlist("second", entry("c", 1, addedAt = 50), entry("d", 3, addedAt = 20))
            )
        )
        assertEquals(listOf(1L, 2L, 3L), recentlyAdded(library).map { it.origin!!.messageId })
    }

    @Test
    fun recentlyAddedIsLimitedToFifty() {
        val tracks = (1L..60L).map { entry("i$it", it, addedAt = it) }
        val library = libraryWith(playlists = listOf(playlist("big", *tracks.toTypedArray())))
        assertEquals(MUSIC_SMART_LIMIT, recentlyAdded(library).size)
    }

    @Test
    fun fromChatReturnsOnlyThatChatsTracksNewestFirst() {
        val library = libraryWith(
            playlists = listOf(
                playlist("p", entry("a", 1, addedAt = 10, chat = 1), entry("b", 2, addedAt = 20, chat = 2), entry("c", 3, addedAt = 30, chat = 1))
            )
        )
        assertEquals(listOf(3L, 1L), fromChat(library, 1L).map { it.origin!!.messageId })
        assertEquals(listOf(2L), fromChat(library, 2L).map { it.origin!!.messageId })
        assertEquals(emptyList<MusicTrackRef>(), fromChat(library, 99L))
    }
}
