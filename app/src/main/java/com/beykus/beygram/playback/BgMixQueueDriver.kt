package com.beykus.beygram.playback

import com.beykus.beygram.media.bgmix.BgMixAudible
import com.beykus.beygram.media.bgmix.BgMixPlayer
import com.beykus.beygram.media.bgmix.BgMixStatus
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Plays a music queue through BG Mix. The engine owns the Media state; this class owns the native session.
 *
 * Only complete local WAV, MP3 or FLAC files qualify (see [isEligible]). The session starts with the tapped track and the
 * finished files right after it; the rest of the queue joins the native queue in order as each file finishes downloading
 * ([appendReady]). Streaming, secret and video media stay on the ExoPlayer path.
 *
 * A session can be staged first: it prepares its first track silently while ExoPlayer keeps playing, and only
 * [commitStaged] makes it the active one. Until then [isActive] stays false, so the engine still routes everything to ExoPlayer.
 */
internal class BgMixQueueDriver(private val cacheDir: File) {
    private var player: BgMixPlayer? = null
    private var statusSource: StateFlow<BgMixStatus?>? = null
    /** Native queue id -> index in the queue passed to [start]. */
    private var indexByNativeId: Map<Long, Int> = emptyMap()
    private var items: List<PlaybackItem> = emptyList()
    /** First queue index that is not in the native queue yet; equals the queue size once everything joined. */
    private var appendedUntil: Int = 0
    /** Repeat One keeps one track in the native queue, so nothing is appended. */
    private var single: Boolean = false

    private var staged: Staged? = null

    private class Staged(
        val player: BgMixPlayer,
        val items: List<PlaybackItem>,
        val indexByNativeId: Map<Long, Int>,
        val appendedUntil: Int,
        val single: Boolean
    )

    val isActive: Boolean get() = player != null

    /** Native status stream for the current session; null when stopped. */
    val status: StateFlow<BgMixStatus?>? get() = statusSource

    /** Status of the staged session, while one waits for [commitStaged]. */
    val stagedStatus: StateFlow<BgMixStatus?>? get() = staged?.player?.status

    /** Set when the native output fails during the current session; null while it works or when stopped. */
    val failure: StateFlow<String?>? get() = player?.failure

    /** Queue snapshot the current session was started with. */
    val queueItems: List<PlaybackItem> get() = items

    fun itemAt(index: Int): PlaybackItem? = items.getOrNull(index)

    /**
     * Gives every queue item with [key] the cover [path], in the active session and in the staged one. The engine copies
     * the queue from here on every status tick, so a cover that arrives later must reach this copy too, or the next tick
     * wipes it from the player, the notification and the lock screen.
     */
    fun updateArtwork(key: String, path: String) {
        items = items.withArtwork(key, path)
        staged = staged?.let { next ->
            Staged(next.player, next.items.withArtwork(key, path), next.indexByNativeId, next.appendedUntil, next.single)
        }
    }

    fun indexOfNative(id: Long): Int? = indexByNativeId[id]

    /** The first queue index that still waits for its file, or null when the whole rest of the queue is in the session. */
    val pendingIndex: Int? get() = appendedUntil.takeIf { player != null && !single && it < items.size }

    /** Whether the native queue holds a track after queue index [index]. */
    fun hasNativeAfter(index: Int): Boolean = indexByNativeId.values.any { it > index }

    /**
     * Starts [items] from [startIndex] and plays at once. [fileFor] returns the item as a complete local file, or null
     * while it is not one. Returns false when the native session cannot start or the tapped track is not a ready file.
     */
    fun start(
        items: List<PlaybackItem>,
        startIndex: Int,
        single: Boolean = false,
        fileFor: (PlaybackItem) -> PlaybackItem? = { item -> item.takeIf { isEligible(it) } }
    ): Boolean {
        // The old output must be gone before the new one exists, so the two never overlap.
        stop()
        awaitPendingRelease()
        if (!stage(items, startIndex, single, fileFor)) return false
        if (!commitStaged()) return false
        player?.play()
        return true
    }

