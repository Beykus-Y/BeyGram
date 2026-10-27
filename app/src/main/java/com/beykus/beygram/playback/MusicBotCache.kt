package com.beykus.beygram.playback

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies of music bot tracks, which the shared player reads from disk. A copy is removed only once nothing can still play
 * it: not the playing track, not its queue, not a music track waiting behind a voice note, and not a copy that was made but
 * has not reached the player yet.
 */
internal object MusicBotCache {
    const val DIR_NAME = "music-bot"

    private val lock = Any()
    private val pending = HashSet<String>()

    /** Copies that were made but not yet handed over or discarded. Only tests read this. */
    internal val pendingCount: Int get() = synchronized(lock) { pending.size }

    /**
     * Copies [source] into [dir] under a unique name and returns the copy. The name is reserved before the copy starts,
     * so a prune that runs meanwhile keeps the file. The lock is not held during the copy, which can be large.
     *
     * A cancelled caller drops its copy: cancellation is seen when the IO copy hands back, so the reservation and the
     * partial or complete file are removed here rather than by a caller that will never receive the path.
     */
    suspend fun copyReserved(source: File, dir: File): File {
        val copy = synchronized(lock) {
            dir.mkdirs()
            File(dir, "${UUID.randomUUID()}-${source.name}").also { pending += it.absolutePath }
        }
        try {
            withContext(Dispatchers.IO) { source.copyTo(copy) }
            return copy
        } catch (error: Throwable) {
            discard(copy.absolutePath)
            throw error
        }
    }

    /** The player owns [path] now, so it is kept for as long as the player uses it. */
    fun handOver(path: String) = synchronized(lock) {
        pending -= path
    }

    /** Removes a copy that nothing will play, such as a result the user has already replaced with a newer tap. */
    fun discard(path: String) = synchronized(lock) {
        pending -= path
        File(path).delete()
    }

    /** Removes every copy in [dir] that is neither in [inUse] nor waiting to be handed over. */
    fun prune(dir: File, inUse: Set<String>) = synchronized(lock) {
        val keep = inUse + pending
        dir.listFiles().orEmpty().forEach { file ->
            if (file.absolutePath !in keep) file.delete()
        }
    }
}
