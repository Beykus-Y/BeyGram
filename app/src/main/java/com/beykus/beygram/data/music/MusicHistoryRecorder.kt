package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Counts a listen when a music item actually starts playing: the first moment its state reports `started` after the
 * item appears. Pause, resume and seeking inside the same item count nothing. A new item, or a stop followed by a new
 * start, counts again. Secret, view-once, non-music items and items of another account are ignored.
 */
object MusicHistoryRecorder {
    /** Runs the observer in [scope] for as long as the scope lives. */
    fun start(
        scope: CoroutineScope,
        repository: MusicLibraryRepository,
        playback: StateFlow<PlaybackState> = PlaybackEngine.state
    ): Job = scope.launch { observe(repository, playback) }

    /** Collects playback states until the calling coroutine is cancelled. */
    suspend fun observe(
        repository: MusicLibraryRepository,
        playback: StateFlow<PlaybackState> = PlaybackEngine.state
    ) {
        var seeded = false
        var lastKey: String? = null
        var countedKey: String? = null
        playback.collect { state ->
            val item = state.item
            val key = item?.occurrenceId
            if (!seeded) {
                // A start that was already audible before observation began was not started by this observer.
                seeded = true
                lastKey = key
                countedKey = if (state.started) key else null
            } else if (key != lastKey) {
                // A different item, or none, opens a new counting window.
                lastKey = key
                countedKey = null
            }
            if (item == null || !state.started || key == countedKey) return@collect
            countedKey = key
            if (item.accountId != repository.activeAccountId) return@collect
            val track = item.toTrackRef() ?: return@collect
            repository.recordPlay(track)
        }
    }
}
