package com.beykus.beygram.ui.screens.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftHistoryTest {
    @Test fun emptyHistoryHasNothingToUndo() {
        val history = DraftHistory<Int>()
        assertFalse(history.canUndo)
        assertNull(history.undo())
    }

    @Test fun undoReturnsTheNewestStateFirst() {
        val history = DraftHistory<Int>()
        history.checkpoint(1)
        history.checkpoint(2)
        assertTrue(history.canUndo)
        assertEquals(2, history.undo())
        assertEquals(1, history.undo())
        assertFalse(history.canUndo)
        assertNull(history.undo())
    }

    @Test fun oldestStatesAreDroppedAtTheLimit() {
        val history = DraftHistory<Int>(limit = 2)
        history.checkpoint(1)
        history.checkpoint(2)
        history.checkpoint(3)
        assertEquals(3, history.undo())
        assertEquals(2, history.undo())
        assertNull(history.undo())
    }
}
