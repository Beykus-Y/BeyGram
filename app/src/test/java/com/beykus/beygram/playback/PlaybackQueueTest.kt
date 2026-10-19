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
        assertEquals(second, nextInQueue(queue, 0, first.key))
        assertEquals(third, nextInQueue(queue, 1, second.key))
    }

    @Test
    fun lastTrackHasNoNext() {
        assertNull(nextInQueue(queue, 2, third.key))
    }

    @Test
    fun trackOutsideTheQueueHasNoNext() {
        assertNull(nextInQueue(queue, 0, "Voice:9"))
    }

    @Test
    fun placeWithAnotherTrackHasNoNext() {
        assertNull(nextInQueue(queue, 1, first.key))
    }

    @Test
    fun emptyQueueHasNoNext() {
        assertNull(nextInQueue(emptyList(), -1, first.key))
    }

    private val chatA = PlaybackOrigin(1L, 10L)
    private val chatB = PlaybackOrigin(2L, 20L)
    private val voice = PlaybackItem("Voice:9", MediaKind.Voice, "/voice.ogg", "Voice", 5)

    @Test
    fun sameFileFromAnotherChatDropsTheOldQueue() {
        val previous = PlaybackQueue(queue, 0)
        val playing = first.copy(origin = chatA)
        assertEquals(PlaybackQueue(), queueAfterReselect(previous, playing.copy(origin = chatB), given = null, currentOrigin = chatA))
    }

    @Test
    fun sameFileFromTheSamePlaceKeepsTheQueue() {
        val previous = PlaybackQueue(queue, 0)
        assertEquals(previous, queueAfterReselect(previous, first.copy(origin = chatA), given = null, currentOrigin = chatA))
    }

    @Test
    fun givenQueueReplacesTheOldOne() {
        val previous = PlaybackQueue(queue, 0)
        val given = PlaybackQueue(listOf(third, first), 1)
        assertEquals(given, queueAfterReselect(previous, first.copy(origin = chatB), given, currentOrigin = chatA))
    }

    @Test
    fun voiceReselectedFromAnotherChatKeepsTheMusicQueue() {
        val previous = PlaybackQueue(queue, 1)
        assertEquals(previous, queueAfterReselect(previous, voice.copy(origin = chatB), given = null, currentOrigin = chatA))
    }

    @Test
    fun repeatedTrackAdvancesFromItsOwnPlace() {
        val repeated = listOf(first, second, first, third)
        assertEquals(third, nextInQueue(repeated, 2, first.key))
        assertEquals(second, nextInQueue(repeated, 0, first.key))
    }

    @Test
    fun musicIsTitledByPerformerAndTrackTitle() {
        val media = MessageMedia(MediaKind.Audio, fileId = 1, fileName = "file.mp3", title = "Song", performer = "Artist")
        assertEquals("Artist — Song", media.playbackItem("/file.mp3", "Аудио").title)
    }

    @Test
    fun musicWithoutTitleIsTitledByPerformerAndFileName() {
        val media = MessageMedia(MediaKind.Audio, fileId = 1, fileName = "file.mp3", performer = "Artist")
        assertEquals("Artist — file.mp3", media.playbackItem("/file.mp3", "Аудио").title)
    }

    @Test
    fun musicWithoutTagsKeepsItsFileName() {
        val media = MessageMedia(MediaKind.Audio, fileId = 1, fileName = "file.mp3")
        assertEquals("file.mp3", media.playbackItem("/file.mp3", "Аудио").title)
    }
}
