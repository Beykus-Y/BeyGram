package com.beykus.beygram.data.tdlib

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keyed [MutableStateFlow]s with a bound on how many are kept.
 *
 * Once the map grows past [maxEntries], the least recently used entries that are idle are dropped. An entry is idle
 * when nothing collects it and [isIdle] accepts its value; an entry with a running collection is never dropped.
 * A dropped entry is created again from [seed] the next time it is read, so [isIdle] must only accept values that
 * [seed] plus the owner's own data can rebuild.
 *
 * [observe] returns a handle, not the flow itself. A handle stays valid after its entry was dropped, because every
 * read and collection finds the live entry again: a screen that kept the handle across a drop still receives the
 * state of the re-created entry.
 *
 * Writers use the `*Locked` members while holding [lock], which the owner shares with its other state. An owner that
 * knows an idle entry is no longer needed can drop it at once with [removeIfIdleLocked].
 * [onEvicted] runs under [lock] and must not call back into this map. [onCreatedByRead] runs outside [lock], after a
 * read or a collection created an entry.
 */
internal class EvictableStateMap<K : Any, V>(
    private val lock: Any,
    private val maxEntries: Int,
    private val seed: (K) -> V,
    private val isIdle: (K, V) -> Boolean,
    private val onEvicted: (K) -> Unit = {},
    private val onCreatedByRead: (K) -> Unit = {}
) {
    private class Entry<V>(val state: MutableStateFlow<V>) {
        /** Collections running on this entry; an entry that is being collected is never dropped. */
        var collectors = 0
    }

    init {
        require(maxEntries >= 4) { "maxEntries must leave room around the entry being created" }
    }

    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    /** Trimming starts only past this size, so a map full of busy entries does not rescan on every insert. */
    private var trimThreshold = maxEntries

    /** Number of entries held now. */
    fun size(): Int = synchronized(lock) { entries.size }

    /** Whether an entry for [key] is held now. */
    fun contains(key: K): Boolean = synchronized(lock) { entries.containsKey(key) }

    /** The entry for [key], created from [seed] when missing. The caller holds [lock]. */
    fun stateLocked(key: K): MutableStateFlow<V> = entryLocked(key).first.state

    /** The current value, or null when no entry is held. The caller holds [lock]. */
    fun peekLocked(key: K): V? = entries[key]?.state?.value

    /** The current value; creates the entry when it is missing and reports that through [onCreatedByRead]. */
    fun read(key: K): V {
        val (entry, created) = synchronized(lock) { entryLocked(key) }
        if (created) onCreatedByRead(key)
        return entry.state.value
    }

    /** A handle for [key] that stays valid across drops; see the class comment. */
    fun observe(key: K): StateFlow<V> = Handle(key)

    private fun entryLocked(key: K): Pair<Entry<V>, Boolean> {
        entries[key]?.let { return it to false }
        val entry = Entry(MutableStateFlow(seed(key)))
        entries[key] = entry
        trimLocked(except = key)
        return entry to true
    }

    /** Drops the entry for [key] at once when nothing uses it, instead of waiting for a trim. The caller holds [lock]. */
    fun removeIfIdleLocked(key: K) {
        val entry = entries[key] ?: return
        if (!canDropLocked(key, entry)) return
        entries.remove(key)
        onEvicted(key)
    }

    private fun canDropLocked(key: K, entry: Entry<V>): Boolean = entry.collectors == 0 && isIdle(key, entry.state.value)

    private fun trimLocked(except: K) {
        if (entries.size <= trimThreshold) return
        val target = maxEntries - maxEntries / 4
        val candidates = entries.entries.iterator()
        while (entries.size > target && candidates.hasNext()) {
            val candidate = candidates.next()
            // The entry being created is never dropped: its writer still holds it.
            if (candidate.key == except || !canDropLocked(candidate.key, candidate.value)) continue
            candidates.remove()
            onEvicted(candidate.key)
        }
        trimThreshold = maxOf(maxEntries, entries.size + maxEntries / 8)
    }

    private suspend fun collectRetained(key: K, collector: FlowCollector<V>): Nothing {
        val (entry, created) = synchronized(lock) {
            entryLocked(key).also { it.first.collectors++ }
        }
        try {
            // Inside the try, so the collection is released even when the read hook fails.
            if (created) onCreatedByRead(key)
            entry.state.collect(collector)
        } finally {
            synchronized(lock) { entry.collectors-- }
        }
    }

    private inner class Handle(private val key: K) : StateFlow<V> {
        override val value: V get() = read(key)
        override val replayCache: List<V> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<V>): Nothing = collectRetained(key, collector)
    }
}