    /**
     * Prepares a paused session for [items] from [startIndex] without making it active: the native side decodes and
     * analyses the first track while the caller keeps playing elsewhere. Returns false when nothing could be staged.
     */
    fun stage(
        items: List<PlaybackItem>,
        startIndex: Int,
        single: Boolean = false,
        fileFor: (PlaybackItem) -> PlaybackItem? = { item -> item.takeIf { isEligible(it) } }
    ): Boolean {
        discardStaged()
        val ready = items.map { fileFor(it) }
        val tail = (if (single) listOf(startIndex).takeIf { startIndex in ready.indices && ready[startIndex] != null }
        else bgMixReadyPrefix(ready, startIndex)) ?: return false
        val created = BgMixPlayer(cacheDir)
        if (!created.start()) return false
        val ids = created.setQueue(tail.map { File(ready[it]!!.path) })
        // A file the decoder rejects would vanish from the queue without a word, so it refuses the session as a whole.
        if (ids.size != tail.size || ids.any { it == 0L }) {
            releaser.submit { created.release() }
            return false
        }
        val resolved = items.mapIndexed { index, item -> ready[index] ?: item }
        staged = Staged(
            player = created,
            items = resolved,
            indexByNativeId = ids.zip(tail).associate { (id, index) -> id to index },
            appendedUntil = (tail.lastOrNull() ?: startIndex) + 1,
            single = single
        )
        return true
    }

    /** Makes the staged session the active one, stopping any previous session first. Paused until [resume]. */
    fun commitStaged(): Boolean {
        val next = staged ?: return false
        staged = null
        stop()
        awaitPendingRelease()
        items = next.items
        indexByNativeId = next.indexByNativeId
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
        pendingRelease = releaser.submit { old.player.release() }
    }

    /** Seeks the staged session before it plays; false when its first track is not loaded yet. */
    fun seekStaged(seconds: Double): Boolean = staged?.player?.seek(seconds) ?: false

    /** Output volume of the staged session: it starts silent and rises during the handover crossfade. */
    fun setStagedVolume(volume: Float) {
        staged?.player?.setOutputVolume(volume)
    }

    /** Starts output of the staged session while the caller still plays; it becomes active only with [commitStaged]. */
    fun playStaged() {
        staged?.player?.play()
    }

    /** Where the staged session is heard, measured against the audio clock; null while that cannot be told yet. */
    fun stagedAudible(): BgMixAudible? = staged?.player?.audible()

    /**
     * Adds the queue items that have become complete files since the session started, in queue order: an item that
     * is still downloading holds back the ones after it, so the native order never differs from the queue. Returns
     * how many items joined.
     */
    fun appendReady(fileFor: (PlaybackItem) -> PlaybackItem?): Int {
        val session = player ?: return 0
        if (single) return 0
        var added = 0
        while (appendedUntil < items.size) {
            val index = appendedUntil
            val file = fileFor(items[index]) ?: break
            val id = session.addTrack(File(file.path))
            if (id == 0L) break
            indexByNativeId = indexByNativeId + (id to index)
            items = items.toMutableList().also { it[index] = file }
            appendedUntil++
            added++
        }
        return added
    }

    /** The audio session of the native output; null when no session runs. */
    val audioSessionId: Int? get() = player?.audioSessionId?.takeIf { it > 0 }

    fun pause() {
        player?.pause()
    }

    /** Seeks the current BG Mix session; false when it cannot seek right now. */
    fun seek(seconds: Double): Boolean = player?.seek(seconds) ?: false

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

    /** Releases the native session. Blocks briefly while the render thread stops, so old output never overlaps new output. */
    fun stop() {
        val old = player ?: return
        player = null
        statusSource = null
        indexByNativeId = emptyMap()
        items = emptyList()
        appendedUntil = 0
        single = false
        // Releasing waits for the render thread, so it runs off the main thread; a new session waits for it below.
        pendingRelease = releaser.submit { old.release() }
    }

    private val releaser = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bgmix-release").apply { isDaemon = true }
    }

    @Volatile
    private var pendingRelease: java.util.concurrent.Future<*>? = null

    /** The old output must be gone before a new one starts, so the two never overlap. */
    private fun awaitPendingRelease() {
        val pending = pendingRelease ?: return
        runCatching { pending.get(2, java.util.concurrent.TimeUnit.SECONDS) }
        pendingRelease = null
    }

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
                File(item.path).isFile
    }
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

/**
 * Whether BG Mix may take a queue from [source]. A source with more pages still to load stays on ExoPlayer: the native queue
 * holds only the items that are known now, so it would end at the edge of the first page.
 */
internal fun bgMixAdmitsSource(source: PlaybackQueueSource?): Boolean = source?.loadMore == null

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
