package com.beykus.beygram.data.media

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
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
    private val maxConcurrent: Int
) {
    private class SlotEntry(
        val handleId: Long,
        val priority: Int,
        val grantedState: MutableState<Boolean> = mutableStateOf(false)
    )

    private val lock = Any()
    private val slots = LinkedHashMap<Long, SlotEntry>()
    private var nextHandleId = 1L

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
                    val appContext = context.applicationContext
                    val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val tier = detectMediaPerformanceTier(
                        isLowRam = am?.isLowRamDevice == true,
                        memoryClassMb = am?.memoryClass ?: 0,
                        cpuCores = Runtime.getRuntime().availableProcessors(),
                        powerSaveMode = pm?.isPowerSaveMode == true
                    )
                    val cap = when (tier) {
                        MediaPerformanceTier.Low -> 2
                        MediaPerformanceTier.Mid -> 3
                        MediaPerformanceTier.High -> 4
                    }
                    GifAnimationBudget(maxConcurrent = cap).also { instance = it }
                }
            }
        }
    }
}
