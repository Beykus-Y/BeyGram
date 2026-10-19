package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryAutoLoadTest {
    private fun nearTop(
        index: Int = 44,
        rows: Int = 50,
        older: Boolean = true,
        loading: Boolean = false,
        error: Boolean = false,
        ready: Boolean = true
    ) = shouldAutoLoadOlderMessages(index, rows, older, loading, error, ready)

    @Test fun prefetchesNearTheOldestLoadedItem() {
        assertTrue(nearTop(index = 43))
        assertTrue(nearTop(index = 49))
        assertFalse(nearTop(index = 42))
    }

    @Test fun noRequestDuringLoadingOrWhenHistoryIsExhausted() {
        assertFalse(nearTop(loading = true))
        assertFalse(nearTop(older = false))
        assertFalse(nearTop(error = true))
        assertFalse(nearTop(ready = false))
    }

    @Test fun noRequestBeforeAViewportIsMeasuredOrWhenEmpty() {
        assertFalse(nearTop(index = -1))
        assertFalse(nearTop(index = 0, rows = 0))
        assertTrue(nearTop(index = 0, rows = 1))
    }
}
