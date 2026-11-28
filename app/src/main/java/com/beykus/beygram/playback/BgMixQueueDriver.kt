package com.beykus.beygram.playback

import com.beykus.beygram.media.bgmix.BgMixAudible
import com.beykus.beygram.media.bgmix.BgMixPlayer
import com.beykus.beygram.media.bgmix.BgMixStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Plays a music queue through BG Mix. The engine owns the Media state; this class owns the native session.
 *
 * Only complete local WAV, MP3 or FLAC files qualify (see [isEligible]). The session starts with the tapped track;
 * its successor joins after the deck is loaded, and the rest of the queue follows as playback advances
 * ([appendReady]). Endless queues (a radio with `loadMore`) are allowed: their next pages join through [extendQueue].
 * Streaming, secret and video media stay on the ExoPlayer path.
 *
 * A session can be staged first: it prepares its first track silently while ExoPlayer keeps playing, and only
 * [commitStaged] makes it the active one. Until then [isActive] stays false, so the engine still routes everything to ExoPlayer.
 */
internal class BgMixQueueDriver(private val cacheDir: File) {
    @Volatile private var player: BgMixPlayer? = null
    private var statusSource: StateFlow<BgMixStatus?>? = null
    /** Native queue id -> logical occurrence index. Equal track keys can appear at different indices. */
    private var nativeIndexById: Map<Long, Int> = emptyMap()
    @Volatile private var items: List<PlaybackItem> = emptyList()
    /** First queue index that is not in the native queue yet; equals the queue size once everything joined. */
    private var appendedUntil: Int = 0
    /** Repeat One keeps one track in the native queue, so nothing is appended. */
    private var single: Boolean = false

    private var staged: Staged? = null

    private class Staged(
        val player: BgMixPlayer,
        val items: List<PlaybackItem>,
        val nativeIndexById: Map<Long, Int>,
        val appendedUntil: Int,
        val single: Boolean
    )

    /** Host: attaches the BG Wave session as the tap of the active BG Mix player; false when none is active. */
    fun attachWaveTap(sessionHandle: Long): Boolean {
        val active = player ?: return false
        // Native crossfades start before the UI reports the next deck as current. Never attach
        // a tap to a queue that may mix a private track into an otherwise eligible one.
        if (sessionHandle != 0L && (items.isEmpty() || items.any { !isWaveCaptureEligible(it) })) {
            active.attachWaveSession(0L)
            return false
        }
        return active.attachWaveSession(sessionHandle)
    }

    val isActive: Boolean get() = player != null

    /** Native status stream for the current session; null when stopped. */
    val status: StateFlow<BgMixStatus?>? get() = statusSource

    /** Status of the staged session, while one waits for [commitStaged]. */
    val stagedStatus: StateFlow<BgMixStatus?>? get() = staged?.player?.status

    /** Set when the native output fails during the current session; null while it works or when stopped. */
    val failure: StateFlow<String?>? get() = player?.failure

    /** Queue snapshot the current session was started with. */
    val queueItems: List<PlaybackItem> get() = items

    /** Tracks in the native queue right now (the playing one and the prepared successor). */
    val nativeCount: Int get() = nativeIndexById.size

    /**
     * Local files the native session still needs: everything in its queue and the staged deck of a handover in
     * progress. Cache pruning and eviction must keep these, or a copy disappears under a deck that is being prepared.
     */
    fun pathsInUse(): Set<String> {
        val paths = HashSet<String>()
        items.forEach { item -> item.path.takeIf(::localCopyReady)?.let(paths::add) }
        staged?.items?.forEach { item -> item.path.takeIf(::localCopyReady)?.let(paths::add) }
        return paths
    }

    /** Keys of the active and staged native queue, for an eviction window that must not drop a prepared deck. */
    fun keysInUse(): Set<String> {
        val keys = HashSet<String>()
        nativeIndexById.values.forEach { index -> items.getOrNull(index)?.key?.let(keys::add) }
        staged?.let { session ->
            session.nativeIndexById.values.forEach { index -> session.items.getOrNull(index)?.key?.let(keys::add) }
        }
        return keys
    }

    fun itemAt(index: Int): PlaybackItem? = items.getOrNull(index)

