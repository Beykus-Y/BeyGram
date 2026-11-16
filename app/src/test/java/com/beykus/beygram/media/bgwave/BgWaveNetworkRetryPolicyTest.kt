package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgWaveNetworkRetryPolicyTest {
    private val debounce = BgWaveNetworkRetryPolicy.DEBOUNCE_MILLIS
    private val window = BgWaveNetworkRetryPolicy.WINDOW_MILLIS

    @Test fun retriesFailedSessionAfterDebounce() {
        assertTrue(BgWaveNetworkRetryPolicy.shouldRetry(BgWavePhase.Failed, 1_000L, false, 1_000L + debounce))
        assertTrue(BgWaveNetworkRetryPolicy.shouldRetry(BgWavePhase.Reconnecting, 1_000L, false, 1_000L + debounce))
    }

    @Test fun doesNotRetryBeforeDebounce() {
        assertFalse(BgWaveNetworkRetryPolicy.shouldRetry(BgWavePhase.Failed, 1_000L, false, 1_000L + debounce - 1))
    }

    @Test fun doesNotRetryAfterWindowOrTwice() {
        assertFalse(BgWaveNetworkRetryPolicy.shouldRetry(BgWavePhase.Failed, 0L, false, window + 1))
        assertFalse(BgWaveNetworkRetryPolicy.shouldRetry(BgWavePhase.Failed, 0L, true, debounce))
    }

    @Test fun doesNotRetryHealthyOrFinishedSessions() {
        listOf(BgWavePhase.Live, BgWavePhase.Connecting, BgWavePhase.Ended, BgWavePhase.Offering, BgWavePhase.AwaitingAnswer)
            .forEach { assertFalse("$it", BgWaveNetworkRetryPolicy.shouldRetry(it, 0L, false, debounce)) }
        assertFalse(BgWaveNetworkRetryPolicy.shouldRetry(null, 0L, false, debounce))
        assertFalse(BgWaveNetworkRetryPolicy.shouldRetry(BgWavePhase.Failed, null, false, debounce))
    }

    @Test fun detectsNetworkChange() {
        assertFalse(BgWaveNetworkRetryPolicy.isChange(null, 1L, false))
        assertFalse(BgWaveNetworkRetryPolicy.isChange(1L, 1L, false))
        assertTrue(BgWaveNetworkRetryPolicy.isChange(1L, 2L, false))
        assertTrue(BgWaveNetworkRetryPolicy.isChange(null, 1L, true))
    }

    @Test fun watchesOnlyRunningOrFailedSessions() {
        fun st(p: BgWavePhase) = BgWaveSessionState(BgWaveRole.Listener, "a", 1L, 1L, "s", p)
        assertFalse(BgWaveNetworkRetryPolicy.isWatched(null))
        assertTrue(BgWaveNetworkRetryPolicy.isWatched(st(BgWavePhase.Live)))
        assertFalse(BgWaveNetworkRetryPolicy.isWatched(st(BgWavePhase.Ended)))
    }
}
