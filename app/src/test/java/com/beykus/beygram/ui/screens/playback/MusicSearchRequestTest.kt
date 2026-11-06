package com.beykus.beygram.ui.screens.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSearchRequestTest {
    @Test fun currentResponseCanUpdateResults() {
        assertTrue(isCurrentMusicSearchResult(4L, 4L, "манит", "  манит  "))
    }

    @Test fun supersededResponseMustNotReplaceNewerResults() {
        assertFalse(isCurrentMusicSearchResult(4L, 5L, "манит", "манит"))
    }

    @Test fun differentQueryNeverAcceptsOldResults() {
        assertFalse(isCurrentMusicSearchResult(4L, 4L, "манит", "другая"))
        assertFalse(isCurrentMusicSearchResult(4L, 4L, "манит", ""))
    }
}
