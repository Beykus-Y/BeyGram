package com.beykus.beygram.data.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of one opened mode. Only timing metadata is kept: frames are released unread. */
internal sealed interface ProbeOutcome {
    data class Measured(val stats: ProbeFrameStats, val verdict: ProbeVerdict, val failedCaptures: Int,
        val thermalBefore: Int?, val thermalAfter: Int?) : ProbeOutcome
    data class OpenFailed(val reason: String) : ProbeOutcome
    data class SessionFailed(val reason: String) : ProbeOutcome
    data object TimedOut : ProbeOutcome
    data object Cancelled : ProbeOutcome
    data object StoppedHot : ProbeOutcome
}

internal data class ProbeRow(val mode: ProbeMode, val outcome: ProbeOutcome?)

/**
 * Opt-in, sequential camera check. Opens one lens at a time with a single opaque stream (no
 * recording, no sound, no pixel access), asks for a frame-rate range and reads the sensor
 * timestamps of the delivered frames. The camera is fully closed before the next mode starts.
 */
internal class VideoNoteFpsProbe(private val context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Passive: reads characteristics only. */
    fun cameras(): List<ProbeCameraCaps> = manager.cameraIdList.mapNotNull { id ->
        runCatching { capsOf(id, manager.getCameraCharacteristics(id)) }.getOrNull()
    }

    fun thermalStatus(): Int? = runCatching { power?.currentThermalStatus }.getOrNull()

    suspend fun measure(mode: ProbeMode): ProbeOutcome {
        val range = mode.range ?: return ProbeOutcome.SessionFailed("not planned")
        val thermalBefore = thermalStatus()
        if (thermalBefore != null && thermalBefore >= PROBE_THERMAL_SEVERE) return ProbeOutcome.StoppedHot
        val thread = HandlerThread("beygram-fps-check").apply { start() }
        val handler = Handler(thread.looper)
        val executor = Executor { handler.post(it) }
        val reader = ImageReader.newInstance(mode.size.width, mode.size.height, ImageFormat.PRIVATE, 6)
        // Frames are acquired and closed immediately: nothing is decoded, copied or stored.
        reader.setOnImageAvailableListener({ r -> runCatching { r.acquireNextImage()?.close() } }, handler)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        val closed = CompletableDeferred<Unit>()
        // Completes once the open request settles; true when a CameraDevice existed (and so will close).
        val settled = CompletableDeferred<Boolean>()
        try {
            device = when (val opened = open(mode.cameraId, handler, closed, settled)) {
                is OpenResult.Opened -> opened.device
                is OpenResult.Failed -> return ProbeOutcome.OpenFailed(opened.reason)
            }
            session = configure(device, reader.surface, executor)
                ?: return ProbeOutcome.SessionFailed("configure")
            val timestamps = ArrayList<Long>()
            val exposures = ArrayList<Long>()
            var failed = 0
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val callback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    // The first frames come while auto-exposure settles; they are not counted.
                    if (SystemClock.elapsedRealtimeNanos() - startedAt < WARMUP_NS) return
                    val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                    synchronized(timestamps) {
                        timestamps += ts
                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { exposures += it }
                    }
                }
                override fun onCaptureFailed(s: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    synchronized(timestamps) { failed++ }
                }
            }
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(range.first, range.last))
            }.build()
            session.setRepeatingRequest(request, callback, handler)
            delay((WARMUP_NS + MEASURE_NS) / 1_000_000L)
            runCatching { session.stopRepeating() }
            val (ts, exp, failures) = synchronized(timestamps) { Triple(timestamps.toList(), exposures.toList(), failed) }
            val stats = measureProbeFrames(ts, exp, range.last)
            val thermalAfter = thermalStatus()
            return ProbeOutcome.Measured(stats, classifyProbe(stats, range.last, thermalAfter), failures, thermalBefore, thermalAfter)
        } catch (cancelled: CancellationException) {
            // CancellationException is an IllegalStateException: it must not become "session failed".
            throw cancelled
        } catch (failure: SecurityException) {
            return ProbeOutcome.OpenFailed("permission")
        } catch (failure: android.hardware.camera2.CameraAccessException) {
            return ProbeOutcome.SessionFailed("access ${failure.reason}")
        } catch (failure: IllegalArgumentException) {
            // The camera rejected the range/size pair for this session.
            return ProbeOutcome.SessionFailed(failure.javaClass.simpleName)
        } catch (failure: IllegalStateException) {
            return ProbeOutcome.SessionFailed(failure.javaClass.simpleName)
        } finally {
            runCatching { session?.close() }
            val toClose = device
            withContext(NonCancellable) {
                val hadDevice = if (toClose != null) {
                    runCatching { toClose.close() }
                    true
                } else {
                    // Cancelled or timed out while opening: keep the callback thread alive until the
                    // request settles, so a late onOpened can still close the camera (it does so itself).
                    withTimeoutOrNull(OPEN_SETTLE_TIMEOUT_MS) { settled.await() } ?: false
                }
                // Never start the next mode, or let recording start, while this camera is still releasing.
                if (hadDevice) withTimeoutOrNull(CLOSE_TIMEOUT_MS) { closed.await() }
            }
            reader.close()
            thread.quitSafely()
        }
    }

    private sealed interface OpenResult {
        data class Opened(val device: CameraDevice) : OpenResult
        data class Failed(val reason: String) : OpenResult
    }

    @SuppressLint("MissingPermission") // Checked by hasPermission() before the run starts.
    private suspend fun open(
        id: String,
        handler: Handler,
        closed: CompletableDeferred<Unit>,
        settled: CompletableDeferred<Boolean>
    ): OpenResult = suspendCancellableCoroutine { cont ->
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    // A late open after cancellation is closed here; the caller waits for onClosed.
                    if (cont.isActive) cont.resume(OpenResult.Opened(camera)) else camera.close()
                    settled.complete(true)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    settled.complete(true)
                    if (cont.isActive) cont.resume(OpenResult.Failed("disconnected"))
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    settled.complete(true)
                    if (cont.isActive) cont.resume(OpenResult.Failed(openErrorName(error)))
                }
                override fun onClosed(camera: CameraDevice) { closed.complete(Unit) }
            }, handler)
        } catch (failure: Exception) {
            settled.complete(false)
            throw failure
        }
    }

    private suspend fun configure(device: CameraDevice, surface: Surface, executor: Executor): CameraCaptureSession? =
        suspendCancellableCoroutine { cont ->
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(surface)),
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cont.isActive) cont.resume(session) else session.close()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
            device.createCaptureSession(config)
        }

    companion object {
        const val WARMUP_NS = 800_000_000L
        const val MEASURE_NS = 2_000_000_000L
        const val MODE_TIMEOUT_MS = 8_000L
        const val RUN_TIMEOUT_MS = 150_000L
        private const val CLOSE_TIMEOUT_MS = 2_000L
        private const val OPEN_SETTLE_TIMEOUT_MS = 5_000L

        fun openErrorName(error: Int): String = when (error) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "in use"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "max cameras in use"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "disabled by policy"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "device error"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "service error"
            else -> "error $error"
        }

        fun facingOf(value: Int?): ProbeFacing = when (value) {
            CameraCharacteristics.LENS_FACING_FRONT -> ProbeFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> ProbeFacing.BACK
            CameraCharacteristics.LENS_FACING_EXTERNAL -> ProbeFacing.EXTERNAL
            else -> ProbeFacing.UNKNOWN
        }

        fun capsOf(id: String, c: CameraCharacteristics): ProbeCameraCaps {
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = runCatching { map?.getOutputSizes(ImageFormat.PRIVATE)?.map { ProbeSize(it.width, it.height) } }
                .getOrNull().orEmpty()
            val durations = sizes.associateWith { size ->
                runCatching { map?.getOutputMinFrameDuration(ImageFormat.PRIVATE, android.util.Size(size.width, size.height)) ?: 0L }
                    .getOrDefault(0L)
            }
            val highSpeed = runCatching {
                map?.highSpeedVideoSizes.orEmpty().associate { size ->
                    ProbeSize(size.width, size.height) to map?.getHighSpeedVideoFpsRangesFor(size).orEmpty()
                        .map { it.lower..it.upper }
                }
            }.getOrDefault(emptyMap())
            return ProbeCameraCaps(
                cameraId = id,
                facing = facingOf(c.get(CameraCharacteristics.LENS_FACING)),
                normalRanges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.map { it.lower..it.upper }.orEmpty(),
                streamSizes = sizes,
                minFrameDurationNs = durations,
                highSpeed = highSpeed
            )
        }
    }
}

/** Runs one mode with a hard timeout; a hung camera HAL never blocks the whole check. */
internal suspend fun VideoNoteFpsProbe.measureWithTimeout(mode: ProbeMode): ProbeOutcome = try {
    withTimeout(VideoNoteFpsProbe.MODE_TIMEOUT_MS) { measure(mode) }
} catch (timeout: TimeoutCancellationException) {
    // Only this mode's own timeout is a result; the whole run's timeout or a cancel must propagate.
    currentCoroutineContext().ensureActive()
    ProbeOutcome.TimedOut
}
