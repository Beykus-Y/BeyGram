package com.beykus.beygram.ui.screens.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityPolicyTest {
    @Test
    fun noTrackNeverDisplaysBar() {
        assertFalse(shouldShowMiniPlayer(hasItem = false, hideWhenImeVisible = false, keyboardVisible = false))
        assertFalse(shouldShowMiniPlayer(hasItem = false, hideWhenImeVisible = true, keyboardVisible = true))
    }

    @Test
    fun rootAndTabBarsDoNotCompeteWithKeyboard() {
        assertTrue(shouldShowMiniPlayer(hasItem = true, hideWhenImeVisible = true, keyboardVisible = false))
        assertFalse(shouldShowMiniPlayer(hasItem = true, hideWhenImeVisible = true, keyboardVisible = true))
    }

    @Test
    fun chatIntegratedBarKeepsItsOwnKeyboardLayout() {
        assertTrue(shouldShowMiniPlayer(hasItem = true, hideWhenImeVisible = false, keyboardVisible = false))
        assertTrue(shouldShowMiniPlayer(hasItem = true, hideWhenImeVisible = false, keyboardVisible = true))
    }
}
