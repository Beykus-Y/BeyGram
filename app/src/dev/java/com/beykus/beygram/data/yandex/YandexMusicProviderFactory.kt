package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFactory
import com.beykus.beygram.ui.screens.music.yandex.YandexLibrarySnapshots

/** Loaded by name from [com.beykus.beygram.data.music.provider.MusicProviders] in the DEV build only. */
class YandexMusicProviderFactory : MusicProviderFactory {
    override fun create(appContext: Context): MusicProvider? {
        if (!YandexConfig.isConfigured) return null
        val http = YandexHttp()
        return YandexMusicProvider(
            store = YandexSessionStore(appContext),
            http = http,
            api = YandexApi(http),
            auth = YandexAuth(http),
            // The snapshot of the library goes with the session, whether or not the music tab is open.
            onSignedOut = { YandexLibrarySnapshots.clear(appContext) },
        )
    }
}
