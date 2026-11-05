package com.beykus.beygram.playback

import kotlin.math.abs

/**
 * Silence at the two ends of one complete music file, in milliseconds. A field is [UNKNOWN_MS] when that edge
 * could not be measured: the scanned window was silent from end to end, the codec failed, or the duration is unknown.
 * Unknown edges are never skipped.
 */
internal data class SilenceEdges(
    val leadingMs: Long,
    val trailingMs: Long,
    val durationMs: Long
) {
    val leadingKnown: Boolean get() = leadingMs >= 0L
    val trailingKnown: Boolean get() = trailingMs >= 0L

    companion object {
        const val UNKNOWN_MS = -1L
        val UNKNOWN = SilenceEdges(UNKNOWN_MS, UNKNOWN_MS, 0L)
    }
}

/** Length of one level window. Silence is counted in whole windows, so every edge is accurate to this value. */
internal const val SILENCE_WINDOW_MS = 10

/** A window whose loudest sample stays below this amplitude is silent. 0.00316 is -50 dBFS. */
internal const val SILENCE_PEAK_THRESHOLD = 0.0031622777f

/** How far from each end the measurement looks. Silence longer than this stays unknown and is never skipped. */
internal const val SILENCE_SCAN_MS = 10_000L

/** Shorter silences are not worth skipping, so they are reported as zero. */
internal const val SILENCE_MIN_REPORT_MS = 100L

/** Number of windows that cover [SILENCE_SCAN_MS]. */
internal const val SILENCE_SCAN_WINDOWS = (SILENCE_SCAN_MS / SILENCE_WINDOW_MS).toInt()

private const val PCM16_SCALE = 1f / 32768f

/**
 * Collects the peak amplitude (0..1) of each [SILENCE_WINDOW_MS] window of interleaved PCM. A window holds whole
 * frames across all channels, so the peak covers every channel.
 *
 * With [keepLast] false the meter keeps the first [capacity] windows and stops there. With [keepLast] true it
 * keeps the most recent [capacity] windows, which is what the end of a file needs.
 */
internal class SilenceWindowMeter(
    sampleRate: Int,
    channels: Int,
    private val capacity: Int,
    private val keepLast: Boolean
) {
    private val windowSamples = maxOf(1, sampleRate * SILENCE_WINDOW_MS / 1000) * maxOf(1, channels)
    private val peaks = FloatArray(capacity)
    private var closed = 0
    private var inWindow = 0
    private var peak = 0f

    /** True once a window has reached the threshold, so the first audible point is known. */
    var hasAudibleWindow = false
        private set

    /** True when no further windows are needed. Only the first-window mode ever becomes full. */
    val isFull: Boolean get() = !keepLast && closed >= capacity

    fun addPcm16(samples: ShortArray, length: Int) {
        var i = 0
        while (i < length && !isFull) {
            offer(abs(samples[i].toInt()) * PCM16_SCALE)
            i++
        }
    }

    fun addFloat(samples: FloatArray, length: Int) {
        var i = 0
        while (i < length && !isFull) {
            offer(abs(samples[i]))
            i++
        }
    }

    private fun offer(amplitude: Float) {
        if (amplitude > peak) peak = amplitude
        inWindow++
        if (inWindow == windowSamples) closeWindow()
    }

    private fun closeWindow() {
        if (peak >= SILENCE_PEAK_THRESHOLD) hasAudibleWindow = true
        val slot = if (closed < capacity) closed else closed % capacity
        peaks[slot] = peak
        closed++
        peak = 0f
        inWindow = 0
    }

    /** Peaks in time order. A partial last window counts when it holds any samples. */
    fun finish(): FloatArray {
        if (inWindow > 0 && (keepLast || closed < capacity)) closeWindow()
        if (!keepLast || closed <= capacity) return peaks.copyOf(minOf(closed, capacity))
        // Ring buffer: the oldest kept window sits right after the newest one.
        val start = closed % capacity
        return FloatArray(capacity) { i -> peaks[(start + i) % capacity] }
    }
}

/** Number of silent windows before the first audible one, or null when no window is audible. */
internal fun leadingSilentWindows(peaks: FloatArray): Int? {
    for (i in peaks.indices) {
        if (peaks[i] >= SILENCE_PEAK_THRESHOLD) return i
    }
    return null
}

/** Number of silent windows after the last audible one, or null when no window is audible. */
internal fun trailingSilentWindows(peaks: FloatArray): Int? {
    for (i in peaks.indices.reversed()) {
        if (peaks[i] >= SILENCE_PEAK_THRESHOLD) return peaks.size - 1 - i
    }
    return null
}

/**
 * Turns the measured peaks into skip amounts. [head] holds the first [SILENCE_SCAN_MS] of the file, and [tail] holds
 * the last ones, or null when the duration is unknown. An edge without any audible window stays unknown.
 */
internal fun silenceEdgesFromPeaks(head: FloatArray?, tail: FloatArray?, durationMs: Long): SilenceEdges {
    val leading = head?.let(::leadingSilentWindows)?.let(::reportedMs) ?: SilenceEdges.UNKNOWN_MS
    val trailing = tail?.let(::trailingSilentWindows)?.let(::reportedMs) ?: SilenceEdges.UNKNOWN_MS
    return SilenceEdges(leadingMs = leading, trailingMs = trailing, durationMs = durationMs.coerceAtLeast(0L))
}

private fun reportedMs(windows: Int): Long {
    val ms = windows.toLong() * SILENCE_WINDOW_MS
    return if (ms < SILENCE_MIN_REPORT_MS) 0L else ms
}
