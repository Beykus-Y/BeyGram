package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.ProfileAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileMusicTest {
    private fun track(title: String = "", performer: String = "", fileName: String = "file.mp3") = ProfileAudio(
        fileId = 7,
        title = title,
        performer = performer,
        fileName = fileName,
        durationSeconds = 120,
        mimeType = "audio/mpeg",
        sizeBytes = 10L
    )

    @Test
    fun displayTitleJoinsPerformerAndTitle() {
        assertEquals("Artist — Song", track(title = "Song", performer = "Artist").displayTitle)
    }

    @Test
    fun displayTitleFallsBackToFileNameWithoutTags() {
        assertEquals("file.mp3", track().displayTitle)
    }

    @Test
    fun displayTitleKeepsFileNameWhenOnlyPerformerIsKnown() {
        assertEquals("Artist — file.mp3", track(performer = "Artist").displayTitle)
        assertEquals("file.mp3", track().trackTitle)
    }

    @Test
    fun playbackKeyIsScopedByAccountAndOwner() {
        assertEquals("profile|a|1|7", profileAudioPlaybackKey("a", 1L, 7))
        assertNotEquals(profileAudioPlaybackKey("a", 1L, 7), profileAudioPlaybackKey("b", 1L, 7))
        assertNotEquals(profileAudioPlaybackKey("a", 1L, 7), profileAudioPlaybackKey("a", 2L, 7))
    }
}
