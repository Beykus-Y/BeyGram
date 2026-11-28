package com.beykus.beygram.data.media

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

internal class GifAnimationHandle internal constructor(
    internal val handleId: Long,
    val granted: State<Boolean>
)

/**
 * Coordinates how many GIF / animated-WebP tiles in the media picker may animate simultaneously.
 *
 * Previews with higher priority preempt grid tiles, and releasing a handle immediately promotes
 * the next highest-priority waiting tile.
 */
internal class GifAnimationBudget(
    maxConcurrent: Int
) {
    private class SlotEntry(
        val handleId: Long,
        val priority: Int,
        val grantedState: MutableState<Boolean> = mutableStateOf(false)
    )

    private val lock = Any()
    private val slots = LinkedHashMap<Long, SlotEntry>()
    private var nextHandleId = 1L

    /** The cap the current tier allows. It changes when the device enters or leaves battery saver. */
    @Volatile
    private var maxConcurrent: Int = maxConcurrent.coerceAtLeast(1)

    /**
     * Applies a cap from a new performance tier. Tiles that no longer fit lose their grant immediately and the next
     * waiting tiles take the freed ones, so a running picker follows battery saver without a restart.
     */
    internal fun updateMaxConcurrent(value: Int) {
        synchronized(lock) {
            val next = value.coerceAtLeast(1)
            if (next == maxConcurrent) return
            maxConcurrent = next
            recomputeLocked()
        }
    }

    fun acquire(priority: Int): GifAnimationHandle {
        val entry = synchronized(lock) {
            val id = nextHandleId++
            SlotEntry(handleId = id, priority = priority).also {
                slots[id] = it
                recomputeLocked()
            }
        }
        return GifAnimationHandle(entry.handleId, entry.grantedState)
    }

    fun release(handle: GifAnimationHandle) {
        synchronized(lock) {
            if (slots.remove(handle.handleId) != null) {
                recomputeLocked()
            }
        }
    }

    internal fun activeGrantedCount(): Int = synchronized(lock) {
        slots.values.count { it.grantedState.value }
    }

    internal fun totalSlotCount(): Int = synchronized(lock) { slots.size }

    private fun recomputeLocked() {
        val winners = slots.values
            .sortedWith(compareByDescending<SlotEntry> { it.priority }.thenBy { it.handleId })
            .take(maxConcurrent.coerceAtLeast(1))
            .mapTo(HashSet()) { it.handleId }
        slots.values.forEach { slot ->
            val shouldGrant = slot.handleId in winners
            if (slot.grantedState.value != shouldGrant) {
                slot.grantedState.value = shouldGrant
            }
        }
    }

    companion object {
        @Volatile
        private var instance: GifAnimationBudget? = null

        fun default(context: Context): GifAnimationBudget {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: run {
                    val application = context.applicationContext ?: context
                    // The cap follows battery saver: the tier is re-read on every change, not only at process start.
                    val tiers = MediaPerformanceTierSource.of(application) { tier ->
                        instance?.updateMaxConcurrent(maxConcurrentGifAnimations(tier))
                    }
                    GifAnimationBudget(maxConcurrent = maxConcurrentGifAnimations(tiers.tier))
                        .also { instance = it }
                }
            }
        }
    }
}
