package com.beykus.beygram.playback

import android.os.SystemClock
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

    /** A prefetch for the row that plays right after the current one: it starts before ordinary prefetch. */
    fun prefetchSoon(key: String) = prefetch(key)

    /**
     * Ranks the rows the current playback order needs ([keep], with [urgent] as the row being opened now) and cancels
     * unfinished fetches that no reader owns. Completed copies are left to the existing eviction policy.
     */
    fun prioritize(keep: Set<String>, urgent: String?) {}

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

    /**
     * Keeps the fetchers focused on [keep], with [currentKey] as the row that is opening now. A provider is only asked
     * about rows it owns, so another provider's active readers are never cancelled by a queue edit elsewhere.
     */
    fun prioritize(currentKey: String?, keep: Set<String>) {
        val perSource = LinkedHashMap<LazyTrackSource, MutableSet<String>>()
        for (key in keep) sourceFor(key)?.let { perSource.getOrPut(it) { HashSet() }.add(key) }
        if (perSource.isEmpty()) return
        val urgent = currentKey?.takeIf { key -> keep.contains(key) }
        perSource.forEach { (source, keys) -> source.prioritize(keys, urgent) }
    }

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
internal abstract class LazyTrackResolver<T : Any>(private val prefix: String, private val maxConcurrentFetches: Int = 4) : LazyTrackSource {
    private class Entry<V : Any>(
        @Volatile var track: V?,
        @Volatile var failed: Boolean,
        @Volatile var handedOver: Boolean,
        var speculative: Boolean,
        /** 2 = the row playback opens now, 1 = the row that plays next, 0 = ordinary speculation. */
        @Volatile var priority: Int,
        val sequence: Long,
    ) {
        lateinit var job: Job
        /** True while this fetch waits for a download slot, so the admission order can rank it. */
        @Volatile var waiting: Boolean = false
    }

    /** Download slots. A waiting fetch is admitted by priority and then by arrival order, not by arrival alone. */
    private val admissionLock = Any()
    private var activeFetches = 0
    private val admissionVersion = MutableStateFlow(0L)

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

    override fun request(key: String) = enqueue(key, speculative = false, priority = PRIORITY_PLAYING)

    override fun prefetch(key: String) = enqueue(key, speculative = true, priority = PRIORITY_SPECULATIVE)

    override fun prefetchSoon(key: String) = enqueue(key, speculative = true, priority = PRIORITY_NEXT)

