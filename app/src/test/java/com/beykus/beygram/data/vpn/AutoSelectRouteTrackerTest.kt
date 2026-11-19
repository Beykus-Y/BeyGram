package com.beykus.beygram.data.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSelectRouteTrackerTest {
    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test fun unconfirmedNodeIsCooledDownAfterConfirmWindow() {
        val clock = FakeClock()
        val tracker = AutoSelectRouteTracker(confirmMillis = 20_000L, cooldownMillis = 600_000L, clock = clock)
        tracker.markPending("a")
        clock.now = 19_999L
        assertFalse(tracker.expireIfUnconfirmed())
        clock.now = 20_000L
        assertTrue(tracker.expireIfUnconfirmed())
        assertTrue(tracker.isCoolingDown("a"))
    }

    @Test fun confirmedNodeIsNeverCooledDown() {
        val clock = FakeClock()
        val tracker = AutoSelectRouteTracker(confirmMillis = 20_000L, cooldownMillis = 600_000L, clock = clock)
        tracker.markPending("a")
        tracker.confirm()
        clock.now = 60_000L
        assertFalse(tracker.expireIfUnconfirmed())
        assertFalse(tracker.isCoolingDown("a"))
    }

    @Test fun keptNodeKeepsItsFirstTimerSoItExpires() {
        val clock = FakeClock()
        val tracker = AutoSelectRouteTracker(confirmMillis = 20_000L, cooldownMillis = 600_000L, clock = clock)
        tracker.markPending("a")
        clock.now = 20_000L
        // The next sweep reports the same node as kept. That must not restart the timer.
        tracker.markPending("a")
        assertTrue(tracker.expireIfUnconfirmed())
    }

    @Test fun cooldownEndsAndClearsWithNetworkChange() {
        val clock = FakeClock()
        val tracker = AutoSelectRouteTracker(confirmMillis = 20_000L, cooldownMillis = 600_000L, clock = clock)
        tracker.markPending("a")
        clock.now = 20_000L
        tracker.expireIfUnconfirmed()
        clock.now = 600_000L
        assertFalse(tracker.isCoolingDown("a"))

        tracker.markPending("b")
        clock.now = 620_000L
        tracker.expireIfUnconfirmed()
        assertTrue(tracker.isCoolingDown("b"))
        tracker.clearCooldowns()
        assertFalse(tracker.isCoolingDown("b"))
    }
}
