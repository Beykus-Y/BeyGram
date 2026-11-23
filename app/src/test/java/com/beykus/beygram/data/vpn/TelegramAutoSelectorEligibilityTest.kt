package com.beykus.beygram.data.vpn

import com.beykus.beygram.domain.model.TelegramConnectionState
import com.beykus.beygram.domain.model.VpnNetworkState
import com.beykus.beygram.domain.model.VpnTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun wifiToCellularWhileReachableResetsCooldowns() {
        val wifi = VpnNetworkState(internetAvailable = true, wifiAvailable = true, cellularAvailable = true, activeTransport = VpnTransport.WIFI)
        val cellular = wifi.copy(activeTransport = VpnTransport.CELLULAR)
        val wifiKey = autoSelectNetworkKey(wifi)
        val cellularKey = autoSelectNetworkKey(cellular)
        assertNotNull(wifiKey)
        assertNotNull(cellularKey)
        assertNotEquals(wifiKey, cellularKey)
        assertTrue(shouldResetAutoSelectCooldowns("g", wifiKey, "g", cellularKey, true))
    }

    @Test fun cellularToWifiWhileReachableResetsCooldowns() {
        val wifi = VpnNetworkState(internetAvailable = true, wifiAvailable = true, cellularAvailable = true, activeTransport = VpnTransport.WIFI)
        val cellular = wifi.copy(activeTransport = VpnTransport.CELLULAR)
        assertTrue(shouldResetAutoSelectCooldowns("g", autoSelectNetworkKey(cellular), "g", autoSelectNetworkKey(wifi), true))
    }

    @Test fun sameTransportFlagNoiseDoesNotResetCooldowns() {
        val withCellular = VpnNetworkState(internetAvailable = true, wifiAvailable = true, cellularAvailable = true, activeTransport = VpnTransport.WIFI, changedAtMillis = 1_000L)
        val withoutCellular = withCellular.copy(cellularAvailable = false, changedAtMillis = 2_000L)
        val firstKey = autoSelectNetworkKey(withCellular)
        val secondKey = autoSelectNetworkKey(withoutCellular)
        assertEquals(firstKey, secondKey)
        assertFalse(shouldResetAutoSelectCooldowns("g", firstKey, "g", secondKey, true))
    }

    @Test fun reachabilityAndGroupChangesStillReset() {
        val wifi = VpnNetworkState(internetAvailable = true, wifiAvailable = true, activeTransport = VpnTransport.WIFI)
        val unreachable = wifi.copy(internetAvailable = false)
        assertNull(autoSelectNetworkKey(unreachable))
        assertTrue(shouldResetAutoSelectCooldowns("g", autoSelectNetworkKey(wifi), "g", autoSelectNetworkKey(unreachable), true))
        assertTrue(shouldResetAutoSelectCooldowns("g", autoSelectNetworkKey(wifi), "other", autoSelectNetworkKey(wifi), true))
        assertTrue(shouldResetAutoSelectCooldowns(null, null, "g", autoSelectNetworkKey(wifi), false))
    }
}
