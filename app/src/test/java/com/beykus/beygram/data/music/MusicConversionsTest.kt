package com.beykus.beygram.data.music

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicConversionsTest {
    private fun playback(
        kind: MediaKind = MediaKind.Audio,
        origin: PlaybackOrigin? = PlaybackOrigin(chatId = 5L, messageId = 9L, accountId = TEST_ACCOUNT),
        secret: Boolean = false,
        viewOnce: Boolean = false
    ): PlaybackItem = PlaybackItem(
        key = "key",
        kind = kind,
        path = "/music/a.mp3",
        title = "Artist — Song",
        durationSeconds = 201,
        origin = origin,
        accountId = TEST_ACCOUNT,
        trackTitle = "Song",
        performer = "Artist",
        album = "Album",
        secret = secret,
        viewOnce = viewOnce
    )

    @Test
    fun musicWithAnOriginBecomesAPlaylistTrackWithItsTagTitle() {
        val track = playback().toPlaylistTrack(itemId = "item", addedAt = 42L)!!
        assertEquals("item", track.itemId)
        assertEquals("Song", track.title)
        assertEquals("Artist", track.performer)
        assertEquals("Album", track.album)
        assertEquals(201, track.durationSeconds)
        assertEquals(PlaybackOrigin(chatId = 5L, messageId = 9L, accountId = TEST_ACCOUNT), track.origin)
        assertEquals(42L, track.addedAt)
        assertNull(track.audioHash)
    }

    @Test
    fun nonMusicSecretViewOnceAndOriginlessItemsAreNotTracks() {
        assertNull(playback(kind = MediaKind.Video).toPlaylistTrack())
        assertNull(playback(secret = true).toPlaylistTrack())
        assertNull(playback(viewOnce = true).toPlaylistTrack())
        assertNull(playback(origin = null).toPlaylistTrack())
        assertNull(playback(origin = null).toTrackRef())
        assertNull(playback(secret = true).toTrackRef())
    }

    @Test
    fun aPlaybackItemAndItsPlaylistEntryShareOneTrackKey() {
        val item = playback()
        val track = item.toPlaylistTrack(itemId = "entry", addedAt = 1L)!!
        assertEquals("tg:$TEST_ACCOUNT:5:9", track.trackKey)
        assertEquals(track.trackKey, item.toTrackRef()!!.trackKey)
        assertEquals(track.toTrackRef(), item.toTrackRef())
    }
}
