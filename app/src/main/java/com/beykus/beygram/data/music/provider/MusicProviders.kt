package com.beykus.beygram.data.music.provider

import android.content.Context
import com.beykus.beygram.BuildConfig

/**
 * Creates a provider implementation, or null when the build cannot offer it (for example, no client config).
 * Implementations live only in the DEV source set, so the Stable build has no such class at all.
 */
interface MusicProviderFactory {
    fun create(appContext: Context): MusicProvider?
}

/**
 * Entry point for screens and the player. Returns null where a provider is not built in (always in Stable),
 * so callers hide the provider's UI instead of showing an error.
 */
object MusicProviders {
    /** The DEV-only factory, loaded by name so that Stable never references the class. */
    private const val YANDEX_FACTORY_CLASS = "com.beykus.beygram.data.yandex.YandexMusicProviderFactory"

    @Volatile
    private var yandexProvider: MusicProvider? = null

    @Volatile
    private var yandexLoaded = false

    /** Yandex Music provider, or null when this build does not include it. Created once per process. */
    @Synchronized
    fun yandex(context: Context): MusicProvider? {
        if (!yandexLoaded) {
            yandexLoaded = true
            yandexProvider = if (BuildConfig.FLAVOR == "dev") loadFactory()?.create(context.applicationContext) else null
        }
        return yandexProvider
    }

    private fun loadFactory(): MusicProviderFactory? = try {
        Class.forName(YANDEX_FACTORY_CLASS).getDeclaredConstructor().newInstance() as? MusicProviderFactory
    } catch (e: ClassNotFoundException) {
        null
    }
}
