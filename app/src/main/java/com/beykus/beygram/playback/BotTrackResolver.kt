package com.beykus.beygram.playback

import android.os.SystemClock
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

    /** After a failed attempt (usually the network), the same result is not fetched again for this long. */
    private const val RETRY_COOLDOWN_MS = 5_000L

    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    /** Starts fetching once; asking again while it runs or after it finished does nothing. */
    fun request(key: String) {
        val fetcher = fetch ?: return
        if (!isBotKey(key)) return
        val (queryId, resultId) = parse(key) ?: return
        val known = entries[key]
        if (known != null) {
            if (known.failed) return
            if (known.track == null) return // still loading
            if (resolved(key) != null) return // ready; a copy the cache removed is dropped by resolved() and fetched again
        }
        val existing = entries[key]
        val until = cooldownUntil[key]
        if (until != null && SystemClock.elapsedRealtime() < until) return
        val entry = Entry(track = null, failed = false, handedOver = false)
        if (existing == null) {
            if (entries.putIfAbsent(key, entry) != null) return
        } else if (!entries.replace(key, existing, entry)) {
            return
        }
        scope.launch {
            val result = runCatching { fetcher(queryId, resultId) }.getOrElse { Result.failure(it) }
            val track = result.getOrNull()
            // A reset or a newer fetch replaced this entry while it ran: the late copy has no owner, so it goes.
            if (entries[key] !== entry) {
                track?.let { MusicBotCache.discard(it.path) }
                return@launch
            }
            when {
                track != null -> entry.track = track
                result.isFailure -> {
                    // A failed request (timeout, no network) is not a verdict on the track: a later ask may try again.
                    cooldownUntil[key] = SystemClock.elapsedRealtime() + RETRY_COOLDOWN_MS
                    entries.remove(key, entry)
                }
                // The bot answered with something that is not audio: asking again would not change that.
                else -> entry.failed = true
            }
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
        cooldownUntil.clear()
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
