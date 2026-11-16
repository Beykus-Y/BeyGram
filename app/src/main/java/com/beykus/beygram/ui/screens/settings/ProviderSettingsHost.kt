package com.beykus.beygram.ui.screens.settings

import androidx.compose.runtime.Composable
import com.beykus.beygram.ui.screens.settings.yandex.YandexSettingsHost

/**
 * Settings UI of an optional music source, shared by DEV and Stable.
 * The source hides its entry points when it is not configured.
 */
interface ProviderSettingsHost {
    /** Entries shown in the Experiments screen for this source. */
    @Composable
    fun ExperimentsEntries(onOpenSignIn: () -> Unit)

    /** Sign-in and account screen for this source. */
    @Composable
    fun SignInScreen(onBack: () -> Unit)
}

object ProviderSettingsHosts {
    @Volatile
    private var yandexHost: ProviderSettingsHost? = null

    @Volatile
    private var yandexLoaded = false

    /** Yandex Music settings host, or null when this build does not include the source. */
    @Synchronized
    fun yandex(): ProviderSettingsHost? {
        if (!yandexLoaded) {
            yandexLoaded = true
            yandexHost = YandexSettingsHost()
        }
        return yandexHost
    }

}