    /**
     * Gives every queue item with [key] the cover [path], in the active session and in the staged one. The engine copies
     * the queue from here on every status tick, so a cover that arrives later must reach this copy too, or the next tick
     * wipes it from the player, the notification and the lock screen.
     */
    fun updateArtwork(key: String, path: String) {
        items = items.withArtwork(key, path)
        staged = staged?.let { next ->
            Staged(next.player, next.items.withArtwork(key, path), next.nativeIndexById, next.appendedUntil, next.single)
        }
    }

    /** Queue index of the item the native track [id] plays; null when that track is not mapped. */
    fun indexOfNative(id: Long): Int? = nativeIndexById[id]

    /** The first queue index that still waits for its file, or null when the whole rest of the queue is in the session. */
    val pendingIndex: Int? get() = appendedUntil.takeIf { player != null && !single && it < items.size }

    /** Whether the native queue holds a track after queue index [index]. */
    fun hasNativeAfter(index: Int): Boolean = nativeIndexById.keys.any { (indexOfNative(it) ?: -1) > index }

    /** A newly appended id may not be present in the asynchronously published status yet. */
    fun hasUnfinishedNativeAfter(index: Int, status: BgMixStatus): Boolean {
        val states = status.queue.associate { it.id to it.state }
        return nativeIndexById.keys.any { id ->
            (indexOfNative(id) ?: -1) > index && states[id] != "played" && states[id] != "failed"
        }
    }

    /**
     * Prepares a paused session for [items] from [startIndex] without making it active: the native side decodes and
     * analyses the first track while the caller keeps playing elsewhere. Returns false when nothing could be staged.
     */
    suspend fun stage(
        items: List<PlaybackItem>,
        startIndex: Int,
        single: Boolean = false,
        fileFor: (PlaybackItem) -> PlaybackItem? = { item -> item.takeIf { isEligible(it) } }
    ): Boolean {
        discardStaged()
        if (starting || !releaseComplete() || player != null || startIndex !in items.indices) return false
        // Prepare only the selected track during staging. A failed first decode must never
        // silently promote its successor and hand over to the wrong song.
        val file = fileFor(items[startIndex]) ?: return false
        val created = BgMixPlayer(cacheDir)
        val caller = currentCoroutineContext()
        starting = true
        var adopted = false
        try {
            // Keep ownership even if the main-thread handover is cancelled while
            // AudioTrack/nativeCreate is blocked. A new startup waits for retirement.
            val ids = withContext(NonCancellable + Dispatchers.IO) {
                caller.ensureActive()
                if (!created.start()) emptyList()
                else {
                    caller.ensureActive()
                    created.setQueue(listOf(File(file.path)))
                }
            }
            caller.ensureActive()
            if (ids.size != 1 || ids[0] == 0L) return false
            val resolved = items.toMutableList().also { it[startIndex] = file }
            staged = Staged(
                player = created,
                items = resolved,
                nativeIndexById = mapOf(ids[0] to startIndex),
                appendedUntil = startIndex + 1,
                single = single
            )
            adopted = true
            return true
        } finally {
            if (!adopted) retire(created)
            starting = false
        }
    }

    /** Makes the staged session the active one, stopping any previous session first. Paused until [resume]. */
    fun commitStaged(): Boolean {
        val next = staged ?: return false
        if (player != null || !releaseComplete()) return false
        staged = null
        items = next.items
        nativeIndexById = next.nativeIndexById
        appendedUntil = next.appendedUntil
        single = next.single
        statusSource = next.player.status
        player = next.player
        return true
    }

    /** Releases a staged session that will not be used. */
    fun discardStaged() {
        val old = staged ?: return
        staged = null
        retire(old.player)
    }

    /** Seeks the staged session before it plays; false when its first track is not loaded yet. */
    suspend fun seekStaged(seconds: Double): Boolean = staged?.player?.seek(seconds) ?: false

    /** Output volume of the staged session: it starts silent and rises during the handover crossfade. */
    fun setStagedVolume(volume: Float) {
        staged?.player?.setOutputVolume(volume)
    }

    /** Starts output of the staged session while the caller still plays; it becomes active only with [commitStaged]. */
    fun playStaged() {
        staged?.player?.play()
    }

    /** Where the staged session is heard, measured against the audio clock; null while that cannot be told yet. */
    suspend fun stagedAudible(): BgMixAudible? = staged?.player?.audible()

