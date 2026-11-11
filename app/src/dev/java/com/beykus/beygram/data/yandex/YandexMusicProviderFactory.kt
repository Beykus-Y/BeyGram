package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.data.music.provider.MusicProviderFactory

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
        )
    }
}
