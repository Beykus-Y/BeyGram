package com.beykus.beygram.data.media

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadTdlibMappingTest {
    private val mib = AutoDownloadSettings.MIB
    private val template = TdApi.AutoDownloadSettings(
        /* isAutoDownloadEnabled = */ true,
        /* maxPhotoFileSize       = */ 9,
        /* maxVideoFileSize       = */ 9L,
        /* maxOtherFileSize       = */ 9L,
        /* videoUploadBitrate     = */ 1200,
        /* preloadLargeVideos     = */ true,
        /* preloadNextAudio       = */ true,
        /* preloadStories         = */ true,
        /* useLessDataForCalls    = */ false
    )

    private val settings = AutoDownloadSettings(
        wifi = AutoDownloadNetworkPolicy(
            photos = AutoDownloadRule(true, 2 * mib),
            videos = AutoDownloadRule(false, 5 * mib),
            files = AutoDownloadRule(true, 1 * mib),
            music = AutoDownloadRule(true, 3 * mib)
        ),
        mobile = AutoDownloadNetworkPolicy(
            photos = AutoDownloadRule(true, mib),
            videos = AutoDownloadRule(true, 4 * mib),
            files = AutoDownloadRule(false, 2 * mib),
            music = AutoDownloadRule(false, 3 * mib)
        ),
        roaming = AutoDownloadNetworkPolicy()
    )

    @Test fun mapsPerCategoryLimitsAndPreloadSwitches() {
        val mapped = AutoDownloadTdlibMapping.toTdlib(settings, AutoDownloadNetwork.Wifi, template)
        assertTrue(mapped.isAutoDownloadEnabled)
        assertEquals((2 * mib).toInt(), mapped.maxPhotoFileSize)
        assertEquals(0L, mapped.maxVideoFileSize)
        assertEquals(3 * mib, mapped.maxOtherFileSize)
        assertEquals(1200, mapped.videoUploadBitrate)
        assertFalse(mapped.preloadLargeVideos)
        assertTrue(mapped.preloadNextAudio)
        assertFalse(mapped.preloadStories)
        assertFalse(mapped.useLessDataForCalls)
    }

    @Test fun disabledNetworkSendsNoLimits() {
        val mapped = AutoDownloadTdlibMapping.toTdlib(settings, AutoDownloadNetwork.Roaming, template)
        assertFalse(mapped.isAutoDownloadEnabled)
        assertEquals(0, mapped.maxPhotoFileSize)
        assertEquals(0L, mapped.maxVideoFileSize)
        assertEquals(0L, mapped.maxOtherFileSize)
        assertFalse(mapped.preloadLargeVideos)
        assertFalse(mapped.preloadNextAudio)
    }

    @Test fun dataSaverOverridesMeteredPolicyButNotWifi() {
        val saver = settings.copy(dataSaver = true)
        val mobile = AutoDownloadTdlibMapping.toTdlib(saver, AutoDownloadNetwork.Mobile, template)
        assertFalse(mobile.isAutoDownloadEnabled)
        assertEquals(0, mobile.maxPhotoFileSize)
        assertTrue(mobile.useLessDataForCalls)

        val wifi = AutoDownloadTdlibMapping.toTdlib(saver, AutoDownloadNetwork.Wifi, template)
        assertTrue(wifi.isAutoDownloadEnabled)
        assertEquals((2 * mib).toInt(), wifi.maxPhotoFileSize)
    }

    @Test fun unlimitedPhotoIsClampedToTdlibInt() {
        val unlimited = settings.copy(
            wifi = settings.wifi.copy(photos = AutoDownloadRule(true, Long.MAX_VALUE))
        )
        val mapped = AutoDownloadTdlibMapping.toTdlib(unlimited, AutoDownloadNetwork.Wifi, template)
        assertEquals(Int.MAX_VALUE, mapped.maxPhotoFileSize)
    }

    @Test fun configuresExactlyTheThreeUiNetworks() {
        val networks = AutoDownloadTdlibMapping.configuredNetworks.map { it.first }
        assertEquals(
            listOf(AutoDownloadNetwork.Wifi, AutoDownloadNetwork.Mobile, AutoDownloadNetwork.Roaming),
            networks
        )
        assertTrue(AutoDownloadTdlibMapping.configuredNetworks[0].second is TdApi.NetworkTypeWiFi)
        assertTrue(AutoDownloadTdlibMapping.configuredNetworks[1].second is TdApi.NetworkTypeMobile)
        assertTrue(AutoDownloadTdlibMapping.configuredNetworks[2].second is TdApi.NetworkTypeMobileRoaming)
    }
}
