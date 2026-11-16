package com.beykus.beygram.media.bgwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgWaveListenerKeepAlivePolicyTest {
    private val sid = "102132435465768798a9bacbdcedfe0f"

    private fun state(role: BgWaveRole, phase: BgWavePhase) = BgWaveSessionState(
        role = role, accountId = "a", chatId = 7L, peerId = 7L, sidHex = sid, phase = phase,
    )

    @Test fun noSessionDoesNotKeepAlive() {
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(null))
    }

    @Test fun hostNeverKeepsAliveInAnyPhase() {
        BgWavePhase.entries.forEach { phase ->
            assertFalse("host $phase", BgWaveListenerKeepAlivePolicy.shouldKeepAlive(state(BgWaveRole.Host, phase)))
        }
    }

    @Test fun listenerKeepsAliveWhileTheWaveRuns() {
        listOf(BgWavePhase.Connecting, BgWavePhase.Live, BgWavePhase.Reconnecting).forEach { phase ->
            assertTrue("listener $phase", BgWaveListenerKeepAlivePolicy.shouldKeepAlive(state(BgWaveRole.Listener, phase)))
        }
    }

    @Test fun listenerKeepsAliveInFailedOnlyWhileANetworkRetryCanHelp() {
        val lost = state(BgWaveRole.Listener, BgWavePhase.Failed).copy(errorKey = BgWaveErrors.NO_CONNECTION)
        assertTrue(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(lost))
        assertEquals(BgWaveListenerKeepAlivePolicy.Need.RetryPending, BgWaveListenerKeepAlivePolicy.need(lost))
        val rejected = state(BgWaveRole.Listener, BgWavePhase.Failed).copy(errorKey = BgWaveErrors.REJECTED)
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(rejected))
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(state(BgWaveRole.Listener, BgWavePhase.Failed)))
    }

    @Test fun listenerDoesNotKeepAliveAfterEnd() {
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(state(BgWaveRole.Listener, BgWavePhase.Ended)))
    }

    @Test fun hostNeverKeepsAliveEvenWhenFailedWithoutConnection() {
        val lost = state(BgWaveRole.Host, BgWavePhase.Failed).copy(errorKey = BgWaveErrors.NO_CONNECTION)
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(lost))
    }
}
