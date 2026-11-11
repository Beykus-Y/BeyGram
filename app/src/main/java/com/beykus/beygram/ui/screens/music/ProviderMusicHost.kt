package com.beykus.beygram.ui.screens.music

import android.content.Context
import androidx.compose.runtime.Composable
import com.beykus.beygram.BuildConfig
import com.beykus.beygram.data.music.provider.ProviderTrack

/**
 * Music-screen UI of an optional music source. Only the DEV build has one, so Stable draws nothing and never
 * references the implementation, which is loaded by class name (see [ProviderMusicHosts]).
 */
interface ProviderMusicHost {
    /**
     * Whether this build can reach the source at all, for example has its sign-in keys. When it cannot, the music
     * screen shows no tab for it instead of a sign-in that leads nowhere.
     */
    fun isAvailable(context: Context): Boolean = true

    /**
     * Results of [query] from this source, shown below the local results. Draws nothing when the source is not
     * signed in or the query is blank. [onPlay] starts a list at an index; null means the rows cannot play yet.
     */
    @Composable
    fun SearchResults(query: String, onPlay: ((List<ProviderTrack>, Int) -> Unit)?)

    /** Short name of this source on the source switch of the music screen. */
    @Composable
    fun tabLabel(): String

    /**
     * Liked tracks and playlists of this source: the whole content of its tab on the music screen. Before sign-in it
     * shows a card whose button calls [onSignIn]. [onPlay] starts a list at an index; null means the rows cannot play
     * yet.
     */
    @Composable
    fun LibrarySection(onPlay: ((List<ProviderTrack>, Int) -> Unit)?, onSignIn: () -> Unit)
}

object ProviderMusicHosts {
    /** DEV-only host, loaded by name. */
    private const val YANDEX_HOST_CLASS = "com.beykus.beygram.ui.screens.music.yandex.YandexMusicHost"

    @Volatile
    private var yandexHost: ProviderMusicHost? = null

    @Volatile
    private var yandexLoaded = false

    /** Yandex Music host for the music screens, or null when this build does not include the source. */
    @Synchronized
    fun yandex(): ProviderMusicHost? {
        if (!yandexLoaded) {
            yandexLoaded = true
            yandexHost = if (BuildConfig.FLAVOR == "dev") loadHost(YANDEX_HOST_CLASS) else null
        }
        return yandexHost
    }

    private fun loadHost(className: String): ProviderMusicHost? = try {
        Class.forName(className).getDeclaredConstructor().newInstance() as? ProviderMusicHost
    } catch (e: ClassNotFoundException) {
        null
    }
}