    /**
     * Adds the queue items that have become complete files since the session started, in queue order: an item that
     * is still downloading holds back the ones after it, so the native order never differs from the queue. Returns
     * how many items joined, stopping at the current playback window even if the rest is cached.
     */
    fun appendReady(currentIndex: Int, fileFor: (PlaybackItem) -> PlaybackItem?): Int {
        val session = player ?: return 0
        if (single) return 0
        // Do not let a failed first track advance into the next one before the engine sees its failure.
        val snapshot = session.status.value ?: return 0
        if (snapshot.decks.getOrNull(snapshot.master)?.loaded != true) return 0
        var added = 0
        val end = bgMixWindowEndExclusive(items.size, currentIndex, BG_MIX_NATIVE_AHEAD)
        while (appendedUntil < end) {
            val index = appendedUntil
            val file = fileFor(items[index]) ?: break
            val id = session.addTrack(File(file.path))
            if (id == 0L) break
            nativeIndexById = nativeIndexById + (id to index)
            items = items.toMutableList().also { it[index] = file }
            appendedUntil++
            added++
        }
        return added
    }

    /**
     * Adds [added] after the queue of the running session. They join the native queue through [appendReady] as their
     * files finish, so the order and the playing track stay as they are. False when no session runs or it plays one track.
     */
    fun extendQueue(added: List<PlaybackItem>): Boolean {
        if (player == null || single || added.isEmpty()) return false
        items = items + added
        return true
    }

    /**
     * Makes [newItems] the session's queue while [currentKey] keeps playing. Native tracks after it that no longer follow the new
     * order are removed; items that are not native yet join through [appendReady]. Returns false when this cannot be done safely
     * (no session, the playing track missing, a transition running, a track to remove already loaded on a deck, or a native
     * removal refused). After false the native queue may be partly edited, so the caller must stop the session (its fallback does).
     */
    fun retarget(newItems: List<PlaybackItem>, currentKey: String): Boolean {
        val session = player ?: return false
        val oldIndices = uniqueBgMixQueueIndices(items.map { it.key }) ?: return false
        val newIndices = uniqueBgMixQueueIndices(newItems.map { it.key }) ?: return false
        val oldCurrent = oldIndices[currentKey] ?: return false
        val newCurrent = newIndices[currentKey] ?: return false
        if (single) {
            // Repeat One keeps only the playing track natively, so there is nothing to remove or append.
            nativeIndexById = nativeIndexById.mapValues { newCurrent }
            items = newItems
            appendedUntil = newCurrent + 1
            return true
        }
        val plan = bgMixRetargetPlan(
            items.map { it.key }, oldCurrent, appendedUntil,
            newItems.map { it.key }, newCurrent
        ) ?: return false
        if (plan.removeKeys.isNotEmpty()) {
            val status = session.status.value ?: return false
            if (status.transitionActive) return false
            val states = status.queue.associate { it.id to it.state }
            // Check every removal before the first one, so a refusal in the middle does not leave a partial edit behind.
            val removeIds = plan.removeKeys
                .flatMap { key -> nativeIndexById.filterValues { it == oldIndices[key] }.keys }
                .filter { id -> states[id] != "played" && states[id] != "failed" }
            val loadedElsewhere = removeIds.any { id ->
                status.decks.withIndex().any { (deckIndex, deck) ->
                    deckIndex != status.master && deck.loaded && deck.trackId == id
                }
            }
            if (loadedElsewhere) return false
            for (id in removeIds) {
                if (!session.removeTrack(id)) return false
                nativeIndexById = nativeIndexById - id
            }
        }
        // Keep the finished file of an item that was already resolved, instead of the still-growing copy of the new queue.
        val resolved = newItems.map { next ->
            items.getOrNull(oldIndices[next.key] ?: -1)
                ?.takeIf { it.key == next.key && it.growing == null && next.growing != null } ?: next
        }
        val finished = session.status.value?.queue.orEmpty()
            .filter { it.state == "played" || it.state == "failed" }.mapTo(HashSet()) { it.id }
        nativeIndexById = nativeIndexById.mapNotNull { (id, oldIndex) ->
            val index = items.getOrNull(oldIndex)?.let { newIndices[it.key] } ?: return@mapNotNull null
            // A track that already finished must not stand for a fresh occurrence after the master.
            (id to index).takeUnless { id in finished && index > newCurrent }
        }.toMap()
        items = resolved
        appendedUntil = plan.appendedUntil
        return true
    }

