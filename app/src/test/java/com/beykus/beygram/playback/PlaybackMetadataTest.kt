package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMetadataTest {
    private val music = PlaybackItem(
        key = "tg|account|chat:1:message:2|Audio|10",
        kind = MediaKind.Audio,
        path = "/song.mp3",
        title = "Artist — Song",
        durationSeconds = 245,
        artworkPath = "/art.jpg",
        performer = "Artist",
        fileId = 10,
        trackTitle = "Song",
        album = "Album"
    )

    @Test
    fun metadataKeepsSeparateTitleArtistAlbumArtworkAndDuration() {
        val metadata = music.systemMetadata()
        assertEquals("Song", metadata.title)
        assertEquals("Artist", metadata.artist)
        assertEquals("Album", metadata.album)
        assertEquals("/art.jpg", metadata.artworkPath)
        assertEquals(245_000L, metadata.durationMs)
    }

    @Test
    fun voiceMetadataDoesNotPretendToHaveAlbumArtwork() {
        val voice = PlaybackItem("Voice:1", MediaKind.Voice, "/voice.ogg", "Voice", 5)
        val metadata = voice.systemMetadata()
        assertEquals("Voice", metadata.title)
        assertEquals("", metadata.artist)
        assertNull(metadata.artworkPath)
    }

    @Test
    fun secretMetadataIsAlwaysEmpty() {
        val secret = music.copy(secret = true, artworkPath = "/secret-art.jpg")
        assertEquals(PlaybackMetadata(), secret.systemMetadata())
    }

    @Test
    fun secretChatMetadataIsAlsoWithheld() {
        val private = music.copy(isSecretChat = true, artworkPath = "/secret-chat-art.jpg")
        assertEquals(PlaybackMetadata(), private.systemMetadata())
        assertFalse(private.systemMediaId == private.key)
    }

    @Test
    fun viewOnceMetadataIsAlsoWithheld() {
        val once = music.copy(viewOnce = true, artworkPath = "/view-once-art.jpg")
        assertEquals(PlaybackMetadata(), once.systemMetadata())
        assertFalse(once.systemMediaId == once.key)
    }
}
