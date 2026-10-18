package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToReplyTest {
    private val triggerPx = 56f

    @Test fun bubbleFollowsFingerUpToTrigger() {
        assertEquals(40f, replySwipeOffset(pull = 40f, triggerPx = triggerPx), 0f)
    }

    @Test fun pullPastTriggerIsResisted() {
        assertEquals(71.4f, replySwipeOffset(pull = 100f, triggerPx = triggerPx), 0.01f)
    }

    @Test fun replyArmsOnlyAtTrigger() {
        assertFalse(isReplyArmed(pull = 55f, triggerPx = triggerPx))
        assertTrue(isReplyArmed(pull = 56f, triggerPx = triggerPx))
    }

    @Test fun arrowProgressGrowsToFullAtTrigger() {
        assertEquals(0f, replySwipeProgress(pull = 0f, triggerPx = triggerPx), 0f)
        assertEquals(0.5f, replySwipeProgress(pull = 28f, triggerPx = triggerPx), 0f)
        assertEquals(1f, replySwipeProgress(pull = 90f, triggerPx = triggerPx), 0f)
    }
}
