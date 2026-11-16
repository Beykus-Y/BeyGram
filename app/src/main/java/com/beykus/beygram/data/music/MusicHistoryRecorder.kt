package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Decides when a started track has been listened to enough to enter the history. */
fun interface ListenRule {
    fun qualifies(listenedMs: Long, durationMs: Long): Boolean

    companion object {
        /** A listen counts as soon as the track starts. */
        val Immediate = ListenRule { _, _ -> true }

        /** Thirty seconds of sound, or half of a shorter track, whichever comes first. */
        val Standard = ListenRule { listenedMs, durationMs -> listenedMs >= listenThresholdMs(durationMs) }
    }
}

const val LISTEN_THRESHOLD_MS = 30_000L

/** The sound a track must play before it counts: [LISTEN_THRESHOLD_MS], or half of its length when that is shorter. */
fun listenThresholdMs(durationMs: Long): Long =
    if (durationMs > 0L) minOf(LISTEN_THRESHOLD_MS, durationMs / 2L) else LISTEN_THRESHOLD_MS

/** The position has fallen back after the track had played to its end: the same track began again (repeat one). */
internal fun restartedFromTheEnd(previousPositionMs: Long, positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0L && durationMs - previousPositionMs <= 5_000L && positionMs < previousPositionMs - 5_000L

/**
 * Counts a listen once a music item has really been listened to: it has started and has played for the time [rule]
 * asks for (by default 30 seconds or half of the track). Time is counted while the sound plays, not from the position,
 * so skipping through a list adds nothing and seeking cannot fake a listen. Pause and resume inside the same item add
 * to the same count. A new item, a stop followed by a new start, or the same track beginning again (repeat) opens a new
 * count. Secret, view-once, non-music items and items of another account are ignored.
 */
object MusicHistoryRecorder {
    /** Runs the observer in [scope] for as long as the scope lives. */
    fun start(
        scope: CoroutineScope,
        repository: MusicLibraryRepository,
        playback: StateFlow<PlaybackState> = PlaybackEngine.state
    ): Job = scope.launch { observe(repository, playback) }

    /**
     * Collects playback states until the calling coroutine is cancelled. Counted starts are queued and written one after
     * another by a separate coroutine, so collecting never waits for the disk and the history keeps the order of the starts.
     */
    suspend fun observe(
        repository: MusicLibraryRepository,
        playback: StateFlow<PlaybackState> = PlaybackEngine.state,
        rule: ListenRule = ListenRule.Standard,
        nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
    ) {
        coroutineScope {
            val starts = Channel<MusicTrackRef>(Channel.UNLIMITED)
            launch {
                for (track in starts) repository.recordPlay(track)
            }
            var seeded = false
            var lastKey: String? = null
            var countedKey: String? = null
            var listenedMs = 0L
            var lastTickAt = 0L
            var wasPlaying = false
            var lastPositionMs = 0L
            playback.collect { state ->
                val item = state.item
                val key = item?.occurrenceId
                val now = nowMs()
                // Only sound that actually played counts. A long gap between two states (the process slept) is capped.
                if (wasPlaying && key == lastKey) listenedMs += (now - lastTickAt).coerceIn(0L, MAX_TICK_GAP_MS)
                lastTickAt = now
                wasPlaying = state.playing
                if (!seeded) {
                    // A start that was already audible before observation began was not started by this observer.
                    seeded = true
                    lastKey = key
                    countedKey = if (state.started) key else null
                    listenedMs = 0L
                } else if (key != lastKey) {
                    // A different item, or none, opens a new counting window.
                    lastKey = key
                    countedKey = null
                    listenedMs = 0L
                } else if (countedKey == key &&
                    restartedFromTheEnd(lastPositionMs, state.positionMs, state.durationMs)
                ) {
                    // The same track began again: it is a new listen.
                    countedKey = null
                    listenedMs = 0L
                }
                lastPositionMs = state.positionMs
                if (item == null || !state.started || key == countedKey) return@collect
                val durationMs = state.durationMs.takeIf { it > 0L } ?: (item.durationSeconds * 1_000L)
                if (!rule.qualifies(listenedMs, durationMs)) return@collect
                countedKey = key
                if (item.accountId != repository.activeAccountId) return@collect
                val track = item.toTrackRef() ?: return@collect
                starts.trySend(track)
            }
        }
    }

    private const val MAX_TICK_GAP_MS = 2_000L
}
