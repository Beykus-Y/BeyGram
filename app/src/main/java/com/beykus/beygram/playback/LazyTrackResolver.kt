package com.beykus.beygram.playback

import android.os.SystemClock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A provider of queue rows that have no file yet. The key prefix names the provider, so the player asks the registry
 * and never names a provider in its own code.
 */
internal interface LazyTrackSource {
    /** True for the keys this provider resolves. */
    fun owns(key: String): Boolean

    /** Tracks of a restricted provider may not be forwarded, shared, exported or put on a profile. */
    val restricted: Boolean

    /** Local path of the fetched copy while the file still exists, otherwise null. */
    fun resolvedPath(key: String): String?

    /** True after the provider answered that the track cannot be delivered; a later ask does not change that. */
    fun failed(key: String): Boolean

    /** Starts fetching once; asking again while it runs or after it finished does nothing. */
    fun request(key: String)

    /** Speculative fetches may be cancelled when the lookahead window moves. */
    fun prefetch(key: String) = request(key)

    /** The queue row now names the copy, so the provider keeps it for as long as the row is there. */
    fun published(key: String)

    /**
     * A copy that is still downloading and can already be played from its readable start. Null when there is no such
     * copy yet; the player waits for the file as it does for any other row.
     */
    fun growingCopy(key: String): LazyGrowingCopy? = null
}

/** The file a provider is still writing, and the reader that follows it as the bytes arrive. */
internal data class LazyGrowingCopy(val path: String, val growing: GrowingPlaybackSource)

/** Routes a queue key to the provider that owns its prefix. A key with no owner plays as an ordinary queue row. */
/** Which lazy rows are being fetched right now, and which fetched copies are already held. */
internal data class LazyTrackProgress(val fetching: Set<String> = emptySet(), val ready: Set<String> = emptySet())

internal object LazyTrackRegistry {
    private val sources: List<LazyTrackSource> = listOf(BotTrackResolver, YandexTrackResolver)
    private val _progress = MutableStateFlow(LazyTrackProgress())

    /** Changes whenever a fetch starts or ends, or a queue is reset, so the queue shows downloads as they happen. */
    val progress: StateFlow<LazyTrackProgress> = _progress.asStateFlow()

    fun sourceFor(key: String): LazyTrackSource? = sources.firstOrNull { it.owns(key) }

    fun isLazyKey(key: String): Boolean = sourceFor(key) != null

    /** Recomputes [progress] from every resolver; a resolver calls it after each change of its entries. */
    internal fun publishProgress() {
        val resolvers = sources.filterIsInstance<LazyTrackResolver<*>>()
        _progress.value = LazyTrackProgress(
            fetching = resolvers.flatMapTo(HashSet()) { it.fetchingKeys() },
            ready = resolvers.flatMapTo(HashSet()) { it.readyKeys() }
        )
    }
}

/**
 * Turns queue rows without a file into a real file. A row is fetched when it becomes current or next in line, the same
 * way a chat track is downloaded lazily. A row that cannot be fetched fails like an unreadable file, so the error policy
 * skips it instead of stopping the queue.
 *
 * Subclasses say where a copy lives ([pathOf]), how it is removed or kept ([discardCopy], [handOverCopy]) and how the key
 * becomes a fetch ([setFetch]).
 */
internal abstract class LazyTrackResolver<T : Any>(private val prefix: String, maxConcurrentFetches: Int = 4) : LazyTrackSource {
    private class Entry<V : Any>(@Volatile var track: V?, @Volatile var failed: Boolean, @Volatile var handedOver: Boolean, var speculative: Boolean) {
        lateinit var job: Job
    }

    private val permits = Semaphore(maxConcurrentFetches)

    private val entries = ConcurrentHashMap<String, Entry<T>>()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var fetch: (suspend (String) -> Result<T?>)? = null

    protected abstract fun pathOf(track: T): String

    /** Removes a copy that nothing will play. */
    protected abstract fun discardCopy(path: String)

    /** The player owns [path] now. */
    protected abstract fun handOverCopy(path: String)

    /** The fetch for one key. A null value is a verdict: the track cannot be delivered. A failure is retried later. */
    protected fun setFetch(fetcher: suspend (String) -> Result<T?>) {
        fetch = fetcher
    }

    override fun owns(key: String): Boolean = key.startsWith(prefix)

    override fun resolvedPath(key: String): String? = resolvedTrack(key)?.let { track -> pathOf(track) }

