package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadNetworkTest {
    @Test fun wifiIsUnmetered() {
        assertEquals(AutoDownloadNetwork.Wifi, classifyAutoDownloadNetwork(true, wifi = true, mobile = false, vpn = false, roaming = false))
    }

    @Test fun mobileAndRoamingAreSeparate() {
        assertEquals(AutoDownloadNetwork.Mobile, classifyAutoDownloadNetwork(true, wifi = false, mobile = true, vpn = false, roaming = false))
        assertEquals(AutoDownloadNetwork.Roaming, classifyAutoDownloadNetwork(true, wifi = false, mobile = true, vpn = false, roaming = true))
    }

    @Test fun noInternetWinsOverTransport() {
        assertEquals(AutoDownloadNetwork.None, classifyAutoDownloadNetwork(false, wifi = true, mobile = true, vpn = false, roaming = false))
    }

    @Test fun unknownOrVpnTransportUsesTheConservativePolicy() {
        assertEquals(AutoDownloadNetwork.Other, classifyAutoDownloadNetwork(true, wifi = false, mobile = false, vpn = true, roaming = false))
        assertEquals(AutoDownloadNetwork.Other, classifyAutoDownloadNetwork(true, wifi = false, mobile = false, vpn = false, roaming = false))
        assertTrue(AutoDownloadNetwork.Other.metered)
    }

    @Test fun wifiWinsWhenBothTransportsAreReported() {
        assertEquals(AutoDownloadNetwork.Wifi, classifyAutoDownloadNetwork(true, wifi = true, mobile = true, vpn = false, roaming = true))
    }

    @Test fun meteredWifiIsClassifiedSeparatelyForDataSaver() {
        assertEquals(
            AutoDownloadNetwork.WifiMetered,
            classifyAutoDownloadNetwork(true, wifi = true, mobile = false, vpn = false, roaming = false, wifiMetered = true)
        )
        assertTrue(AutoDownloadNetwork.WifiMetered.metered)
        assertFalse(AutoDownloadNetwork.Wifi.metered)
    }
}
