package com.beykus.beygram.playback

import java.io.File
import java.util.UUID

/**
 * Copies of music bot tracks, which the shared player reads from disk. A copy is removed only once nothing can still play
 * it: not the playing track, not its queue, not a music track waiting behind a voice note, and not a copy that was made but
 * has not reached the player yet.
 */
internal object MusicBotCache {
    const val DIR_NAME = "music-bot"

    private val lock = Any()
    private val pending = HashSet<String>()

    /** Copies [source] into [dir] under a unique name. The copy stays until it is handed over or discarded. */
    fun store(source: File, dir: File): File = synchronized(lock) {
        dir.mkdirs()
        val copy = File(dir, "${UUID.randomUUID()}-${source.name}")
        try {
            source.copyTo(copy)
        } catch (error: Exception) {
            copy.delete()
            throw error
        }
        pending += copy.absolutePath
        copy
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
