package com.beykus.beygram.media.bgmotion

import kotlin.math.abs

/**
 * Turns the player's 250 ms position ticks into a smooth, monotonic media clock
 * for the mascot. The audio player remains the master; this class only
 * extrapolates between ticks and corrects small drift, snapping on seeks or item
 * changes.
 *
 * Pure Kotlin so the timing rules can be tested without Android or native code.
 */
class MascotClock {
    companion object {
        /** A deviation larger than this is a seek, not normal tick jitter. */
        const val SEEK_THRESHOLD_MS = 350L

        /** Share of the residual error corrected on every player tick. */
        private const val CORRECTION = 0.2
    }

    private var anchorPositionMs = 0L
    private var anchorNanos = 0L
    private var speed = 1f
    private var playing = false
    private var durationMs = 0L
    private var itemKey: String? = null
    private var initialized = false

    val currentItemKey: String? get() = itemKey

    fun onPlaybackState(
        itemKey: String?,
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
        speed: Float,
        nowNanos: Long
    ) {
        val target = positionMs.coerceAtLeast(0L)
        val estimate = estimateMs(nowNanos)
        val itemChanged = initialized && this.itemKey != null && itemKey != null && this.itemKey != itemKey
        val seek = !initialized || itemChanged || abs(target - estimate) > SEEK_THRESHOLD_MS

        this.itemKey = itemKey
        this.durationMs = durationMs.coerceAtLeast(0L)
        this.speed = speed.coerceAtLeast(0.1f)
        this.playing = playing

        if (seek || !playing) {
            anchorPositionMs = target
            anchorNanos = nowNanos
        } else {
            // Keep the frame-to-frame motion smooth; pull the anchor towards the
            // player by a small fraction instead of jumping on every tick.
            val error = target - estimate
            anchorPositionMs = (estimate + (error * CORRECTION).toLong()).coerceAtLeast(0L)
            anchorNanos = nowNanos
        }
        initialized = true
    }

    fun timeSeconds(nowNanos: Long): Double {
        if (!initialized) return 0.0
        var ms = estimateMs(nowNanos)
        if (durationMs > 0L) ms = ms.coerceIn(0L, durationMs)
        return ms / 1000.0
    }

    fun reset() {
        anchorPositionMs = 0L
        anchorNanos = 0L
        speed = 1f
        playing = false
        durationMs = 0L
        itemKey = null
        initialized = false
    }

    private fun estimateMs(nowNanos: Long): Long {
        if (!initialized) return anchorPositionMs
        if (!playing) return anchorPositionMs
        val elapsedNanos = (nowNanos - anchorNanos).coerceAtLeast(0L)
        val advanced = (elapsedNanos / 1_000_000.0 * speed).toLong()
        return (anchorPositionMs + advanced).coerceAtLeast(0L)
    }
}
