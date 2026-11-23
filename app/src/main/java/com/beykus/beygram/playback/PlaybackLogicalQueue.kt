package com.beykus.beygram.playback

/**
 * The logical music queue shared by the playback adapters (Media3 timeline, BG Mix native queue). It is the single
 * source of order, identity and the playing index; adapters only mirror it. This step adds no behavior by itself:
 * the engine keeps its current paths until adapters are moved onto it.
 */
internal data class PlaybackLogicalQueue(
    val items: List<PlaybackItem> = emptyList(),
    val index: Int = -1,
    val generation: Long = 0L
) {
    val playing: PlaybackItem? get() = items.getOrNull(index)

    /**
     * Appends [page] after the last item without moving the playing index. Items already in the queue, items of another
     * account and protected media are dropped. Returns the same queue when [generation] is not the current one, so a
     * page read for an older queue never lands in a newer one.
     */
    fun appendPage(page: List<PlaybackItem>, pageGeneration: Long): PlaybackLogicalQueue {
        if (pageGeneration != generation || items.isEmpty() || index !in items.indices) return this
        val account = playing?.accountId ?: return this
        val added = appendMusicPage(items, page, account)
        if (added.isEmpty()) return this
        return copy(items = items + added)
    }

    /** Starts a new logical queue; every start bumps the generation so older pages are refused. */
    fun restart(newItems: List<PlaybackItem>, newIndex: Int): PlaybackLogicalQueue =
        PlaybackLogicalQueue(newItems, newIndex, generation + 1)
}
