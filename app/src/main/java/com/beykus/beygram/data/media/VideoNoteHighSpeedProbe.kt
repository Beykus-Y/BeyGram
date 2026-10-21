package com.beykus.beygram.data.media

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import com.beykus.beygram.R
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opt-in experiment (issue #234): what a constrained high-speed session really delivers, and whether a
 * normal session accepts a 60 FPS range that the camera does not list. Frames go to an encoder input
 * surface that discards its output: no file, no sound, no pixels read. Only sensor timestamps are kept.
 * The result says nothing about recording a circle with sound; it only separates "not in the normal
 * ranges" from "available in another mode".
 */
@OptIn(ExperimentalCoroutinesApi::class) // resume(value, onCancellation) releases a camera opened during cancellation.
internal object VideoNoteHighSpeedProbe {
    private const val WARMUP_NS = 800_000_000L
    private const val MODE_TIMEOUT_MS = 9_000L
    private const val MAX_VENDOR_KEYS = 12

    /** Per camera and size, the high-speed ranges worth measuring: the lowest one reaching 60 and the fixed 120. */
    internal fun highSpeedCandidates(ranges: List<IntRange>): List<IntRange> {
        val reaching = ranges.filter { it.last in 60..120 }
        val lowest = reaching.filter { it.first < it.last }.minByOrNull { it.last }
        val fixed = reaching.filter { it.first == it.last }.maxByOrNull { it.last }
        return listOfNotNull(lowest, fixed).distinct()
    }

    /** A rate counts only when the frames spread over most of the window: a short burst then a stall does not. */
    internal fun sustained(stats: ProbeFrameStats, minFps: Double = 54.0): Boolean =
        (stats.measuredFps ?: 0.0) >= minFps && stats.windowMs > 0 && stats.spanMs >= stats.windowMs * 0.8

    suspend fun run(context: Context): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val manager = context.getSystemService(CameraManager::class.java)
        val probe = VideoNoteFpsProbe(context)
        val sb = StringBuilder()
        sb.appendLine(context.getString(R.string.hs_heading))
        sb.appendLine(context.getString(R.string.camera_report_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT))
        sb.appendLine(context.getString(R.string.hs_method))
        if (!probe.hasPermission()) {
            sb.appendLine(context.getString(R.string.fps_check_no_permission))
            return@withContext sb.toString()
        }
        val cameras = probe.cameras()
        var reached60 = false
        var anyHighSpeed = false
        for (caps in cameras) {
            val characteristics = runCatching { manager.getCameraCharacteristics(caps.cameraId) }.getOrNull() ?: continue
            sb.appendLine()
            sb.appendLine(context.getString(R.string.fps_check_camera_heading, caps.cameraId, facingName(context, caps.facing)))
            vendorKeys(characteristics).let { keys ->
                sb.appendLine(if (keys.isEmpty()) context.getString(R.string.hs_vendor_none)
                    else context.getString(R.string.hs_vendor, keys.joinToString()))
            }
            // 1. A normal session asked for a 60 FPS range the camera does not list.
            if (caps.normalRanges.none { it.last >= 60 }) {
                for (size in listOf(ProbeSize(1920, 1080), ProbeSize(1280, 720)).filter { it in caps.streamSizes }) {
                    for (range in listOf(60..60, 30..60)) {
                        val mode = ProbeMode(caps.cameraId, caps.facing, size, 60, range, ProbeModeStatus.PLANNED, null)
                        val outcome = probe.measureWithTimeout(mode)
                        sb.appendLine(unlistedText(context, size, range, outcome))
                        if (outcome is ProbeOutcome.Measured && sustained(outcome.stats)) reached60 = true
                        if (outcome is ProbeOutcome.StoppedHot) { sb.appendLine(context.getString(R.string.fps_check_stopped_hot)); return@withContext sb.toString() }
                    }
                }
            }
            // 2. Constrained high-speed sessions.
            if (caps.highSpeed.isEmpty()) {
                sb.appendLine(context.getString(R.string.hs_camera_none))
                continue
            }
            anyHighSpeed = true
            sb.appendLine(context.getString(R.string.hs_sizes, caps.highSpeed.entries.joinToString("; ") { (size, ranges) ->
                "$size: " + ranges.joinToString { "${it.first}..${it.last}" } }))
            val sizes = caps.highSpeed.keys.filter { (it.width == 1920 && it.height == 1080) || (it.width == 1280 && it.height == 720) }
            for (size in sizes) {
                for (range in highSpeedCandidates(caps.highSpeed[size].orEmpty())) {
                    if ((probe.thermalStatus() ?: 0) >= PROBE_THERMAL_SEVERE) {
                        sb.appendLine(context.getString(R.string.fps_check_stopped_hot)); return@withContext sb.toString()
                    }
                    val result = measureHighSpeed(manager, caps.cameraId, size, range)
                    sb.appendLine(highSpeedText(context, size, range, result))
                    if (result.sustained) reached60 = true
                    delay(300)
                }
            }
        }
        sb.appendLine()
        sb.appendLine(when {
            reached60 -> context.getString(R.string.hs_conclusion_yes)
            anyHighSpeed -> context.getString(R.string.hs_conclusion_no_hs)
            else -> context.getString(R.string.hs_conclusion_no)
        })
        sb.appendLine(context.getString(R.string.hs_audio_note))
        sb.toString()
    }

    private fun facingName(context: Context, facing: ProbeFacing): String = context.getString(when (facing) {
        ProbeFacing.FRONT -> R.string.camera_facing_front
        ProbeFacing.BACK -> R.string.camera_facing_back
        ProbeFacing.EXTERNAL -> R.string.camera_facing_external
        ProbeFacing.UNKNOWN -> R.string.camera_facing_unknown
    })

    /** Names (no values) of vendor-specific request keys: input for a later, targeted vendor experiment. */
    private fun vendorKeys(c: CameraCharacteristics): List<String> = runCatching {
        c.availableCaptureRequestKeys.map { it.name }
            .filter { name -> !name.startsWith("android.") && !name.startsWith("com.android.") }
            .distinct().take(MAX_VENDOR_KEYS)
    }.getOrDefault(emptyList())

    private fun ms(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "?"

    private fun unlistedText(context: Context, size: ProbeSize, range: IntRange, outcome: ProbeOutcome): String = when (outcome) {
        is ProbeOutcome.Measured -> context.getString(R.string.hs_unlisted_measured, size.toString(), "${range.first}..${range.last}",
            outcome.stats.measuredFps?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "?",
            outcome.stats.frames, ms(outcome.stats.p50GapMs), ms(outcome.stats.p95GapMs), ms(outcome.stats.maxGapMs))
        is ProbeOutcome.OpenFailed -> context.getString(R.string.hs_mode_failed, size.toString(), "${range.first}..${range.last}", outcome.reason)
        is ProbeOutcome.SessionFailed -> context.getString(R.string.hs_mode_failed, size.toString(), "${range.first}..${range.last}", outcome.reason)
        else -> context.getString(R.string.hs_mode_failed, size.toString(), "${range.first}..${range.last}", outcome.javaClass.simpleName)
    }

    private fun highSpeedText(context: Context, size: ProbeSize, range: IntRange, r: HighSpeedResult): String =
        if (r.error != null) context.getString(R.string.hs_mode_failed, size.toString(), "${range.first}..${range.last}", r.error)
        else context.getString(R.string.hs_measured, size.toString(), "${range.first}..${range.last}",
            r.fps?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "?", r.frames, r.burst,
            ms(r.p50), ms(r.p95), ms(r.max))

    private class HighSpeedResult(val fps: Double?, val frames: Int, val burst: Int, val p50: Double?, val p95: Double?, val max: Double?, val error: String?, val sustained: Boolean = false)

    private fun failed(reason: String) = HighSpeedResult(null, 0, 0, null, null, null, reason)

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun measureHighSpeed(manager: CameraManager, id: String, size: ProbeSize, range: IntRange): HighSpeedResult {
        val thread = HandlerThread("beygram-hs-check").apply { start() }
        val handler = Handler(thread.looper)
        val executor = Executor { handler.post(it) }
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var openRequested = false
        val encoderFailed = java.util.concurrent.atomic.AtomicBoolean(false)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        val closed = CompletableDeferred<Unit>()
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, range.last)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            try {
                val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                codec = enc
                enc.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(c: MediaCodec, index: Int) {}
                    override fun onOutputBufferAvailable(c: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        runCatching { c.releaseOutputBuffer(index, false) }
                    }
                    override fun onError(c: MediaCodec, e: MediaCodec.CodecException) { encoderFailed.set(true) }
                    override fun onOutputFormatChanged(c: MediaCodec, f: MediaFormat) {}
                }, handler)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface = enc.createInputSurface()
                enc.start()
            } catch (failure: Exception) {
                return failed("encoder: " + failure.javaClass.simpleName)
            }
            val target: Surface = surface ?: return failed("encoder: no surface")
            openRequested = true
            val opened = withTimeoutOrNull(MODE_TIMEOUT_MS) {
                suspendCancellableCoroutine<CameraDevice?> { cont ->
                    try {
                        manager.openCamera(id, object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) { if (cont.isActive) cont.resume(camera) { camera.close() } else camera.close() }
                            override fun onDisconnected(camera: CameraDevice) { camera.close(); if (cont.isActive) cont.resume(null) }
                            override fun onError(camera: CameraDevice, error: Int) { camera.close(); if (cont.isActive) cont.resume(null) }
                            override fun onClosed(camera: CameraDevice) { closed.complete(Unit) }
                        }, handler)
                    } catch (failure: Exception) {
                        openRequested = false
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } ?: return failed("open")
            device = opened
            val dev: CameraDevice = opened
            val configured = withTimeoutOrNull(MODE_TIMEOUT_MS) {
                suspendCancellableCoroutine<CameraCaptureSession?> { cont ->
                    try {
                        dev.createCaptureSession(SessionConfiguration(
                            SessionConfiguration.SESSION_HIGH_SPEED, listOf(OutputConfiguration(target)), executor,
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) { if (cont.isActive) cont.resume(s) { s.close() } else s.close() }
                                override fun onConfigureFailed(s: CameraCaptureSession) { if (cont.isActive) cont.resume(null) }
                            }))
                    } catch (failure: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } ?: return failed("session")
            session = configured
            val hs = configured as? CameraConstrainedHighSpeedCaptureSession ?: return failed("not a high-speed session")
            val builder = dev.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(range.first, range.last))
                addTarget(target)
            }
            val burst = hs.createHighSpeedRequestList(builder.build())
            val timestamps = ArrayList<Long>()
            val exposures = ArrayList<Long>()
            var firstAt = 0L
            val callback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    synchronized(timestamps) {
                        if (firstAt == 0L) firstAt = now
                        if (now - firstAt < WARMUP_NS) return
                        result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { timestamps += it }
                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { exposures += it }
                    }
                }
            }
            hs.setRepeatingBurst(burst, callback, handler)
            delay((WARMUP_NS + PROBE_MEASURE_NS) / 1_000_000L + 400L)
            runCatching { hs.stopRepeating() }
            val stats = synchronized(timestamps) { measureProbeFrames(timestamps.toList(), exposures.toList(), range.last) }
            if (encoderFailed.get()) return failed("encoder error")
            return HighSpeedResult(stats.measuredFps, stats.frames, burst.size, stats.p50GapMs, stats.p95GapMs, stats.maxGapMs, null, sustained(stats))
        } catch (failure: Exception) {
            return failed(failure.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) {
                runCatching { session?.close() }
                runCatching { device?.close() }
                // A timed-out open can still complete later: that late onOpened closes the camera itself.
                if (device != null || openRequested) withTimeoutOrNull(if (device != null) 2_000L else 5_000L) { closed.await() }
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { surface?.release() }
                thread.quitSafely()
            }
        }
    }
}
