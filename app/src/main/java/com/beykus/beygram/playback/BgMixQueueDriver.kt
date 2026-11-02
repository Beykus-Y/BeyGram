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

    /** Queue snapshot the current session was started with. */
    val queueItems: List<PlaybackItem> get() = items

    fun itemAt(index: Int): PlaybackItem? = items.getOrNull(index)

    fun indexOfNative(id: Long): Int? = indexByNativeId[id]

    /**
     * Starts [items] from [startIndex]. Returns false when the native session cannot start or no file is accepted;
     * the caller then keeps the ExoPlayer path.
     */
    fun start(items: List<PlaybackItem>, startIndex: Int): Boolean {
        stop()
        val accepted = items.withIndex().filter { it.index >= startIndex && isEligible(it.value) }
        if (accepted.isEmpty()) return false
        val created = BgMixPlayer(cacheDir)
        if (!created.start()) return false
        val ids = created.setQueue(accepted.map { File(it.value.path) })
        val mapping = ids.zip(accepted).filter { it.first != 0L }.associate { it.first to it.second.index }
        if (mapping.isEmpty()) {
            created.release()
            return false
        }
        this.items = items
        indexByNativeId = mapping
        statusSource = created.status
        player = created
        created.play()
        return true
    }

    fun pause() {
        player?.pause()
    }

    /** Seeks the current BG Mix session; false when it cannot seek right now. */
    fun seek(seconds: Double): Boolean = player?.seek(seconds) ?: false

    fun resume() {
        player?.play()
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
        old.release()
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
