package com.beykus.beygram.data.yandex

import android.content.Context
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.MusicProviderFailure
import com.beykus.beygram.data.music.provider.ProviderArtwork
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Opens a «Моя волна» for some seeds (the personal wave, or one track) and plays it. */
internal object YandexWaveLauncher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The router of the radio started last; a settings change swaps the upcoming tracks of that radio. */
    @Volatile
    private var active: YandexWaveRouter? = null

    private fun openSession(provider: YandexMusicProvider, seeds: List<String>, given: Collection<String>) = YandexWaveSession(
        seeds = seeds,
        start = { s, queue -> provider.waveStart(s, queue) },
        next = { id, queue -> provider.waveNext(id, queue) },
        initialGiven = given,
    )

    /**
     * Changes the seeds of the radio that plays: the upcoming tracks are replaced, the playing one keeps playing. [onDone]
     * gets true once the new tracks are queued; false means nothing was changed for the listener.
     */
    fun changeSeeds(context: Context, seeds: List<String>, onDone: (Boolean) -> Unit) {
        val router = active
        if (router == null || !PlaybackEngine.currentQueueIsEndless()) {
            onDone(false)
            return
        }
        val app = context.applicationContext
        scope.launch {
            when (val result = router.swap(seeds)) {
                is ProviderResult.Failure -> onDone(false)
                is ProviderResult.Success -> {
                    val fresh = result.value.filter { it.isAvailable }
                    if (fresh.isEmpty()) {
                        onDone(false)
                    } else {
                        ProviderTracks.remember(fresh)
                        PlaybackEngine.replaceYandexUpcoming(app, fresh) { router.more() }
                        onDone(true)
                    }
                }
            }
        }
    }

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
        /** Position of [startTrack] when it is already playing: the wave takes over there and the track is not restarted. */
        keepPositionMs: Long? = null,
    ) {
        val app = context.applicationContext
        scope.launch {
            val session = openSession(provider, seeds, emptyList())
            when (val first = session.first()) {
                is ProviderResult.Failure -> onDone(first.failure)
                is ProviderResult.Success -> when {
                    !isCurrent() -> onDone(null)
                    first.value.none { it.isAvailable } -> onDone(MusicProviderFailure.NotFound)
                    else -> {
                        // Stable sort: the chosen track moves to the front, the rest keep the service's order.
                        val ordered = startTrack?.let { seed -> first.value.sortedBy { it.trackId != seed.trackId } } ?: first.value
                        // A track that is playing must lead the queue, or the queue would restart it, so it is put first.
                        val leading = if (startTrack != null && keepPositionMs != null && ordered.none { it.trackId == startTrack.trackId }) {
                            listOf(startTrack) + ordered
                        } else {
                            ordered
                        }
                        val radio = PlaybackRadio(
                            title = app.getString(R.string.yandex_wave_title),
                            subtitle = subtitle,
                        )
                        val router = YandexWaveRouter(session) { s, given -> openSession(provider, s, given) }
                        active = router
                        ProviderArtwork.playTracks(app, leading, 0, { router.more() }, radio, keepPositionMs)
                        YandexWaveResume.attach(app, session, radio, seeds, subtitle)
                        // After the queue started, so the tracker sees the radio as the player's queue.
                        YandexWaveFeedback.start(provider, { router.current }, radio)
                        onDone(null)
                    }
                }
            }
        }
    }
}
