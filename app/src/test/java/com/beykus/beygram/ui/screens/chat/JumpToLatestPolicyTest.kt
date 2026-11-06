package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpToLatestPolicyTest {
    @Test fun animationIsAllowedForLoadedHistory() {
        assertTrue(shouldAnimateJumpToLatest(motionInstant = false, hasNewerMessages = false))
    }

    @Test fun disabledMotionAlwaysJumpsImmediately() {
        assertFalse(shouldAnimateJumpToLatest(motionInstant = true, hasNewerMessages = false))
        assertFalse(shouldAnimateJumpToLatest(motionInstant = true, hasNewerMessages = true))
    }

    @Test fun olderOrUnloadedHistoryNeverAnimatesLongJump() {
        assertFalse(shouldAnimateJumpToLatest(motionInstant = false, hasNewerMessages = true))
    }
}
