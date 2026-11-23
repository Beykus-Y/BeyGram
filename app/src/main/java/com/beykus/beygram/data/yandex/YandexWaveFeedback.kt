package com.beykus.beygram.data.yandex

import android.util.Log
import com.beykus.beygram.data.music.provider.ProviderResult
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** The player failed on the current track (network or decoder): that is not a choice of the listener. */
    val failed: Boolean = false,
    /** How many manual Next or queue-row selections the player has seen; a change means the listener left a track. */
    val manualSkips: Long = 0L,
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
 * and when another track replaces it, it counts as finished only if the listener heard most of it and it reached its
 * end (or the last seconds, which a crossfade or a gap skip cuts off). Otherwise it counts as skipped with the seconds
 * really heard: a seek moves the position without time passing, so only playback that real time could have covered counts.
 * A Next or a queue-row pick is a deliberate skip even near the end, and a track the player failed on is not reported.
 */
internal class WaveListenTracker(
    private val clock: () -> Long = System::currentTimeMillis,
    private val isRadioTrack: (String) -> Boolean,
) {
    private var key: String? = null
    private var started = false
    private var positionMs = 0L
    private var durationMs = 0L
    private var ended = false

    /** Milliseconds of the current track actually heard. */
    private var listenedMs = 0L
    private var lastPositionMs = 0L
    private var lastWallMs = 0L
    private var lastPlaying = false

    /** The listener left the current track by Next or by picking another row. */
    private var skippedByListener = false

    /** The player failed on the current track. */
    private var broken = false

    /** The manual-skip count seen last; null until the first state. */
    private var seenManualSkips: Long? = null

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
        val wall = clock()
        val manualBefore = seenManualSkips
        seenManualSkips = state.manualSkips
        // The count moves before the player moves on to the next track, so a change belongs to the track being left.
        if (manualBefore != null && state.manualSkips != manualBefore && key != null && !state.ended) {
            skippedByListener = true
        }
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
            listenedMs = 0L
            lastPlaying = false
            skippedByListener = false
            broken = false
        }
        if (now != null && key == now) {
            // A restart of the same track (Repeat One, Previous) is not a skip of it.
            if (skippedByListener && state.positionMs + STEP_SLACK_MS < lastPositionMs) skippedByListener = false
            if (lastPlaying && state.playing) listenedMs += heardStep(state.positionMs, wall)
            lastPositionMs = state.positionMs
            lastWallMs = wall
            lastPlaying = state.playing
            positionMs = state.positionMs
            if (state.durationMs > 0L) durationMs = state.durationMs
            if (state.ended) ended = true
            // Follows the player: a track the player recovered on and then heard counts again.
            broken = state.failed
            if (state.playing && !started) {
                started = true
                events += WaveListenEvent.Started(now)
            }
        }
        return events
    }

    /**
     * The seconds of playback one step of the position adds. The position may only move as fast as real time passed
     * (a little faster for a higher playback speed); a bigger move is a seek and adds only what the clock allows.
     */
    private fun heardStep(position: Long, wall: Long): Long {
        val moved = position - lastPositionMs
        val elapsed = wall - lastWallMs
        if (moved <= 0L || elapsed <= 0L) return 0L
        return minOf(moved, elapsed * MAX_SPEED + STEP_SLACK_MS)
    }

    private fun close(current: String): WaveListenEvent? {
        if (!started || broken) return null
        val heardEnough = durationMs <= 0L || listenedMs >= durationMs * HEARD_FRACTION
        val nearEnd = durationMs > 0L &&
            (durationMs - positionMs <= END_TOLERANCE_MS || positionMs >= durationMs * END_FRACTION)
        val finished = !skippedByListener && heardEnough && (ended || nearEnd)
        return if (finished) {
            WaveListenEvent.Finished(current, (if (durationMs > 0L) durationMs else positionMs) / 1_000.0)
        } else {
            WaveListenEvent.Skipped(current, listenedMs / 1_000.0)
        }
    }

    private companion object {
        /** A crossfade or a trailing-silence skip leaves the track this early; it still counts as heard to the end. */
        const val END_TOLERANCE_MS = 15_000L
        const val END_FRACTION = 0.95

        /** Share of the track that must really be heard to count as finished. */
        const val HEARD_FRACTION = 0.8

        /** Upper bound of the position step per real second: covers a playback speed up to 2x. */
        const val MAX_SPEED = 2L

        /** Slack for the clock between two progress reports. */
        const val STEP_SLACK_MS = 500L
    }
}

