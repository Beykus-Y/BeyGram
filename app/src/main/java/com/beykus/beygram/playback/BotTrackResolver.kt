package com.beykus.beygram.playback

import com.beykus.beygram.domain.model.MusicBotTrack
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns the results of a music bot search into a real queue. A queue row for a bot result has no file yet; it is fetched
 * from the bot when the row becomes current or is next in line, the same way a chat track is downloaded lazily. A row
 * that cannot be fetched fails like an unreadable file, so the error policy skips it instead of stopping the queue.
 */
internal object BotTrackResolver {
    const val KEY_PREFIX = "bot:"

    private class Entry(@Volatile var track: MusicBotTrack?, @Volatile var failed: Boolean, @Volatile var handedOver: Boolean)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var fetch: (suspend (queryId: Long, resultId: String) -> Result<MusicBotTrack?>)? = null

    /** The screen that owns the search supplies the fetch; it stays valid for the queue built from that search. */
    fun configure(fetcher: suspend (queryId: Long, resultId: String) -> Result<MusicBotTrack?>) {
        fetch = fetcher
    }

    fun isBotKey(key: String): Boolean = key.startsWith(KEY_PREFIX)

    fun keyOf(queryId: Long, resultId: String): String = "$KEY_PREFIX$queryId:$resultId"

    /** The fetched copy while its file still exists; a copy the cache has already removed is forgotten. */
    fun resolved(key: String): MusicBotTrack? {
        val entry = entries[key] ?: return null
        val track = entry.track ?: return null
        if (!File(track.path).exists()) {
            entries.remove(key, entry)
            return null
        }
        return track
    }

    fun failed(key: String): Boolean = entries[key]?.failed == true

    /** Starts fetching once; asking again while it runs or after it finished does nothing. */
    fun request(key: String) {
        val fetcher = fetch ?: return
        if (!isBotKey(key)) return
        val (queryId, resultId) = parse(key) ?: return
        val existing = entries[key]
        if (existing != null && (existing.failed || existing.track != null)) {
            if (existing.failed || resolved(key) != null) return
        }
        val entry = Entry(track = null, failed = false, handedOver = false)
        if (entries.putIfAbsent(key, entry) != null) return
        scope.launch {
            val track = runCatching { fetcher(queryId, resultId).getOrNull() }.getOrNull()
            if (track == null) entry.failed = true else entry.track = track
        }
    }

    /** The queue row now names the file, so the cache keeps it for as long as the row is there. */
    fun published(key: String) {
        val entry = entries[key] ?: return
        val track = entry.track ?: return
        entry.handedOver = true
        MusicBotCache.handOver(track.path)
    }

    /** A new search replaces the queue: copies that were fetched ahead but never reached the player are dropped. */
    fun reset() {
        entries.values.forEach { entry ->
            val track = entry.track
            if (track != null && !entry.handedOver) MusicBotCache.discard(track.path)
        }
        entries.clear()
    }

    private fun parse(key: String): Pair<Long, String>? {
        val rest = key.removePrefix(KEY_PREFIX)
        val split = rest.indexOf(':')
        if (split <= 0) return null
        val queryId = rest.substring(0, split).toLongOrNull() ?: return null
        val resultId = rest.substring(split + 1)
        return if (resultId.isEmpty()) null else queryId to resultId
    }
}
