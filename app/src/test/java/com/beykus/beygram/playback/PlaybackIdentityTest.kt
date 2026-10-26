package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackIdentityTest {
    private fun audio(fileId: Int = 7) = MessageMedia(
        kind = MediaKind.Audio,
        fileId = fileId,
        fileName = "song.mp3",
        title = "Song",
        performer = "Artist"
    )

    @Test
    fun sameNumericFileIdInAnotherAccountIsAnotherOccurrence() {
        val media = audio()
        val first = media.playbackKey("account-a", PlaybackOrigin(1L, 10L, "account-a"))
        val second = media.playbackKey("account-b", PlaybackOrigin(1L, 10L, "account-b"))
        assertNotEquals(first, second)
    }

    @Test
    fun sameFileInAnotherMessageStartsANewContext() {
        val media = audio()
        val first = media.playbackKey("account", PlaybackOrigin(1L, 10L, "account"))
        val second = media.playbackKey("account", PlaybackOrigin(1L, 11L, "account"))
        assertNotEquals(first, second)
    }

    @Test
    fun sameFileInAnotherChatIsNotTheSameItem() {
        val media = audio()
        val first = media.playbackKey("account", PlaybackOrigin(1L, 10L, "account"))
        val second = media.playbackKey("account", PlaybackOrigin(2L, 10L, "account"))
        assertNotEquals(first, second)
    }

    @Test
    fun sameFileInTheSameMessageHasTheDocumentedToggleKey() {
        val media = audio()
        val first = media.playbackKey("account", PlaybackOrigin(1L, 10L, "account"))
        val second = media.playbackKey("account", PlaybackOrigin(1L, 10L, "account"))
        assertEquals(first, second)
    }

    @Test
    fun playbackItemCarriesAccountOriginAndOccurrence() {
        val origin = PlaybackOrigin(3L, 30L, "secondary")
        val item = audio().playbackItem("/song.mp3", "Audio", origin)
        assertEquals("secondary", item.accountId)
        assertEquals(origin, item.origin)
        assertEquals(7, item.fileId ?: -1)
        assertEquals("Song", item.trackTitle)
        assertEquals("Artist", item.performer)
        assertEquals(item.key, item.occurrenceId)
        assertEquals(item.key, audio().playbackKey("secondary", origin))
    }

    @Test
    fun viewerOriginMatchesTheBubblePlaybackIdentity() {
        val media = audio()
        val bubbleOrigin = PlaybackOrigin(1L, 10L, "account")
        val viewerOrigin = messagePlaybackOrigin(chatId = 1L, messageId = 10L, accountId = "account")
        assertEquals(bubbleOrigin, viewerOrigin)
        assertEquals(
            media.playbackKey("account", bubbleOrigin),
            media.playbackKey("account", viewerOrigin)
        )
        assertNull(messagePlaybackOrigin(chatId = null, messageId = 10L, accountId = "account"))
    }

    @Test
    fun localFilesHaveASeparateIdentityNamespace() {
        val first = localPlaybackKey("account", "/tmp/a.mp3")
        val same = localPlaybackKey("account", "/tmp/a.mp3")
        val otherPath = localPlaybackKey("account", "/tmp/b.mp3")
        val otherAccount = localPlaybackKey("other", "/tmp/a.mp3")
        assertEquals(first, same)
        assertNotEquals(first, otherPath)
        assertNotEquals(first, otherAccount)
        assertTrue(first.startsWith("local|"))
        assertNotEquals(first, audio().playbackKey("account"))
    }

    @Test
    fun secretSystemIdDoesNotExposeTheTelegramFileId() {
        val item = PlaybackItem(
            key = "tg|account|chat:1:message:2|Audio|7",
            kind = MediaKind.Audio,
            path = "/secret.mp3",
            title = "Secret",
            durationSeconds = 10,
            secret = true,
            fileId = 7
        )
        assertFalse(item.systemMediaId == item.key)
        assertFalse(item.systemMediaId.contains("Audio|7"))
        assertTrue(item.systemMetadata().title.isBlank())
    }
}
