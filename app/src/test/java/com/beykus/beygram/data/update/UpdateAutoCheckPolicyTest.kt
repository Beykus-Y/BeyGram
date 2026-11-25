package com.beykus.beygram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAutoCheckPolicyTest {
    private val halfHour = UpdateAutoCheckPolicy.FOREGROUND_INTERVAL_MS

    @Test
    fun `no previous attempt is always due`() {
        assertTrue(UpdateAutoCheckPolicy.isDue(UpdateAutoCheckPolicy.Attempts(), now = 1_000L, halfHour))
        assertNull(UpdateAutoCheckPolicy.nextAttemptAt(UpdateAutoCheckPolicy.Attempts(), halfHour))
    }

    @Test
    fun `a recent attempt waits for the interval`() {
        val attempts = UpdateAutoCheckPolicy.Attempts(lastAttemptAt = 10_000L)
        assertFalse(UpdateAutoCheckPolicy.isDue(attempts, now = 10_000L + halfHour - 1, halfHour))
        assertTrue(UpdateAutoCheckPolicy.isDue(attempts, now = 10_000L + halfHour, halfHour))
    }

    @Test
    fun `a cold start may check even right after the previous attempt`() {
        val attempts = UpdateAutoCheckPolicy.Attempts(lastAttemptAt = 10_000L)
        assertTrue(
            UpdateAutoCheckPolicy.isDue(
                attempts,
                now = 10_000L + 1_000L,
                intervalMs = UpdateAutoCheckPolicy.LAUNCH_INTERVAL_MS,
            )
        )
    }

    @Test
    fun `failures stretch the next attempt exponentially`() {
        assertEquals(0L, UpdateAutoCheckPolicy.backoffMillis(0))
        assertEquals(5 * 60_000L, UpdateAutoCheckPolicy.backoffMillis(1))
        assertEquals(10 * 60_000L, UpdateAutoCheckPolicy.backoffMillis(2))
        assertEquals(20 * 60_000L, UpdateAutoCheckPolicy.backoffMillis(3))
        // Never longer than six hours, however many failures happened.
        assertEquals(6 * 60 * 60_000L, UpdateAutoCheckPolicy.backoffMillis(50))
    }

    @Test
    fun `backoff wins over a shorter cadence`() {
        val attempts = UpdateAutoCheckPolicy.Attempts(lastAttemptAt = 0L, consecutiveFailures = 3)
        // Three failures mean twenty minutes, less than the thirty-minute cadence.
        assertEquals(UpdateAutoCheckPolicy.FOREGROUND_INTERVAL_MS, UpdateAutoCheckPolicy.nextAttemptAt(attempts, halfHour))
        val manyFailures = UpdateAutoCheckPolicy.Attempts(lastAttemptAt = 0L, consecutiveFailures = 6)
        assertEquals(UpdateAutoCheckPolicy.backoffMillis(6), UpdateAutoCheckPolicy.nextAttemptAt(manyFailures, halfHour))
    }

    @Test
    fun `rate limit blocks every cadence for an hour`() {
        val attempts = UpdateAutoCheckPolicy.Attempts(
            lastAttemptAt = 1_000L,
            consecutiveFailures = 1,
            rateLimitedUntil = UpdateAutoCheckPolicy.afterRateLimit(1_000L),
        )
        val quietUntil = UpdateAutoCheckPolicy.afterRateLimit(1_000L)
        assertFalse(UpdateAutoCheckPolicy.isDue(attempts, now = quietUntil - 1, intervalMs = UpdateAutoCheckPolicy.LAUNCH_INTERVAL_MS))
        assertTrue(UpdateAutoCheckPolicy.isDue(attempts, now = quietUntil, intervalMs = UpdateAutoCheckPolicy.LAUNCH_INTERVAL_MS))
    }

    @Test
    fun `a clock that jumped backwards pauses the loop`() {
        val attempts = UpdateAutoCheckPolicy.Attempts(lastAttemptAt = 10 * 60_000L)
        assertFalse(UpdateAutoCheckPolicy.isDue(attempts, now = 1_000L, UpdateAutoCheckPolicy.LAUNCH_INTERVAL_MS))
    }

    @Test
    fun `jitter is deterministic, bounded and varies with the seed`() {
        val span = 5 * 60_000L
        assertEquals(UpdateAutoCheckPolicy.jitterMs(span, 42L), UpdateAutoCheckPolicy.jitterMs(span, 42L))
        assertTrue(UpdateAutoCheckPolicy.jitterMs(span, 1L) in 0 until span)
        assertTrue(UpdateAutoCheckPolicy.jitterMs(span, 2L) in 0 until span)
        assertTrue(UpdateAutoCheckPolicy.jitterMs(span, 1L) != UpdateAutoCheckPolicy.jitterMs(span, 2L))
        assertEquals(0L, UpdateAutoCheckPolicy.jitterMs(0L, 7L))
    }

    @Test
    fun `only a build newer than the installed one is announced`() {
        assertTrue(UpdateAutoCheckPolicy.shouldAnnounce(releaseVersionCode = 6, installedCode = 5))
        assertFalse(UpdateAutoCheckPolicy.shouldAnnounce(releaseVersionCode = 5, installedCode = 5))
        assertFalse(UpdateAutoCheckPolicy.shouldAnnounce(releaseVersionCode = 4, installedCode = 5))
    }
}