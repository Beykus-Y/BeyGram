package com.beykus.beygram.data.media

/**
 * Collects sensor timestamps and exposure from the capture results of the real recording session
 * (issue #234, §6). Only numbers are kept: no frames, no pixel access. The same camera session
 * serves the preview and the recorder, so this shows what the sensor delivered while recording,
 * which the separate opaque-stream check cannot.
 */
internal class VideoNoteSensorTap(private val capacity: Int = 4000) {
    private val timestamps = ArrayList<Long>()
    private val exposures = ArrayList<Long>()
    private var active = false

    /** Starts a fresh window; results before this call (preview only) are ignored. */
    @Synchronized fun begin() {
        timestamps.clear()
        exposures.clear()
        active = true
    }

    @Synchronized fun record(sensorTimestampNs: Long?, exposureNs: Long?) {
        if (!active || sensorTimestampNs == null || timestamps.size >= capacity) return
        timestamps += sensorTimestampNs
        exposureNs?.let { exposures += it }
    }

    /** Stops collecting and returns the statistics for the recorded window, or null if nothing arrived. */
    @Synchronized fun finish(targetFps: Int): ProbeFrameStats? {
        active = false
        if (timestamps.isEmpty()) return null
        val first = timestamps.first()
        val last = timestamps.last()
        val interval = 1_000_000_000L / targetFps.coerceAtLeast(1)
        // The window is what the recording spanned plus one frame: coverage compares against that.
        return measureProbeFrames(timestamps.toList(), exposures.toList(), targetFps, (last - first) + interval)
    }
}
