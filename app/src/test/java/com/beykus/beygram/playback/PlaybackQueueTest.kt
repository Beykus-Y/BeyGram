package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueTest {
    private val first = PlaybackItem("Audio:1", MediaKind.Audio, "/first.mp3", "First", 180)
    private val second = PlaybackItem("Audio:2", MediaKind.Audio, "/second.mp3", "Second", 200)
    private val third = PlaybackItem("Audio:3", MediaKind.Audio, "/third.mp3", "Third", 120)
    private val queue = listOf(first, second, third)

    @Test
    fun nextTrackFollowsTheQueueOrder() {
        assertEquals(second, nextInQueue(queue, first.key))
        assertEquals(third, nextInQueue(queue, second.key))
    }

    @Test
    fun lastTrackHasNoNext() {
        assertNull(nextInQueue(queue, third.key))
    }

    @Test
    fun trackOutsideTheQueueHasNoNext() {
        assertNull(nextInQueue(queue, "Voice:9"))
    }

    @Test
    fun emptyQueueHasNoNext() {
        assertNull(nextInQueue(emptyList(), first.key))
    }

    @Test
    fun musicIsTitledByPerformerAndTrackTitle() {
        val media = MessageMedia(MediaKind.Audio, fileId = 1, fileName = "file.mp3", title = "Song", performer = "Artist")
        assertEquals("Artist — Song", media.playbackItem("/file.mp3", "Аудио").title)
    }

    @Test
    fun musicWithoutTagsKeepsItsFileName() {
        val media = MessageMedia(MediaKind.Audio, fileId = 1, fileName = "file.mp3")
        assertEquals("file.mp3", media.playbackItem("/file.mp3", "Аудио").title)
    }
}