    private fun enqueue(key: String, speculative: Boolean, priority: Int) {
        val fetcher = fetch ?: return
        if (!owns(key)) return
        while (true) {
            val existing = entries[key]
            if (existing != null) {
                var promoted = false
                val alive = synchronized(existing) {
                    if (entries[key] !== existing) {
                        false
                    } else {
                        if (!speculative) existing.speculative = false
                        // Raising a queued prefetch changes the real admission order, not just a flag.
                        if (priority > existing.priority) {
                            existing.priority = priority
                            promoted = true
                        }
                        true
                    }
                }
                if (!alive) continue
                if (promoted) wakeWaiters()
                // A running or completed fetch is already the one this key will use.
                if (existing.failed || existing.track == null) return
                if (resolvedTrack(key) != null) return
                // The copy disappeared from the cache; resolvedTrack removed the entry, so create it again.
                continue
            }
            val until = cooldownUntil[key]
            if (until != null && SystemClock.elapsedRealtime() < until) return
            val entry = Entry<T>(
                track = null,
                failed = false,
                handedOver = false,
                speculative = speculative,
                priority = priority,
                sequence = nextSequence.incrementAndGet(),
            )
            entry.job = scope.launch(start = CoroutineStart.LAZY) {
                var slot = false
                try {
                    awaitFetchSlot(entry)
                    slot = true
                    val result = runCatching { fetcher(key) }.getOrElse { Result.failure(it) }
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
                    if (slot) releaseFetchSlot()
                    entry.waiting = false
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

    /**
     * Waits until a slot is free and this entry is the most urgent waiting fetch. A promoted prefetch therefore starts
     * as soon as the next wake-up allows it, while the newest playback request overtakes an older one.
     */
    private suspend fun awaitFetchSlot(entry: Entry<T>) {
        entry.waiting = true
        try {
            while (true) {
                // Read the version before the check: a change after it must wake this waiter at least once.
                val version = admissionVersion.value
                val admitted = synchronized(admissionLock) {
                    val next = entries.values
                        .filter { it.waiting && it.track == null && !it.failed }
                        .minWithOrNull(compareByDescending<Entry<T>> { it.priority }.thenBy { it.sequence })
                    if (activeFetches < maxConcurrentFetches && (next == null || next === entry)) {
                        activeFetches++
                        true
                    } else false
                }
                if (admitted) {
                    // More than one slot can be free. An admission does not change activeFetches for the other
                    // waiters unless they are woken, so the remaining free slots would otherwise stay unused while
                    // a lower-priority waiter sleeps until the first fetch finishes.
                    wakeWaiters()
                    return
                }
                admissionVersion.first { it != version }
            }
        } finally {
            entry.waiting = false
        }
    }

    private fun releaseFetchSlot() {
        synchronized(admissionLock) {
            if (activeFetches > 0) activeFetches--
        }
        wakeWaiters()
    }

    private fun wakeWaiters() {
        admissionVersion.update { it + 1 }
    }

    /**
     * Keeps the download slots on the rows playback actually needs. Unfinished fetches outside [keep] have no reader
     * (a growing reader marks its copy handed over), so their slot is freed; the row is fetched again if the player
     * returns to it. Completed copies stay for the existing cache/eviction policy.
     */
    override fun prioritize(keep: Set<String>, urgent: String?) {
        var changed = false
        val cancelled = mutableListOf<Pair<String, Entry<T>>>()
        entries.forEach { (key, entry) ->
            val wanted = when {
                key == urgent -> PRIORITY_PLAYING
                key in keep -> PRIORITY_NEXT
                else -> PRIORITY_SPECULATIVE
            }
            val drop = synchronized(entry) {
                if (entries[key] !== entry) {
                    false
                } else {
                    if (entry.priority != wanted) {
                        entry.priority = wanted
                        changed = true
                    }
                    key !in keep && entry.track == null && !entry.failed && !entry.handedOver
                }
            }
            if (drop) cancelled += key to entry
        }
        cancelled.forEach { (key, entry) ->
            synchronized(entry) {
                if (entries.remove(key, entry)) {
                    entry.job.cancel()
                    changed = true
                }
            }
        }
        if (changed) {
            wakeWaiters()
            LazyTrackRegistry.publishProgress()
        }
        if (cancelled.isNotEmpty()) {
            recordAppEvent(
                kind = EventKind.PERFORMANCE,
                code = "provider_fetch_deprioritized",
                text = "Музыка: устаревшая загрузка освободила слот",
                attrs = mapOf(
                    "cancelled" to cancelled.size.toString(),
                    "slots" to maxConcurrentFetches.toString(),
                    "urgent" to (urgent != null).toString(),
                )
            )
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
        if (changed) {
            wakeWaiters()
            LazyTrackRegistry.publishProgress()
        }
    }

    /**
     * Bounds resolver memory and disk pinning during long sessions: cancels eligible unfinished fetches
     * (like [cancelOutside]) AND evicts completed or failed entries outside [keep].
     * Returns true if at least one entry was removed.
     */
    fun evictOutside(keep: Set<String>, includePlayback: Boolean = false): Boolean {
        var changed = false
        entries.forEach { (key, entry) ->
            if (key !in keep) {
                val discard = synchronized(entry) {
                    val canRemove = when {
                        entry.track != null || entry.failed -> true
                        includePlayback || (entry.speculative && !entry.handedOver) -> true
                        else -> false
                    }
                    if (!canRemove || !entries.remove(key, entry)) return@forEach
                    if (entry.track == null && !entry.failed) entry.job.cancel()
                    changed = true
                    entry.track?.takeUnless { entry.handedOver }
                }
                discard?.let { discardCopy(pathOf(it)) }
            }
        }
        if (changed) {
            wakeWaiters()
            LazyTrackRegistry.publishProgress()
        }
        return changed
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
        wakeWaiters()
        LazyTrackRegistry.publishProgress()
    }

    private companion object {
        /** After a failed attempt (usually the network), the same row is not fetched again for this long. */
        const val RETRY_COOLDOWN_MS = 5_000L

        const val PRIORITY_SPECULATIVE = 0
        const val PRIORITY_NEXT = 1
        const val PRIORITY_PLAYING = 2

        /** FIFO within one priority, so two equally urgent rows keep their order. */
        val nextSequence = AtomicLong()
    }
}
