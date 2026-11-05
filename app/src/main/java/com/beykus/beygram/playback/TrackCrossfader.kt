package com.beykus.beygram.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The track ExoPlayer plays after the watched one: its index in the timeline, its media id and where its audio starts. */
internal data class CrossfadeNext(val index: Int, val mediaId: String, val startMs: Long)

/**
 * Overlaps the end of the playing music track with the start of the next one.
 *
 * The primary player stays the one that the queue and the MediaSession follow. At the switch point it jumps to the next
 * track, which starts silent and ramps up, while a second player, prepared on the outgoing file, plays the outgoing tail
 * and ramps down. Both players are used on the main thread only.
 *
 * Until the switch point fires, the overlap is only a plan. Anything that stops it (pause, lost audio focus, an error, a
 * seek back, a change of track, BG Mix, the setting turned off) releases the second player and gives the primary its
 * full volume back.
 */
internal class TrackCrossfader(
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val busy: () -> Boolean,
    private val newPlayer: () -> ExoPlayer?,
    private val trailingMs: (mediaId: String) -> Long?,
    private val nextAfter: (primary: ExoPlayer, mediaId: String) -> CrossfadeNext?,
) {
    private enum class Outcome { GaveUp, Interrupted, Finished }

    /** One watch per track: its coroutine, its second player, and whether it changed the primary's volume. */
    private class Watch(val mediaId: String) {
        var job: Job? = null
        var tail: ExoPlayer? = null
        var volumeTouched = false

        /** The measured trailing silence, kept once known so the progress ticks stop reading it. */
        var trailing: Long? = null
    }

    private var current: Watch? = null

    /** A track whose watch ended without an overlap. It is not watched again until the track or the setting changes. */
    private var givenUpMediaId: String? = null

    /** True while a watch for [mediaId] runs. The silence skip then leaves that transition to the crossfade. */
    fun isWatching(mediaId: String?): Boolean {
        val watch = current ?: return false
        return mediaId != null && watch.mediaId == mediaId && watch.job?.isActive == true
    }

    /** Starts the watch for the playing track. Called on every progress tick; a watch that is running is left alone. */
    fun watch(primary: ExoPlayer) {
        if (current?.job?.isActive == true) return
        if (!enabled() || busy()) {
            givenUpMediaId = null
            return
        }
        val mediaId = primary.currentMediaItem?.mediaId ?: return
        if (mediaId == givenUpMediaId) return
        val watch = Watch(mediaId)
        current = watch
        watch.job = scope.launch {
            val outcome = try {
                run(primary, watch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RuntimeException) {
                // A failing player must not reach the app's dispatcher. The transition then stays a plain cut.
                Outcome.GaveUp
            }
            release(primary, watch)
            if (current === watch) current = null
            if (outcome == Outcome.GaveUp) givenUpMediaId = watch.mediaId
        }
    }

    /** Stops the watch and any overlap in progress now, and gives the primary its full volume back. */
    fun cancel(primary: ExoPlayer?) {
        val watch = current ?: return
        current = null
        watch.job?.cancel()
        release(primary, watch)
    }

    private fun release(primary: ExoPlayer?, watch: Watch) {
        watch.tail?.release()
        watch.tail = null
        if (watch.volumeTouched) {
            watch.volumeTouched = false
            primary?.volume = 1f
        }
    }

    private suspend fun run(primary: ExoPlayer, watch: Watch): Outcome {
        var lockedSwitch: Long? = null
        var lockedNext: CrossfadeNext? = null
        while (true) {
            if (!enabled() || busy()) return Outcome.Interrupted
            if (!primary.playWhenReady || primary.playerError != null) return Outcome.Interrupted
            if (primary.currentMediaItem?.mediaId != watch.mediaId) return Outcome.Interrupted
            val duration = primary.duration
            if (duration == C.TIME_UNSET || duration <= 0L) {
                delay(CROSSFADE_IDLE_STEP_MS)
                continue
            }
            val position = primary.currentPosition
            val switchAt = lockedSwitch
                ?: crossfadeSwitchMs(duration, knownTrailing(watch))
                ?: return Outcome.GaveUp
            if (position > switchAt + CROSSFADE_TOLERANCE_MS) return Outcome.GaveUp
            val tail = watch.tail
            if (tail == null) {
                val remaining = switchAt - position
                if (remaining > CROSSFADE_PREPARE_LEAD_MS) {
                    delay(crossfadeWaitMs(remaining))
                    continue
                }
                if (remaining < CROSSFADE_PREPARE_MIN_MS) return Outcome.GaveUp
                val next = nextAfter(primary, watch.mediaId) ?: return Outcome.GaveUp
                val player = newPlayer() ?: return Outcome.GaveUp
                watch.tail = player
                prepareTail(player, primary, switchAt)
                lockedSwitch = switchAt
                lockedNext = next
            } else if (position < switchAt - CROSSFADE_TOLERANCE_MS) {
                // Moved back before the switch point: the prepared tail is out of step, so it is prepared again later.
                tail.release()
                watch.tail = null
                lockedSwitch = null
                lockedNext = null
            } else if (position >= switchAt && tail.playbackState == Player.STATE_READY) {
                val next = lockedNext ?: return Outcome.GaveUp
                return fire(primary, watch, tail, next)
            }
            delay(crossfadeWaitMs(switchAt - position))
        }
    }

    /** The track's trailing silence. It is read until it is measured, then kept for the rest of the watch. */
    private fun knownTrailing(watch: Watch): Long {
        watch.trailing?.let { return it }
        val measured = trailingMs(watch.mediaId) ?: return 0L
        watch.trailing = measured
        return measured
    }

    private fun prepareTail(player: ExoPlayer, primary: ExoPlayer, switchAt: Long) {
        val outgoing = checkNotNull(primary.currentMediaItem) { "No outgoing track to overlap" }
        // The equalizer is attached to the primary's audio session. Sharing it lets the tail go through the same effects.
        val sessionId = primary.audioSessionId
        if (sessionId > 0) player.setAudioSessionId(sessionId)
        player.volume = 0f
        player.setMediaItem(outgoing)
        player.setPlaybackParameters(primary.playbackParameters)
        player.prepare()
        player.seekTo(switchAt)
    }

    private suspend fun fire(primary: ExoPlayer, watch: Watch, tail: ExoPlayer, locked: CrossfadeNext): Outcome {
        // The queue may have changed while the tail waited. A different next track keeps this transition a plain cut.
        val target = nextAfter(primary, watch.mediaId)
        if (target == null || target.mediaId != locked.mediaId) return Outcome.GaveUp
        primary.volume = 0f
        watch.volumeTouched = true
        primary.seekTo(target.index, target.startMs)
        tail.volume = 1f
        tail.play()
        val startedAt = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            if (elapsedMs >= CROSSFADE_MS) return Outcome.Finished
            if (primary.playerError != null || !primary.playWhenReady) return Outcome.Interrupted
            if (primary.currentMediaItem?.mediaId != target.mediaId) return Outcome.Interrupted
            val progress = crossfadeProgress(elapsedMs)
            primary.volume = incomingGain(progress)
            tail.volume = outgoingGain(progress)
            delay(CROSSFADE_STEP_MS)
        }
    }
}