/**
 * Sends the listening of the playing radio to the service: radio started, each track started, finished or skipped,
 * and likes changed while it plays. One radio at a time; a new one replaces the previous tracker. Failures are
 * dropped: feedback only tunes recommendations and never affects playback.
 */
internal object YandexWaveFeedback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The jobs of a running radio: the sender and the tracker. */
    private class Running(val radio: PlaybackRadio, val jobs: List<Job>)

    private val running = ArrayList<Running>()

    /**
     * Stops following [radio] at once, for example when the account signed out: no event is created or sent after this,
     * and the events still queued are dropped. Does nothing when the radio is not followed.
     */
    fun stop(radio: PlaybackRadio) {
        val ended = synchronized(running) {
            running.filter { it.radio === radio }.also { running.removeAll(it) }
        }
        ended.forEach { entry -> entry.jobs.forEach { it.cancel() } }
    }

    /**
     * Follows [radio] until the player leaves it. A previous radio's tracker is not cancelled: it sees the new queue,
     * reports its last track as skipped or finished, and ends by itself.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(provider: YandexMusicProvider, generation: Long, session: YandexWaveSession, radio: PlaybackRadio) =
        start(provider, generation, { session }, radio)

    /**
     * Like the function above, for a radio whose session can change: the session is asked on each event. Events are sent
     * only under [generation], the sign-in the radio started under; the provider checks it under its lock.
     */
    fun start(provider: YandexMusicProvider, generation: Long, sessionOf: () -> YandexWaveSession, radio: PlaybackRadio) {
        val outbox = Channel<YandexWaveEvent>(Channel.UNLIMITED)
        val sender = scope.launch(Dispatchers.IO) {
            for (event in outbox) {
                val sessionId = sessionOf().currentSessionId ?: continue
                val batch = event.trackId?.let { id -> sessionOf().batchOfTrack(id) }
                // An event queued under one sign-in never goes out through another: it is dropped.
                val result = provider.waveFeedback(generation, sessionId, event, batch)
                if (result is ProviderResult.Failure) Log.i(TAG, "feedback ${event.type} failed: ${result.failure}")
            }
        }
        outbox.trySend(YandexWaveEvent(YandexWaveEvent.RADIO_STARTED, System.currentTimeMillis()))
        val tracker = WaveListenTracker { key -> sessionOf().trackOf(key) != null }
        val follower = scope.launch {
            try {
                var likes = ProviderTracks.liked.value
                combine(PlaybackEngine.state, ProviderTracks.liked, PlaybackEngine.manualSkips) { state, liked, skips ->
                    WaveSnapshot(
                        radioActive = PlaybackEngine.activeRadio === radio,
                        key = state.item?.key,
                        playing = state.playing,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        ended = state.ended,
                        failed = state.failed,
                        manualSkips = skips,
                    ) to liked
                }
                    .distinctUntilChanged()
                    // The state that closes the tracker is still handled; nothing after it is.
                    .transformWhile { emit(it); !tracker.closed }
                    .collect { (snapshot, liked) ->
                        for (event in tracker.onState(snapshot)) {
                            val (trackId, _) = sessionOf().trackOf(event.key) ?: continue
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
                                val trackId = sessionOf().trackIdOf(id) ?: continue
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
        val entry = Running(radio, listOf(sender, follower))
        synchronized(running) { running.add(entry) }
        follower.invokeOnCompletion { synchronized(running) { running.remove(entry) } }
    }

    private const val TAG = "BeyGramYandexWave"
}
