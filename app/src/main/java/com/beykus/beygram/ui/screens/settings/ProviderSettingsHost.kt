package com.beykus.beygram.ui.screens.settings

import androidx.compose.runtime.Composable
import com.beykus.beygram.BuildConfig

/**
 * Settings UI of an optional music source. Only the DEV build has one, so Stable draws nothing and never
 * references the implementation, which is loaded by class name (see [ProviderSettingsHosts]).
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
    /** DEV-only host, loaded by name. */
    private const val YANDEX_HOST_CLASS = "com.beykus.beygram.ui.screens.settings.yandex.YandexSettingsHost"

    @Volatile
    private var yandexHost: ProviderSettingsHost? = null

    @Volatile
    private var yandexLoaded = false

    /** Yandex Music settings host, or null when this build does not include the source. */
    @Synchronized
    fun yandex(): ProviderSettingsHost? {
        if (!yandexLoaded) {
            yandexLoaded = true
            yandexHost = if (BuildConfig.FLAVOR == "dev") loadHost(YANDEX_HOST_CLASS) else null
        }
        return yandexHost
    }

    private fun loadHost(className: String): ProviderSettingsHost? = try {
        Class.forName(className).getDeclaredConstructor().newInstance() as? ProviderSettingsHost
    } catch (e: ClassNotFoundException) {
        null
    }
}