    /** The audio session of the native output; null when no session runs. */
    val audioSessionId: Int? get() = player?.audioSessionId?.takeIf { it > 0 }

    fun pause() {
        player?.pause()
    }

    /** Seeks the current BG Mix session; false when it cannot seek right now. */
    suspend fun seek(seconds: Double): Boolean = player?.seek(seconds) ?: false

    fun resume() {
        player?.play()
    }

    /** Lowers or restores the output level; used when another app asks to duck. */
    fun setVolume(volume: Float) {
        player?.setOutputVolume(volume)
    }

    /** Starts the planned transition to the next accepted track. */
    fun next() {
        player?.next()
    }

    /** Immediate native switch to the next track (no crossfade). False when the caller must play it on its own player. */
    fun manualNext(): Boolean = player?.manualNext() == com.beykus.beygram.media.bgmix.BgMixNative.MANUAL_ACCEPTED

    /** Immediate native switch to queue index [index]. False when that track is not in the native queue or not ready. */
    fun manualSelect(index: Int): Boolean {
        val p = player ?: return false
        val id = nativeIndexById.entries.firstOrNull { it.value == index }?.key ?: return false
        return p.manualSelect(id) == com.beykus.beygram.media.bgmix.BgMixNative.MANUAL_ACCEPTED
    }

    /** Detaches immediately; the release executor retains ownership through native destruction. */
    fun stop() {
        val old = player ?: return
        player = null
        statusSource = null
        nativeIndexById = emptyMap()
        items = emptyList()
        appendedUntil = 0
        single = false
        retire(old)
    }

    private val releaser = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bgmix-release").apply { isDaemon = true }
    }

    private enum class ReleaseState { Idle, Pending, Failed }

    @Volatile
    private var releaseState = ReleaseState.Idle

    private var starting = false // main-thread ownership, including cancelled startup
    val isRetiring: Boolean get() = starting || releaseState == ReleaseState.Pending

    private fun retire(old: BgMixPlayer) {
        old.requestStop()
        releaseState = ReleaseState.Pending
        releaser.execute {
            releaseState = if (runCatching { old.release() }.isSuccess) ReleaseState.Idle else ReleaseState.Failed
        }
    }

    /** A pending/failed release never blocks a caller or allows another native host to accumulate. */
    private fun releaseComplete(): Boolean = releaseState == ReleaseState.Idle

    fun failedIndex(status: BgMixStatus, atOrAfter: Int): Int? = status.queue.asSequence()
        .filter { it.state == "failed" }
        .mapNotNull { indexOfNative(it.id) }
        .filter { it >= atOrAfter }
        .minOrNull()

    companion object {
        private val EXTENSIONS = setOf("wav", "mp3", "flac")

        private val MIME_TYPES = setOf(
            "audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg", "audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/wave"
        )

        /**
         * A music item that BG Mix will be able to play once its file is complete: the format is right, even if the file is
         * still downloading. The player shows the BG Mix mark for such an item from the first second.
         */
        fun isCandidate(item: PlaybackItem): Boolean =
            item.isMusic &&
                !item.systemPrivate &&
                (item.path.substringAfterLast('.', "").lowercase() in EXTENSIONS || item.mimeType.lowercase() in MIME_TYPES)

        /** A queue item that BG Mix can decode from a finished local file. */
        fun isEligible(item: PlaybackItem): Boolean =
            item.isMusic &&
                !item.systemPrivate &&
                item.growing == null &&
                item.path.isNotBlank() &&
                item.path.substringAfterLast('.', "").lowercase() in EXTENSIONS &&
                localCopyReady(item.path)
    }
}

/** Only one successor is decoded in advance; downloads have a separate window. */
internal const val BG_MIX_NATIVE_AHEAD = 1

/** Exclusive end of a window anchored to the current track, not to the prepared tail. */
internal fun bgMixWindowEndExclusive(queueSize: Int, currentIndex: Int, ahead: Int): Int {
    if (currentIndex !in 0 until queueSize || ahead < 0) return 0
    return currentIndex + 1 + minOf(ahead, queueSize - currentIndex - 1)
}

/** A key-only queue edit cannot identify which duplicate occurrence should be kept playing. */
internal fun uniqueBgMixQueueIndices(keys: List<String>): Map<String, Int>? {
    val indices = HashMap<String, Int>(keys.size)
    keys.forEachIndexed { index, key ->
        if (indices.put(key, index) != null) return null
    }
    return indices
}

