package com.beykus.beygram.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * Explicit shuffle order used by `playNext` when the user wants the inserted track to be next in the *actual*
 * shuffled playback order, not merely next in the visible original queue.
 */
@OptIn(UnstableApi::class)
internal class PlaybackShuffleOrder(private val order: List<Int>) : ShuffleOrder {
    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val position = order.indexOf(index)
        return if (position >= 0 && position < order.lastIndex) order[position + 1] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val position = order.indexOf(index)
        return if (position > 0) order[position - 1] else C.INDEX_UNSET
    }

    override fun getFirstIndex(): Int = order.firstOrNull() ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = order.lastOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }
        return PlaybackShuffleOrder(shifted + (insertionIndex until insertionIndex + insertionCount).toList())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = (indexToExclusive - indexFrom).coerceAtLeast(0)
        val remaining = order.mapNotNull { index ->
            when {
                index in indexFrom until indexToExclusive -> null
                index >= indexToExclusive -> index - count
                else -> index
            }
        }
        return PlaybackShuffleOrder(remaining)
    }

    override fun cloneAndClear(): ShuffleOrder = PlaybackShuffleOrder(emptyList())
}

/** Returns original queue indices in actual playback order, falling back to the unshuffled order if traversal fails. */
@OptIn(UnstableApi::class)
internal fun ShuffleOrder.toIndexOrder(): List<Int> {
    val result = ArrayList<Int>(length)
    val seen = HashSet<Int>(length)
    var index = firstIndex
    while (index != C.INDEX_UNSET && seen.add(index)) {
        result += index
        index = getNextIndex(index)
    }
    return if (result.size == length) result else (0 until length).toList()
}