    /** The fetched copy while its file still exists; a copy the cache has already removed is forgotten. */
    fun resolvedTrack(key: String): T? {
        val entry = entries[key] ?: return null
        val track = entry.track ?: return null
        if (!File(pathOf(track)).exists()) {
            entries.remove(key, entry)
            return null
        }
        return track
    }

    override fun failed(key: String): Boolean = entries[key]?.failed == true

    /** Keys whose fetch is running now; a finished or failed fetch is not in this set. */
    fun fetchingKeys(): Set<String> = entries.filterValues { it.track == null && !it.failed }.keys.toSet()

    /** Keys whose fetched copy is held, whether or not the player has taken it yet. */
    fun readyKeys(): Set<String> = entries.filterValues { it.track != null }.keys.toSet()

    override fun request(key: String) = enqueue(key, speculative = false)

    override fun prefetch(key: String) = enqueue(key, speculative = true)

    private fun enqueue(key: String, speculative: Boolean) {
        val fetcher = fetch ?: return
        if (!owns(key)) return
        while (true) {
            val existing = entries[key]
            if (existing != null) {
                synchronized(existing) {
                    if (entries[key] === existing) {
                        if (!speculative) existing.speculative = false
                        if (existing.failed || existing.track == null || resolvedTrack(key) != null) return
                    }
                }
                // A cache eviction removed the previous entry: look up again instead of replacing a missing key.
                continue
            }
            val until = cooldownUntil[key]
            if (until != null && SystemClock.elapsedRealtime() < until) return
            val entry = Entry<T>(track = null, failed = false, handedOver = false, speculative = speculative)
            entry.job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = runCatching { permits.withPermit { fetcher(key) } }.getOrElse { Result.failure(it) }
                    val track = result.getOrNull()
                    val stale = synchronized(entry) {
                        if (entries[key] !== entry) true
                        else {
                            when {
                                track != null -> entry.track = track
                                result.exceptionOrNull() is CancellationException -> entries.remove(key, entry)
                                result.isFailure -> {
                                    cooldownUntil[key] = SystemClock.elapsedRealtime() + RETRY_COOLDOWN_MS
                                    entries.remove(key, entry)
                                }
                                else -> entry.failed = true
                            }
                            false
                        }
                    }
                    if (stale) track?.let { discardCopy(pathOf(it)) }
                } finally {
                    LazyTrackRegistry.publishProgress()
                }
            }
            if (entries.putIfAbsent(key, entry) == null) {
                LazyTrackRegistry.publishProgress()
                entry.job.start()
                return
            }
            entry.job.cancel()
        }
    }

    /** Cancel only unfinished speculative requests unless the entire playback queue has been replaced. */
    fun cancelOutside(keep: Set<String>, includePlayback: Boolean = false) {
        var changed = false
        entries.forEach { (key, entry) ->
            if (key !in keep) {
                synchronized(entry) {
                    if (entry.track == null && !entry.failed &&
                        (includePlayback || (entry.speculative && !entry.handedOver)) &&
                        entries.remove(key, entry)
                    ) {
                        entry.job.cancel()
                        changed = true
                    }
                }
            }
        }
        if (changed) LazyTrackRegistry.publishProgress()
    }

    override fun published(key: String) {
        val entry = entries[key] ?: return
        val track = synchronized(entry) {
            if (entries[key] !== entry) return
            entry.handedOver = true
            entry.speculative = false
            entry.track
        } ?: return
        handOverCopy(pathOf(track))
    }

    /** Paths of copies that a live entry still refers to, so a prune never removes a copy that is about to play. */
    protected fun livePaths(): Set<String> = entries.values.mapNotNull { entry -> entry.track?.let { track -> pathOf(track) } }.toSet()

    /** A new queue replaces the old one: copies that were fetched ahead but never reached the player are dropped. */
    fun reset() {
        entries.forEach { (key, entry) ->
            val discard = synchronized(entry) {
                if (!entries.remove(key, entry)) return@forEach
                entry.job.cancel()
                entry.track?.takeUnless { entry.handedOver }
            }
            discard?.let { discardCopy(pathOf(it)) }
        }
        cooldownUntil.clear()
        LazyTrackRegistry.publishProgress()
    }

    private companion object {
        /** After a failed attempt (usually the network), the same row is not fetched again for this long. */
        const val RETRY_COOLDOWN_MS = 5_000L
    }
}
