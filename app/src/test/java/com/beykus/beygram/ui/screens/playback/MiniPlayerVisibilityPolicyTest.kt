package com.beykus.beygram.ui.screens.playback

import org.junit.Assert.assertEquals
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

    @Test
    fun slotTransitionsAreMutuallyExclusiveAcrossCloseUndoAndRestore() {
        // Playing
        assertEquals(
            NowPlayingBarSlot.MiniPlayer,
            resolveNowPlayingBarSlot(hasItem = true, undoAvailable = false, hideWhenImeVisible = false, keyboardVisible = false)
        )
        // Closed -> Undo available (even if an item flag lingered, item wins; once cleared, UndoClose shows)
        assertEquals(
            NowPlayingBarSlot.UndoClose,
            resolveNowPlayingBarSlot(hasItem = false, undoAvailable = true, hideWhenImeVisible = false, keyboardVisible = false)
        )
        // Restored via Undo
        assertEquals(
            NowPlayingBarSlot.MiniPlayer,
            resolveNowPlayingBarSlot(hasItem = true, undoAvailable = false, hideWhenImeVisible = false, keyboardVisible = false)
        )
        // Closed again -> Undo expires -> Hidden
        assertEquals(
            NowPlayingBarSlot.Hidden,
            resolveNowPlayingBarSlot(hasItem = false, undoAvailable = false, hideWhenImeVisible = false, keyboardVisible = false)
        )
    }

    @Test
    fun slotHidesBothMiniPlayerAndUndoWhenImeVisibleAndConfiguredToYield() {
        assertEquals(
            NowPlayingBarSlot.Hidden,
            resolveNowPlayingBarSlot(hasItem = false, undoAvailable = true, hideWhenImeVisible = true, keyboardVisible = true)
        )
        assertEquals(
            NowPlayingBarSlot.UndoClose,
            resolveNowPlayingBarSlot(hasItem = false, undoAvailable = true, hideWhenImeVisible = false, keyboardVisible = true)
        )
    }
}
