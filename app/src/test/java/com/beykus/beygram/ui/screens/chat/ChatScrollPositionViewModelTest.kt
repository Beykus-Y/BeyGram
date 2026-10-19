package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChatScrollPositionViewModelTest {
    @Test fun endVisitClearsTheOpeningDecision() {
        val state = ChatScrollPositionViewModel()
        state.readBoundaryId = 42L
        state.readBoundaryChecked = true
        state.readBoundaryWindowRequested = true
        state.endVisit()
        assertNull(state.readBoundaryId)
        assertFalse(state.readBoundaryChecked)
        assertFalse(state.readBoundaryWindowRequested)
    }
}
