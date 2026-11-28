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

    /** The radio of the wave started last: its header caption follows a settings change. */
    @Volatile
    private var activeRadio: PlaybackRadio? = null

    /** A session whose requests run only under [generation], the sign-in the wave started under; the provider checks it under its lock. */
    private fun openSession(
        provider: YandexMusicProvider,
        generation: Long,
        seeds: List<String>,
        given: Collection<String>,
        origins: Map<String, WaveTrackOrigin> = emptyMap(),
    ) = YandexWaveSession(
        seeds = seeds,
        start = { s, queue -> provider.waveStart(generation, s, queue) },
        next = { id, queue -> provider.waveNext(generation, id, queue) },
        initialGiven = given,
        initialOrigins = origins,
    )

    /** The settings change started last; an older one that returns later must not replace it. */
    @Volatile
    private var settingsChange = 0

    /**
     * Changes the seeds of the radio that plays: the upcoming tracks are replaced, the playing one keeps playing. The
     * header caption becomes [subtitle], and the saved wave keeps the new seeds. [onDone] gets true once the new tracks
     * are queued; false means nothing was changed for the listener.
     */
    fun changeSeeds(context: Context, seeds: List<String>, subtitle: String, onDone: (Boolean) -> Unit) {
        val router = active
        val radio = activeRadio
        if (router == null || radio == null ||
            PlaybackEngine.activeRadio !== radio || !PlaybackEngine.currentQueueIsEndless()
        ) {
            onDone(false)
            return
        }
        val mine = ++settingsChange
        val app = context.applicationContext
        scope.launch {
            // A newer settings change (or a new radio) supersedes this one; its request may finish later.
            when (val result = router.swap(seeds) { mine == settingsChange }) {
                null -> onDone(false)
                is ProviderResult.Failure -> onDone(false)
                is ProviderResult.Success -> {
                    if (PlaybackEngine.activeRadio !== radio) {
                        onDone(false)
                        return@launch
                    }
                    val fresh = result.value.filter { it.isAvailable }
                    if (fresh.isEmpty()) {
                        onDone(false)
                    } else {
                        ProviderTracks.remember(fresh)
                        PlaybackEngine.replaceYandexUpcoming(app, fresh, radio) { router.more() }
                        radio.subtitle = subtitle
                        YandexWaveResume.settingsChanged(seeds, subtitle)
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
        val generation = provider.accountGeneration()
        if (generation == null) {
            onDone(MusicProviderFailure.NotSignedIn)
            return
        }
        scope.launch {
            val session = openSession(provider, generation, seeds, emptyList())
            when (val first = session.first()) {
                is ProviderResult.Failure -> onDone(first.failure)
                is ProviderResult.Success -> when {
                    !isCurrent() -> onDone(null)
                    // Signed out, or signed in again, while the first batch loaded: the wave is not played.
                    provider.accountGeneration() != generation -> onDone(MusicProviderFailure.NotSignedIn)
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
                        val router = YandexWaveRouter(session) { s, given, origins ->
                            openSession(provider, generation, s, given, origins)
                        }
                        active = router
                        activeRadio = radio
                        ProviderArtwork.playTracks(app, leading, 0, { router.more() }, radio, keepPositionMs)
                        YandexWaveResume.attach(app, generation, { router.current }, radio, seeds, subtitle)
                        // After the queue started, so the tracker sees the radio as the player's queue.
                        YandexWaveFeedback.start(provider, generation, { router.current }, radio)
                        onDone(null)
                    }
                }
            }
        }
    }
}
