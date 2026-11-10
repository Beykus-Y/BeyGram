package com.beykus.beygram.playback

import com.beykus.beygram.media.bgmix.BgMixPlayer
import com.beykus.beygram.media.bgmix.BgMixStatus
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Plays a music queue through BG Mix. The engine owns the Media state; this class owns the native session.
 *
 * Only complete local WAV, MP3 or FLAC files qualify (see [isEligible]). Streaming, secret and video media
 * stay on the ExoPlayer path.
 */
internal class BgMixQueueDriver(private val cacheDir: File) {
    private var player: BgMixPlayer? = null
    private var statusSource: StateFlow<BgMixStatus?>? = null
    /** Native queue id -> index in the queue passed to [start]. */
    private var indexByNativeId: Map<Long, Int> = emptyMap()
    private var items: List<PlaybackItem> = emptyList()

    val isActive: Boolean get() = player != null

    /** Native status stream for the current session; null when stopped. */
    val status: StateFlow<BgMixStatus?>? get() = statusSource

    /** Set when the native output fails during the current session; null while it works or when stopped. */
    val failure: StateFlow<String?>? get() = player?.failure

    /** Queue snapshot the current session was started with. */
    val queueItems: List<PlaybackItem> get() = items

    fun itemAt(index: Int): PlaybackItem? = items.getOrNull(index)

    fun indexOfNative(id: Long): Int? = indexByNativeId[id]

    /**
     * Starts [items] from [startIndex]. Returns false when the native session cannot start, or when BG Mix cannot take
     * the whole rest of the queue; the caller then keeps the ExoPlayer path.
     */
    fun start(items: List<PlaybackItem>, startIndex: Int, single: Boolean = false): Boolean {
        stop()
        awaitPendingRelease()
        // Every item from the tapped one on has to be a local BG Mix file. Skipping one here would drop it from the
        // queue without a word, so the whole queue stays on ExoPlayer instead.
        val tail = bgMixQueueTail(items, startIndex, single) { isEligible(it) } ?: return false
        val created = BgMixPlayer(cacheDir)
        if (!created.start()) return false
        val ids = created.setQueue(tail.map { File(items[it].path) })
        // A file the decoder rejects is the same loss, so it refuses the queue as a whole too.
        if (ids.size != tail.size || ids.any { it == 0L }) {
            created.release()
            return false
        }
        this.items = items
        indexByNativeId = ids.zip(tail).associate { (id, index) -> id to index }
        statusSource = created.status
        player = created
        created.play()
        return true
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
 * Indexes from [startIndex] to the end of [items], or null when BG Mix cannot take all of them: the start index is out
 * of range, or one of the items from there on fails [eligible].
 */
internal fun bgMixQueueTail(
    items: List<PlaybackItem>,
    startIndex: Int,
    single: Boolean = false,
    eligible: (PlaybackItem) -> Boolean
): List<Int>? {
    if (startIndex !in items.indices) return null
    // Repeat One plays just the current track; the engine starts it again when it ends.
    val tail = if (single) listOf(startIndex) else (startIndex until items.size).toList()
    return if (tail.all { eligible(items[it]) }) tail else null
}

/**
 * Whether BG Mix may take a queue from [source]. A source with more pages still to load stays on ExoPlayer: the native queue
 * holds only the items that are known now, so it would end at the edge of the first page.
 */
internal fun bgMixAdmitsSource(source: PlaybackQueueSource?): Boolean = source?.loadMore == null
