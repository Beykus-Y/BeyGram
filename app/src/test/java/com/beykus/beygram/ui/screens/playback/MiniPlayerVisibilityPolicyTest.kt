package com.beykus.beygram.ui.screens.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityPolicyTest {
    @Test
    fun noTrackNeverDisplaysBar() {
        assertFalse(shouldShowMiniPlayer(hasItem = false, bottomInset = false, keyboardVisible = false))
        assertFalse(shouldShowMiniPlayer(hasItem = false, bottomInset = true, keyboardVisible = true))
    }

    @Test
    fun detachedBarDoesNotCompeteWithKeyboard() {
        assertTrue(shouldShowMiniPlayer(hasItem = true, bottomInset = true, keyboardVisible = false))
        assertFalse(shouldShowMiniPlayer(hasItem = true, bottomInset = true, keyboardVisible = true))
    }

    @Test
    fun embeddedBarKeepsItsOwnKeyboardLayout() {
        assertTrue(shouldShowMiniPlayer(hasItem = true, bottomInset = false, keyboardVisible = false))
        assertTrue(shouldShowMiniPlayer(hasItem = true, bottomInset = false, keyboardVisible = true))
    }
}
