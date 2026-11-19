package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPositionViewModelTest {
    @Test fun endVisitClearsTheOpeningDecision() {
        val state = ChatScrollPositionViewModel()
        state.readBoundaryId = 42L
        state.readBoundaryChecked = true
        state.readBoundaryWindowRequested = true
        state.focusClaimed = true
        state.endVisit()
        assertNull(state.readBoundaryId)
        assertFalse(state.readBoundaryChecked)
        assertFalse(state.readBoundaryWindowRequested)
        assertFalse(state.focusClaimed)
    }

    @Test fun localSendIsPendingWithinTheWindowOnly() {
        val state = ChatScrollPositionViewModel()
        assertFalse(state.localSendPending(1_000L))
        state.markLocalSend(1_000L)
        assertTrue(state.localSendPending(1_000L))
        assertTrue(state.localSendPending(1_000L + LOCAL_SEND_FOLLOW_WINDOW_MS))
        assertFalse(state.localSendPending(1_000L + LOCAL_SEND_FOLLOW_WINDOW_MS + 1))
        // A clock that moved backwards does not count as a pending send.
        assertFalse(state.localSendPending(999L))
    }

    @Test fun consumeLocalSendEndsThePendingState() {
        val state = ChatScrollPositionViewModel()
        state.markLocalSend(1_000L)
        state.consumeLocalSend()
        assertFalse(state.localSendPending(1_000L))
        assertEquals(0L, state.localSendAtMs)
    }

    @Test fun endVisitKeepsAnchorButResetsRestoreAndLocalSend() {
        val state = ChatScrollPositionViewModel()
        val anchor = ChatScrollAnchor(key = "message:7", messageId = 7L, offset = 12, atBottom = false, savedAtMs = 1L)
        state.anchor = anchor
        state.anchorRestored = true
        state.markLocalSend(1_000L)
        state.previousFirstKey = "message:7"
        state.endVisit()
        assertEquals(anchor, state.anchor)
        assertFalse(state.anchorRestored)
        assertFalse(state.localSendPending(1_000L))
        assertNull(state.previousFirstKey)
    }
}
