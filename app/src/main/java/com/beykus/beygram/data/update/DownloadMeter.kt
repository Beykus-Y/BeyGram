package com.beykus.beygram.data.update

/**
 * Progress of one download in exact bytes. [bytesPerSecond] and [etaSeconds] stay null until they are stable;
 * [percent] describes the transfer only. 100% means the bytes arrived, not that the APK is verified.
 */
data class DownloadProgress(
    val bytes: Long,
    val total: Long,
    val bytesPerSecond: Long?,
    val etaSeconds: Long?,
) {
    val percent: Int
        get() = if (total <= 0L) 0 else (bytes * 100L / total).toInt().coerceIn(0, 100)
}

/**
 * Smoothed speed and ETA from exact byte counts and monotonic nanoseconds. The caller passes the clock in,
 * so the class is pure and testable on the JVM. ETA is shown only after [MIN_ELAPSED_NANOS] and [MIN_SAMPLES].
 */
class DownloadMeter(private val total: Long, startBytes: Long, private val startNanos: Long) {
    private var lastBytes = startBytes
    private var lastNanos = startNanos
    private var smoothed: Double? = null
    private var samples = 0

    fun sample(bytes: Long, nowNanos: Long): DownloadProgress {
        val deltaBytes = bytes - lastBytes
        val deltaNanos = nowNanos - lastNanos
        if (deltaBytes > 0L && deltaNanos > 0L) {
            val instant = deltaBytes.toDouble() * NANOS_PER_SECOND / deltaNanos.toDouble()
            smoothed = smoothed?.let { SMOOTHING * instant + (1 - SMOOTHING) * it } ?: instant
            samples++
            lastBytes = bytes
            lastNanos = nowNanos
        }
        val speed = smoothed
        val remaining = total - bytes
        val stable = samples >= MIN_SAMPLES && nowNanos - startNanos >= MIN_ELAPSED_NANOS
        val eta = if (stable && speed != null && speed > 0.0 && remaining > 0L) {
            (remaining / speed).toLong()
        } else {
            null
        }
        return DownloadProgress(bytes, total, speed?.toLong(), eta)
    }

    companion object {
        const val SMOOTHING = 0.3
        const val MIN_SAMPLES = 5
        const val MIN_ELAPSED_NANOS = 3_000_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/** Lets a progress event through at most every [intervalNanos], and always lets the completing one through. */
class ProgressThrottle(private val intervalNanos: Long = 250_000_000L) {
    private var lastEmitNanos: Long? = null

    fun shouldEmit(nowNanos: Long, complete: Boolean): Boolean {
        val last = lastEmitNanos
        if (!complete && last != null && nowNanos - last < intervalNanos) return false
        lastEmitNanos = nowNanos
        return true
    }
}
