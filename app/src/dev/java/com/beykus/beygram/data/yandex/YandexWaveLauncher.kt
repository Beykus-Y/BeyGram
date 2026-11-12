package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.playback.PlaybackRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Opens a «Моя волна» for some seeds (the personal wave, or one track) and plays it. */
internal object YandexWaveLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Opens a radio session for [seeds] and plays its first batch; the player asks the session for more as the queue
     * runs low, so it never ends. [startTrack], when the session gives it, plays first. [subtitle] is what the player
     * header names as the source. [isCurrent] turns false once a newer selection superseded this start, and then the
     * wave is not played. [onDone] gets the failure, or null, once the first batch is handled.
     */
    fun start(
        context: Context,
        provider: YandexMusicProvider,
        seeds: List<String>,
        subtitle: String,
        startTrack: ProviderTrack?,
        isCurrent: () -> Boolean,
        onDone: (MusicProviderFailure?) -> Unit,
    ) {
        val app = context.applicationContext
        scope.launch {
            val session = YandexWaveSession(
                seeds = seeds,
                start = { s, queue -> provider.waveStart(s, queue) },
                next = { id, queue -> provider.waveNext(id, queue) },
            )
            when (val first = session.first()) {
                is ProviderResult.Failure -> onDone(first.failure)
                is ProviderResult.Success -> when {
                    !isCurrent() -> onDone(null)
                    first.value.none { it.isAvailable } -> onDone(MusicProviderFailure.NotFound)
                    else -> {
                        // Stable sort: the chosen track moves to the front, the rest keep the service's order.
                        val ordered = startTrack?.let { seed -> first.value.sortedBy { it.trackId != seed.trackId } } ?: first.value
                        val radio = PlaybackRadio(
                            title = app.getString(R.string.yandex_wave_title),
                            subtitle = subtitle,
                        )
                        ProviderArtwork.playTracks(app, ordered, 0, { session.more() }, radio)
                        // After the queue started, so the tracker sees the radio as the player's queue.
                        YandexWaveFeedback.start(provider, session, radio)
                        onDone(null)
                    }
                }
            }
        }
    }
}
