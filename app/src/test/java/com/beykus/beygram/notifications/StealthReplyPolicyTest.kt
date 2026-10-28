package com.beykus.beygram.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthReplyPolicyTest {
    @Test fun visiblePresenceAllowsEveryAttempt() {
        assertTrue(stealthAllowsReplyAttempt(hideOnline = false, firstDirectAttempt = true))
        assertTrue(stealthAllowsReplyAttempt(hideOnline = false, firstDirectAttempt = false))
    }

    @Test fun hiddenPresenceAllowsOnlyTheDirectAttempt() {
        assertTrue(stealthAllowsReplyAttempt(hideOnline = true, firstDirectAttempt = true))
        assertFalse(stealthAllowsReplyAttempt(hideOnline = true, firstDirectAttempt = false))
    }

    @Test fun aFreshReplyIsLeftForItsDirectAttempt() {
        assertTrue(stealthReplyLeftForDirectAttempt(hideOnline = true, firstDirectAttempt = false, ageMillis = 1_000L))
        assertFalse(stealthReplyLeftForDirectAttempt(hideOnline = true, firstDirectAttempt = false, ageMillis = STEALTH_DIRECT_REPLY_WINDOW_MILLIS))
        assertFalse(stealthReplyLeftForDirectAttempt(hideOnline = true, firstDirectAttempt = true, ageMillis = 1_000L))
        assertFalse(stealthReplyLeftForDirectAttempt(hideOnline = false, firstDirectAttempt = false, ageMillis = 1_000L))
    }
}
