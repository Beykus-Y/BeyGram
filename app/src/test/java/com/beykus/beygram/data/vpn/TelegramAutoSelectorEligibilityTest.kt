package com.beykus.beygram.data.vpn

import com.beykus.beygram.domain.model.TelegramConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAutoSelectorEligibilityTest {
    @Test fun probesWhenTelegramWaitsForNetworkDespiteValidatedAndroidNetwork() {
        assertTrue(shouldProbeAutomaticRoute(true, true, TelegramConnectionState.WaitingForNetwork, ProxyRuntimeStatus.RUNNING))
        assertTrue(shouldProbeAutomaticRoute(true, true, TelegramConnectionState.Connecting, ProxyRuntimeStatus.RUNNING))
    }

    @Test fun neverProbesOnDirectModeOrWhenListenerIsUnavailable() {
        assertFalse(shouldProbeAutomaticRoute(false, true, TelegramConnectionState.Connecting, ProxyRuntimeStatus.RUNNING))
        assertFalse(shouldProbeAutomaticRoute(true, true, TelegramConnectionState.Connecting, ProxyRuntimeStatus.STOPPED))
        assertFalse(shouldProbeAutomaticRoute(true, true, TelegramConnectionState.WaitingForNetwork, ProxyRuntimeStatus.STARTING))
        assertFalse(shouldProbeAutomaticRoute(true, false, TelegramConnectionState.WaitingForNetwork, ProxyRuntimeStatus.RUNNING))
    }

    @Test fun connectedTelegramDoesNotStartUnnecessarySweep() {
        assertFalse(shouldProbeAutomaticRoute(true, true, TelegramConnectionState.Connected, ProxyRuntimeStatus.RUNNING))
    }
}
