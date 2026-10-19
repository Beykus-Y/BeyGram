package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedMessageNavigationTest {
    private val pins = listOf(300L, 200L, 100L).map {
        ChatMessage(id = it, chatId = 1L, text = "Pinned", time = "", isOutgoing = false)
    }
    @Test fun defaultBannerShowsNewestPin() {
        assertEquals(0, currentPinnedIndex(pins, null))
    }
    @Test fun visibleHistoryChoosesNearestEarlierPin() {
        assertEquals(0, currentPinnedIndex(pins, 350L))
        assertEquals(1, currentPinnedIndex(pins, 250L))
        assertEquals(2, currentPinnedIndex(pins, 150L))
    }
    @Test fun olderHistoryAndEmptyPinsAreSafe() {
        assertEquals(2, currentPinnedIndex(pins, 50L))
        assertEquals(-1, currentPinnedIndex(emptyList(), 50L))
    }
    @Test fun viewportOlderThanEveryLoadedPinNeedsNextPage() {
        assertTrue(needsOlderPinnedPage(pins, 50L))
        assertFalse(needsOlderPinnedPage(pins, 100L))
        assertFalse(needsOlderPinnedPage(pins, 250L))
        assertFalse(needsOlderPinnedPage(pins, null))
        assertFalse(needsOlderPinnedPage(pins, 0L))
        assertFalse(needsOlderPinnedPage(emptyList(), 50L))
    }
}
