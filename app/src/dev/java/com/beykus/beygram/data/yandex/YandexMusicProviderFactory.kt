package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.data.music.provider.MusicProvider
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderFactory
import com.beykus.beygram.data.music.provider.ProviderWaves
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.screens.music.yandex.YandexLibrarySnapshots

/** Loaded by name from [com.beykus.beygram.data.music.provider.MusicProviders] in the DEV build only. */
class YandexMusicProviderFactory : MusicProviderFactory {
    override fun create(appContext: Context): MusicProvider? {
        if (!YandexConfig.isConfigured) return null
        val http = YandexHttp()
        val provider = YandexMusicProvider(
            store = YandexSessionStore(appContext),
            http = http,
            api = YandexApi(http),
            auth = YandexAuth(http),
            // The snapshot of the library goes with the session, whether or not the music tab is open.
            onSignedOut = { YandexLibrarySnapshots.clear(appContext) },
        )
        // A wave saved before the last restart comes back paused, and a sign-out removes it.
        YandexWaveResume.watchProvider(appContext, provider)
        // «Волна по треку» from the shared track menus, wherever the track is listed.
        ProviderWaves.fromTrack = { context, track ->
            YandexWaveLauncher.start(
                context = context,
                provider = provider,
                seeds = listOf(YandexWave.trackSeed(track.trackId)),
                subtitle = context.getString(R.string.yandex_wave_source_track, track.title),
                startTrack = track,
                isCurrent = { true },
                onDone = {},
                // The track already playing keeps playing: the wave takes over at its position.
                keepPositionMs = PlaybackEngine.state.value.takeIf { it.item?.key == track.key }?.positionMs,
            )
        }
        ProviderWaves.fromArtist = { context, _, ref ->
            YandexWaveLauncher.start(
                context = context,
                provider = provider,
                seeds = listOf(YandexWave.artistSeed(ref.id)),
                subtitle = context.getString(R.string.yandex_wave_source_artist, ref.name),
                startTrack = null,
                isCurrent = { true },
                onDone = {},
            )
        }
        return provider
    }
}
