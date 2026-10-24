package com.beykus.beygram.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsRetryPolicyTest {
    @Test
    fun regularTimerAndAccountChangesRespectPersistedDeadline() {
        assertFalse(canAttemptAnalyticsSync(1_000L, 3_000L, forced = false))
        assertFalse(canAttemptAnalyticsSync(2_999L, 3_000L, forced = false))
        assertTrue(canAttemptAnalyticsSync(3_000L, 3_000L, forced = false))
        assertTrue(canAttemptAnalyticsSync(3_001L, 3_000L, forced = false))
    }

    @Test
    fun explicitUserToggleCanOverrideRetryAfter() {
        assertTrue(canAttemptAnalyticsSync(1_000L, 3_000L, forced = true))
        assertTrue(canAttemptAnalyticsSync(1_000L, 0L, forced = false))
    }
}
