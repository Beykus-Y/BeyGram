package com.beykus.beygram.data.yandex

import android.util.Log
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/** What the listening tracker needs from one player state. */
internal data class WaveSnapshot(
    /** The player still plays the queue of this radio. */
    val radioActive: Boolean,
    val key: String?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

/** A listening fact about a track of the radio, before it becomes a request. */
internal sealed interface WaveListenEvent {
    val key: String

    data class Started(override val key: String) : WaveListenEvent
    data class Finished(override val key: String, val playedSeconds: Double) : WaveListenEvent
    data class Skipped(override val key: String, val playedSeconds: Double) : WaveListenEvent
}

/**
 * Turns player states into listening facts for the tracks of one radio: a track counts as started once it plays,
 * and when another track replaces it, it counts as finished if it reached its end (or the last seconds, which a
 * crossfade or a gap skip cuts off), otherwise as skipped with the seconds heard.
 */
internal class WaveListenTracker(private val isRadioTrack: (String) -> Boolean) {
    private var key: String? = null
    private var started = false
    private var positionMs = 0L
    private var durationMs = 0L
    private var ended = false

    /** The radio has been the player's queue at least once; before that, an inactive state is just a late start. */
    private var seenActive = false

    /** True once the radio stopped being the player's queue; no further states matter. */
    var closed = false
        private set

    fun onState(state: WaveSnapshot): List<WaveListenEvent> {
        if (closed) return emptyList()
        if (state.radioActive) seenActive = true
        if (!seenActive) return emptyList()
        val events = mutableListOf<WaveListenEvent>()
        val current = key
        if (current != null && (!state.radioActive || state.key != current)) {
            close(current)?.let(events::add)
            key = null
        }
        if (!state.radioActive) {
            closed = true
            return events
        }
        val now = state.key
        if (key == null && now != null && isRadioTrack(now)) {
            key = now
            started = false
            positionMs = 0L
            durationMs = 0L
            ended = false
        }
        if (key != null && key == now) {
            positionMs = state.positionMs
            if (state.durationMs > 0L) durationMs = state.durationMs
            if (state.ended) ended = true
            if (state.playing && !started) {
                started = true
                events += WaveListenEvent.Started(now)
            }
        }
        return events
    }

    private fun close(current: String): WaveListenEvent? {
        if (!started) return null
        val seconds = positionMs / 1_000.0
        val nearEnd = durationMs > 0L &&
            (durationMs - positionMs <= END_TOLERANCE_MS || positionMs >= durationMs * END_FRACTION)
        return if (ended || nearEnd) {
            WaveListenEvent.Finished(current, (if (durationMs > 0L) durationMs else positionMs) / 1_000.0)
        } else {
            WaveListenEvent.Skipped(current, seconds)
        }
    }

    private companion object {
        /** A crossfade or a trailing-silence skip leaves the track this early; it still counts as heard to the end. */
        const val END_TOLERANCE_MS = 15_000L
        const val END_FRACTION = 0.95
    }
}

/**
 * Sends the listening of the playing radio to the service: radio started, each track started, finished or skipped,
 * and likes changed while it plays. One radio at a time; a new one replaces the previous tracker. Failures are
 * dropped: feedback only tunes recommendations and never affects playback.
 */
internal object YandexWaveFeedback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Follows [radio] until the player leaves it. A previous radio's tracker is not cancelled: it sees the new queue,
     * reports its last track as skipped or finished, and ends by itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(provider: YandexMusicProvider, session: YandexWaveSession, radio: PlaybackRadio) {
        val outbox = Channel<YandexWaveEvent>(Channel.UNLIMITED)
        scope.launch(Dispatchers.IO) {
            for (event in outbox) {
                val sessionId = session.currentSessionId ?: continue
                val batch = event.trackId?.let { id -> session.batchOfTrack(id) }
                val result = provider.waveFeedback(sessionId, event, batch)
                if (result is ProviderResult.Failure) Log.i(TAG, "feedback ${event.type} failed: ${result.failure}")
            }
        }
        outbox.trySend(YandexWaveEvent(YandexWaveEvent.RADIO_STARTED, System.currentTimeMillis()))
        val tracker = WaveListenTracker { key -> session.trackOf(key) != null }
        scope.launch {
            try {
                var likes = ProviderTracks.liked.value
                combine(PlaybackEngine.state, ProviderTracks.liked) { state, liked -> state to liked }
                    .map { (state, liked) ->
                        WaveSnapshot(
                            radioActive = PlaybackEngine.activeRadio === radio,
                            key = state.item?.key,
                            playing = state.playing,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            ended = state.ended,
                        ) to liked
                    }
                    .distinctUntilChanged()
                    // The state that closes the tracker is still handled; nothing after it is.
                    .transformWhile { emit(it); !tracker.closed }
                    .collect { (snapshot, liked) ->
                        for (event in tracker.onState(snapshot)) {
                            val (trackId, _) = session.trackOf(event.key) ?: continue
                            val now = System.currentTimeMillis()
                            outbox.trySend(
                                when (event) {
                                    is WaveListenEvent.Started -> YandexWaveEvent(YandexWaveEvent.TRACK_STARTED, now, trackId)
                                    is WaveListenEvent.Finished ->
                                        YandexWaveEvent(YandexWaveEvent.TRACK_FINISHED, now, trackId, event.playedSeconds)
                                    is WaveListenEvent.Skipped ->
                                        YandexWaveEvent(YandexWaveEvent.SKIP, now, trackId, event.playedSeconds)
                                }
                            )
                        }
                        if (liked !== likes && snapshot.radioActive) {
                            for ((id, value) in liked) {
                                // A like the app only learned now (a new batch) is not a change made by the user.
                                val before = likes[id] ?: continue
                                if (before == value) continue
                                val trackId = session.trackIdOf(id) ?: continue
                                val type = if (value) YandexWaveEvent.LIKE else YandexWaveEvent.UNLIKE
                                outbox.trySend(YandexWaveEvent(type, System.currentTimeMillis(), trackId))
                            }
                        }
                        likes = liked
                    }
            } finally {
                // Let the events already queued go out, then stop the sender.
                outbox.close()
            }
        }
    }

    private const val TAG = "BeyGramYandexWave"
}
