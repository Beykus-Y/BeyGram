package com.beykus.beygram.data.yandex

import com.beykus.beygram.BuildConfig
import com.beykus.beygram.ui.screens.music.ProviderMusicHosts
import com.beykus.beygram.ui.screens.music.yandex.YandexMusicHost
import com.beykus.beygram.ui.screens.settings.ProviderSettingsHosts
import com.beykus.beygram.ui.screens.settings.yandex.YandexSettingsHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run against both devDebug and prodDebug: neither host may be disabled by the flavor. */
class YandexReleaseAvailabilityTest {
    @Test fun bothHostsAreRegisteredInEveryFlavor() {
        assertTrue(ProviderMusicHosts.yandex() is YandexMusicHost)
        assertTrue(ProviderSettingsHosts.yandex() is YandexSettingsHost)
    }

    @Test fun missingEitherOAuthValueDisablesTheSource() {
        assertEquals(
            BuildConfig.YANDEX_CLIENT_ID.isNotBlank() && BuildConfig.YANDEX_CLIENT_SECRET.isNotBlank(),
            YandexConfig.isConfigured
        )
    }
}
