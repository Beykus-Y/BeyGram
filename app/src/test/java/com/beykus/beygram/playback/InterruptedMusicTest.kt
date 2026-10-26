package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptedMusicTest {
    private val music = PlaybackItem("Audio:1", MediaKind.Audio, "/music.mp3", "Track", 180)
    private val otherMusic = PlaybackItem("Audio:2", MediaKind.Audio, "/other.mp3", "Other", 200)
    private val voice = PlaybackItem("Voice:3", MediaKind.Voice, "/voice.ogg", "Voice", 5)
    private val circle = PlaybackItem("VideoNote:4", MediaKind.VideoNote, "/circle.mp4", "Circle", 7)

    @Test
    fun playingMusicWaitsForVoiceAtItsPosition() {
        val playing = PlaybackState(item = music, playing = true, positionMs = 61_000L)
        assertEquals(InterruptedMusic(music, 61_000L), interruptedAfterStart(playing, null, voice))
    }

    @Test
    fun pausedMusicDoesNotWait() {
        val paused = PlaybackState(item = music, playing = false, positionMs = 61_000L)
        assertNull(interruptedAfterStart(paused, null, voice))
    }

    @Test
    fun emptyPlayerDoesNotWait() {
        assertNull(interruptedAfterStart(PlaybackState(), null, circle))
    }

    @Test
    fun voiceAndCircleKeepTheMusicWaiting() {
        val waiting = InterruptedMusic(music, 61_000L)
        val voicePlaying = PlaybackState(item = voice, playing = true)
        assertEquals(waiting, interruptedAfterStart(voicePlaying, waiting, circle))
    }

    @Test
    fun newMusicReplacesTheWaitingMusic() {
        val waiting = InterruptedMusic(music, 61_000L)
        val voicePlaying = PlaybackState(item = voice, playing = true)
        assertNull(interruptedAfterStart(voicePlaying, waiting, otherMusic))
    }

    @Test
    fun startingTheWaitingMusicConsumesIt() {
        val waiting = InterruptedMusic(music, 61_000L)
        val voicePlaying = PlaybackState(item = voice, playing = true)
        assertNull(interruptedAfterStart(voicePlaying, waiting, music))
    }

    @Test
    fun resumeReturnsTheWaitingMusicToItsPosition() {
        val waiting = InterruptedMusic(music, 61_000L)
        assertEquals(61_000L, resumePositionMs(waiting, music.key))
    }

    @Test
    fun otherFileStartsFromTheBeginning() {
        val waiting = InterruptedMusic(music, 61_000L)
        assertEquals(0L, resumePositionMs(waiting, otherMusic.key))
        assertEquals(0L, resumePositionMs(null, music.key))
    }

    @Test
    fun reselectingInterruptedMusicRestoresPositionQueueAndModes() {
        val savedQueue = listOf(music, otherMusic)
        val waiting = InterruptedMusic(
            item = music,
            positionMs = 61_000L,
            queue = savedQueue,
            queueIndex = 1,
            shuffleEnabled = true,
            repeatMode = PlaybackRepeatMode.All
        )
        val restore = interruptedMusicRestore(waiting, music.key, givenQueue = null, givenSource = null)
        assertNotNull(restore)
        val restored = restore!!
        assertEquals(PlaybackQueue(savedQueue, 1), restored.queue)
        assertEquals(61_000L, restored.positionMs)
        assertTrue(restored.shuffleEnabled)
        assertEquals(PlaybackRepeatMode.All, restored.repeatMode)
    }

    @Test
    fun explicitQueueWinsButInterruptedPositionIsStillKept() {
        val waiting = InterruptedMusic(music, 61_000L, queue = listOf(music, otherMusic), queueIndex = 1)
        val given = PlaybackQueue(listOf(otherMusic, music), 1)
        val restore = interruptedMusicRestore(waiting, music.key, given, givenSource = null)
        assertEquals(given, restore?.queue)
        assertEquals(61_000L, restore?.positionMs)
    }

    @Test
    fun anotherTrackDoesNotRestoreInterruptedMusic() {
        val waiting = InterruptedMusic(music, 61_000L)
        assertNull(interruptedMusicRestore(waiting, otherMusic.key, givenQueue = null, givenSource = null))
        assertNull(interruptedMusicRestore(null, music.key, givenQueue = null, givenSource = null))
    }
}
