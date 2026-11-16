package com.beykus.beygram.data.music.provider

import android.content.Context
import com.beykus.beygram.data.yandex.YandexMusicProviderFactory

/**
 * Creates a provider implementation, or null when the build cannot offer it (for example, no client config).
 * Availability is determined by provider configuration, not by the release channel.
 */
interface MusicProviderFactory {
    fun create(appContext: Context): MusicProvider?
}

/**
 * Entry point for screens and the player. Returns null when a provider is not configured,
 * so callers hide the provider's UI instead of showing an error.
 */
object MusicProviders {
    @Volatile
    private var yandexProvider: MusicProvider? = null

    @Volatile
    private var yandexLoaded = false

    /** Yandex Music provider, or null when this build does not include it. Created once per process. */
    @Synchronized
    fun yandex(context: Context): MusicProvider? {
        if (!yandexLoaded) {
            yandexLoaded = true
            yandexProvider = YandexMusicProviderFactory().create(context.applicationContext)
        }
        return yandexProvider
    }

}
