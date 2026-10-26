package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedOptionsTest {
    @Test fun requestedSpeedChoicesArePresentAndOrdered() {
        assertEquals(listOf(0.5f, 1f, 1.5f, 2f, 5f), PLAYBACK_SPEED_OPTIONS)
    }

    @Test fun labelsAreCleanForFractionalAndWholeSpeeds() {
        assertEquals(listOf("0.5", "1", "1.5", "2", "5"), PLAYBACK_SPEED_OPTIONS.map(::speedLabel))
        assertTrue(1f in PLAYBACK_SPEED_OPTIONS)
    }

    @Test fun inlineVoiceMenuReadsVoicePreferenceEvenWhenMusicIsCurrent() {
        val music = PlaybackItem("Audio:1", MediaKind.Audio, "/music.mp3", "Music", 180)
        val state = PlaybackState(item = music, speed = 1f, musicSpeed = 1f, voiceSpeed = 2f)
        assertEquals(2f, state.inlineVoiceSpeed())
        assertEquals(1f, playbackSpeedFor(MediaKind.Audio, state.musicSpeed, state.voiceSpeed))
    }
}
