package com.beykus.beygram.playback

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the saved music session current for the whole process, so it does not depend on a screen being open: a
 * track change, pause, seek or mode change is written at once, and while music plays the position is written every
 * [PLAYING_WRITE_INTERVAL_MS]. Only the interface restores the session after sign-in; this class only writes.
 */
internal class MusicSessionPersistence(
    context: Context,
    private val state: StateFlow<PlaybackState>,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** The account whose session the next start restores; music of any other kind clears its saved session. */
    private val activeAccount: () -> String? = { null },
) {
    private val store = MusicSessionStore(context.applicationContext)
    private var job: Job? = null
    private var lastSignature: PersistSignature? = null
    private var lastWrittenAt = 0L
    private var lastSaved: SavedMusicSession? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch { state.collect { write(it, force = false) } }
    }

    /** Writes the current state immediately, for example right before the player is closed. */
    fun flush() {
        write(state.value, force = true)
    }

    /** Removes the saved session of [owner] and forgets what was written, so the next change is written again. */
    fun forget(owner: String) {
        lastSignature = null
        lastSaved = null
        store.clear(owner)
    }

    private fun write(snapshot: PlaybackState, force: Boolean) {
        val item = snapshot.item
        // Voice messages and video notes are not music: the music session stays as it was for them.
        if (item == null || !item.isMusic) return
        val signature = persistSignature(snapshot)
        val now = clock()
        val changed = signature != lastSignature
        val periodic = snapshot.playing && now - lastWrittenAt >= PLAYING_WRITE_INTERVAL_MS
        if (!force && !changed && !periodic) return
        lastSignature = signature
        val session = saveableMusicSession(snapshot, item.accountId)
        if (session == null) {
            // Music that cannot be restored (a Yandex track, a search result, profile music, a mixed queue or secret media)
            // must not leave an older Telegram session behind: the next start would play a track from long ago.
            if (changed || force) activeAccount()?.let { owner -> store.clear(owner) }
            lastSaved = null
            return
        }
        if (session == lastSaved && !periodic) return
        store.save(session)
        lastSaved = session
        lastWrittenAt = now
    }

    internal companion object {
        const val PLAYING_WRITE_INTERVAL_MS = 12_000L
    }
}

/** What forces an immediate write. The position is part of it only while paused, where a change means a seek. */
internal data class PersistSignature(
    val itemKey: String?,
    /** The same list instance is reused between position ticks, so comparing it is O(1) in the common case. */
    val queue: List<PlaybackItem>,
    val queueIndex: Int,
    val playing: Boolean,
    val shuffle: Boolean,
    val repeat: PlaybackRepeatMode,
    val pausedPositionSecond: Long
)

internal fun persistSignature(state: PlaybackState): PersistSignature = PersistSignature(
    itemKey = state.item?.key,
    queue = state.queue,
    queueIndex = state.queueIndex,
    playing = state.playing,
    shuffle = state.shuffleEnabled,
    repeat = state.repeatMode,
    pausedPositionSecond = if (state.playing) 0L else state.positionMs / 1_000L
)
