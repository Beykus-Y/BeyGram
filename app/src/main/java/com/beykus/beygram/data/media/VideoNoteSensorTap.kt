package com.beykus.beygram.data.media

/** What the tap collected for one take. [truncated] means the recording outlasted the sample capacity. */
internal data class VideoNoteSensorResult(
    val stats: ProbeFrameStats,
    val crop: String?,
    val zoom: Float?,
    val truncated: Boolean,
    /** The first values seen in the active recording segment (not the earlier preview). */
    val firstCrop: String? = null,
    val firstZoom: Float? = null,
    val cropChanged: Boolean = false,
    val zoomChanged: Boolean = false,
    /** Actual AE range/frame duration/stabilization reported by the camera; null = not reported. */
    val ae: AeCaptureSummary? = null
)

/**
 * Collects sensor timestamps and exposure from the capture results of the real recording session
 * (issue #234, §6). Only numbers are kept: no frames, no pixel access. The same camera session
 * serves the preview and the recorder, so this shows what the sensor delivered while recording,
 * which the separate opaque-stream check cannot.
 *
 * Lifecycle per take: [begin] when recording starts, [stop] when stop is requested (later results
 * belong to finalization, not to the file), [finish] to read and clear. [begin] again on a camera
 * switch starts a new segment, because timestamps of different camera devices are not comparable.
 */
internal class VideoNoteSensorTap(private val capacity: Int = 4000) {
    private val timestamps = ArrayList<Long>()
    private val exposures = ArrayList<Long>()
    private val aeSamples = ArrayList<AeCaptureSample>()
    private var active = false
    private var begun = false
    private var truncated = false
    private var crop: String? = null
    private var zoom: Float? = null
    private var firstCrop: String? = null
    private var firstZoom: Float? = null
    private var cropChanged = false
    private var zoomChanged = false

    /** Cheap check for the camera callback thread, before any work is spent on a result. */
    @Synchronized fun isActive(): Boolean = active

    /** Starts a fresh window; results before this call (preview only) are ignored. */
    @Synchronized fun begin() {
        timestamps.clear()
        exposures.clear()
        aeSamples.clear()
        crop = null
        zoom = null
        firstCrop = null
        firstZoom = null
        cropChanged = false
        zoomChanged = false
        truncated = false
        active = true
        begun = true
    }

    @Synchronized fun record(
        sensorTimestampNs: Long?,
        exposureNs: Long?,
        cropRegion: String? = null,
        zoomRatio: Float? = null,
        frameDurationNs: Long? = null,
        targetFpsRange: IntRange? = null,
        aeState: Int? = null,
        videoStabilizationMode: Int? = null,
        opticalStabilizationMode: Int? = null
    ) {
        if (!active || sensorTimestampNs == null) return
        if (cropRegion != null) {
            if (firstCrop == null) firstCrop = cropRegion
            if (crop != null && crop != cropRegion) cropChanged = true
            crop = cropRegion
        }
        if (zoomRatio != null && zoomRatio.isFinite()) {
            if (firstZoom == null) firstZoom = zoomRatio
            if (zoom != null && kotlin.math.abs(zoom!! - zoomRatio) > 0.02f) zoomChanged = true
            zoom = zoomRatio
        }
        if (timestamps.size >= capacity) {
            truncated = true
            return
        }
        timestamps += sensorTimestampNs
        exposureNs?.let { exposures += it }
        if (frameDurationNs != null || targetFpsRange != null || aeState != null ||
            videoStabilizationMode != null || opticalStabilizationMode != null) {
            aeSamples += AeCaptureSample(
                frameDurationNs = frameDurationNs,
                targetFpsRange = targetFpsRange,
                aeState = aeState,
                videoStabilizationMode = videoStabilizationMode,
                opticalStabilizationMode = opticalStabilizationMode
            )
        }
    }

    /** Stops collecting but keeps the data for [finish]. */
    @Synchronized fun stop() {
        active = false
    }

    /**
     * Returns the statistics of the window started by [begin] and clears everything. Null when no take
     * was begun or nothing arrived, so a previous take's numbers can never leak into a new report.
     */
    @Synchronized fun finish(targetFps: Int): VideoNoteSensorResult? {
        active = false
        val wasBegun = begun
        begun = false
        if (!wasBegun || timestamps.isEmpty()) {
            timestamps.clear(); exposures.clear(); aeSamples.clear(); crop = null; zoom = null
            firstCrop = null; firstZoom = null; cropChanged = false; zoomChanged = false; truncated = false
            return null
        }
        val first = timestamps.first()
        val last = timestamps.last()
        val interval = 1_000_000_000L / targetFps.coerceAtLeast(1)
        // The window is what the recording spanned plus one frame: coverage compares against that.
        val stats = measureProbeFrames(timestamps.toList(), exposures.toList(), targetFps, (last - first) + interval)
        val ae = summarizeAeCapture(aeSamples.toList(), targetFps).takeIf { it.hasData }
        val result = VideoNoteSensorResult(stats, crop, zoom, truncated, firstCrop, firstZoom, cropChanged, zoomChanged, ae)
        timestamps.clear(); exposures.clear(); aeSamples.clear(); crop = null; zoom = null
        firstCrop = null; firstZoom = null; cropChanged = false; zoomChanged = false; truncated = false
        return result
    }
}