/**
 * The queue index an edit must keep for the occurrence that is playing. The exact occurrence wins (the same track may
 * stand in the queue twice), then its key. Only then does the old index apply, clamped to the edited queue: a removed
 * incoming track must never turn into a blind jump to index 0.
 */
internal fun bgMixLogicalIndex(
    queue: List<PlaybackItem>,
    occurrenceId: String?,
    key: String?,
    fallbackIndex: Int,
): Int {
    if (queue.isEmpty()) return -1
    if (occurrenceId != null) {
        val exact = queue.indexOfFirst { it.occurrenceId == occurrenceId }
        if (exact >= 0) return exact
    }
    if (key != null) {
        val sameKey = queue.indexOfFirst { it.key == key }
        if (sameKey >= 0) return sameKey
    }
    return fallbackIndex.coerceIn(0, queue.lastIndex)
}

/** Native tracks to remove, last first, and the queue index where the native queue continues after the playing track. */
internal data class BgMixRetargetPlan(val removeKeys: List<String>, val appendedUntil: Int)

/**
 * How the native queue follows an edited logical queue. The native queue holds the playing track and the items after it up to
 * [oldAppendedUntil] (exclusive). Native items that still match the new order right after the playing track stay; the rest are
 * removed, last first, and join again later in the new order. Null when the playing track is not in both queues.
 */
internal fun bgMixRetargetPlan(
    oldKeys: List<String>, oldCurrent: Int, oldAppendedUntil: Int,
    newKeys: List<String>, newCurrent: Int
): BgMixRetargetPlan? {
    if (oldCurrent !in oldKeys.indices || newCurrent !in newKeys.indices || oldKeys[oldCurrent] != newKeys[newCurrent]) return null
    val nativeUpcoming = oldKeys.subList(oldCurrent + 1, oldAppendedUntil.coerceIn(oldCurrent + 1, oldKeys.size))
    var kept = 0
    while (kept < nativeUpcoming.size && newKeys.getOrNull(newCurrent + 1 + kept) == nativeUpcoming[kept]) kept++
    return BgMixRetargetPlan(nativeUpcoming.drop(kept).reversed(), newCurrent + 1 + kept)
}

/**
 * Indexes from [startIndex] on while each one is ready, in order; null when the item at [startIndex] itself is not ready
 * or the index is out of range. [ready] holds the complete file for each queue position, or null while it is not one.
 * The items after the first gap join the session later, as their files finish.
 */
internal fun bgMixReadyPrefix(ready: List<PlaybackItem?>, startIndex: Int): List<Int>? {
    if (startIndex !in ready.indices || ready[startIndex] == null) return null
    return (startIndex until ready.size).takeWhile { ready[it] != null }
}

/** The queue with the cover [path] on every item of [key]; other items are returned as they are. */
internal fun List<PlaybackItem>.withArtwork(key: String, path: String): List<PlaybackItem> =
    map { item -> if (item.key == key && item.artworkPath != path) item.copy(artworkPath = path) else item }

/**
 * How far BG Mix is ahead of ExoPlayer, in milliseconds: positive when the audio BG Mix plays now is further into the
 * track than ExoPlayer's position at the same moment.
 */
internal fun bgMixAlignmentOffsetMs(audible: BgMixAudible, exoMs: Double): Double =
    audible.audibleSeconds * 1_000.0 - exoMs

/**
 * Where BG Mix must be sought so that, once the audio it has already queued has played out, it sits at ExoPlayer's
 * position: ExoPlayer plays on through that same queued time meanwhile.
 */
internal fun bgMixAlignmentSeekMs(audible: BgMixAudible, exoMs: Double): Double =
    exoMs + audible.bufferedSeconds * 1_000.0

/**
 * Whether a BG Mix queue that has played out its known tracks should wait for the next page of an endless source instead of
 * reporting the end: the source can load more, no queue item is still waiting for its file, Repeat is off, and a page request
 * is running or scheduled. A temporarily empty radio page is not the end of the radio.
 */
internal fun bgMixAwaitsNextPage(
    finished: Boolean,
    pendingIndex: Int?,
    endless: Boolean,
    repeat: PlaybackRepeatMode,
    pageInFlight: Boolean
): Boolean = finished && pendingIndex == null && endless && repeat == PlaybackRepeatMode.Off && pageInFlight
