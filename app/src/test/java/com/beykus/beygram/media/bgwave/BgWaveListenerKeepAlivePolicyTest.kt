package com.beykus.beygram.media.bgwave

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

    @Test fun listenerDoesNotKeepAliveAfterFailureOrEnd() {
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(state(BgWaveRole.Listener, BgWavePhase.Failed)))
        assertFalse(BgWaveListenerKeepAlivePolicy.shouldKeepAlive(state(BgWaveRole.Listener, BgWavePhase.Ended)))
    }
}
