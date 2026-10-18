package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToReplyTest {
    private val triggerPx = 56f

    @Test fun bubbleFollowsFingerUpToTrigger() {
        assertEquals(-40f, replySwipeOffset(travel = -40f, triggerPx = triggerPx), 0f)
    }

    @Test fun travelPastTriggerIsResisted() {
        assertEquals(-71.4f, replySwipeOffset(travel = -100f, triggerPx = triggerPx), 0.01f)
    }

    @Test fun replyArmsOnlyAtTrigger() {
        assertFalse(isReplyArmed(travel = -55f, triggerPx = triggerPx))
        assertTrue(isReplyArmed(travel = -56f, triggerPx = triggerPx))
    }

    @Test fun arrowProgressGrowsToFullAtTrigger() {
        assertEquals(0f, replySwipeProgress(travel = 12f, triggerPx = triggerPx), 0f)
        assertEquals(0.5f, replySwipeProgress(travel = -28f, triggerPx = triggerPx), 0f)
        assertEquals(1f, replySwipeProgress(travel = -90f, triggerPx = triggerPx), 0f)
    }
}
