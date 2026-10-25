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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val thermalBefore: Int?, val thermalAfter: Int?, val counters: ProbeCounters = ProbeCounters()) : ProbeOutcome
    data class OpenFailed(val reason: String) : ProbeOutcome
    data class SessionFailed(val reason: String) : ProbeOutcome
    data object TimedOut : ProbeOutcome
    data object Cancelled : ProbeOutcome
    data object StoppedHot : ProbeOutcome
}

internal data class ProbeRow(val mode: ProbeMode, val outcome: ProbeOutcome?)

/**
 * Opt-in, sequential camera check. Opens one lens at a time with a single YUV stream (no
 * recording, no sound, pixels never read), asks for a frame-rate range and reads the sensor
 * timestamps of the delivered frames. The camera is fully closed before the next mode starts.
 */
@OptIn(ExperimentalCoroutinesApi::class) // resume(value, onCancellation) releases a camera opened during cancellation.
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
        val cleanup = LongArray(1) { -1L }
        val outcome = measureMode(mode, cleanup)
        val cleanupMs = cleanup[0].takeIf { it >= 0 }
        return if (outcome is ProbeOutcome.Measured) outcome.copy(counters = outcome.counters.copy(cleanupMs = cleanupMs)) else outcome
    }

    /** [cleanupMs] receives how long closing the session and the camera took. */
    private suspend fun measureMode(mode: ProbeMode, cleanupMs: LongArray): ProbeOutcome {
        val range = mode.range ?: return ProbeOutcome.SessionFailed("not planned")
        val thermalBefore = thermalStatus()
        if (thermalBefore != null && thermalBefore >= PROBE_THERMAL_SEVERE) return ProbeOutcome.StoppedHot
        // Camera callbacks and buffer returns run on separate threads, so a busy result
        // callback can never delay returning buffers to the camera (and stall the stream).
        val thread = HandlerThread("beygram-fps-check").apply { start() }
        val readerThread = HandlerThread("beygram-fps-reader").apply { start() }
        val handler = Handler(thread.looper)
        val readerHandler = Handler(readerThread.looper)
        val executor = Executor { handler.post(it) }
        // A flexible YUV stream: frames are acquired and closed at once, pixels are never read.
        // The first version used an opaque PRIVATE reader; on Samsung M21 it delivered 0-5 frames
        // per mode, consistent with buffers that were never returned to the camera.
        val reader = ImageReader.newInstance(mode.size.width, mode.size.height, PROBE_FORMAT, READER_MAX_IMAGES)
        val counter = Counters()
        reader.setOnImageAvailableListener({ r ->
            counter.imagesAvailable.incrementAndGet()
            try {
                // Latest-image drains everything queued, so a slow callback cannot hold buffers.
                val image = r.acquireLatestImage()
                if (image != null) {
                    counter.imagesAcquired.incrementAndGet()
                    image.close()
                }
            } catch (failure: Exception) {
                if (counter.acquireErrors.getAndIncrement() == 0) counter.firstAcquireError = failure.javaClass.simpleName
            }
        }, readerHandler)
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
            val frameDurations = ArrayList<Long>()
            var startedAt = 0L
            val callback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(s: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                    counter.captureStarted.incrementAndGet()
                }
                override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (counter.results.getAndIncrement() == 0) counter.firstResultDelayMs = (now - startedAt) / 1_000_000L
                    // The first frames come while auto-exposure settles; they are not counted.
                    if (now - startedAt < WARMUP_NS) return
                    val ts = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
                    synchronized(timestamps) {
                        timestamps += ts
                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { exposures += it }
                        result.get(CaptureResult.SENSOR_FRAME_DURATION)?.let { frameDurations += it }
                    }
                }
                override fun onCaptureFailed(s: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    counter.failed.incrementAndGet()
                }
                override fun onCaptureBufferLost(s: CameraCaptureSession, request: CaptureRequest, target: Surface, frameNumber: Long) {
                    counter.buffersLost.incrementAndGet()
                }
                override fun onCaptureSequenceAborted(s: CameraCaptureSession, sequenceId: Int) {
                    counter.sequenceAborted = true
                }
            }
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(range.first, range.last))
            }.build()
            startedAt = SystemClock.elapsedRealtimeNanos()
            session.setRepeatingRequest(request, callback, handler)
            delay((WARMUP_NS + MEASURE_NS) / 1_000_000L)
            runCatching { session.stopRepeating() }
            val (ts, exp) = synchronized(timestamps) { timestamps.toList() to exposures.toList() }
            val durations = synchronized(timestamps) { frameDurations.toList() }
            val stats = measureProbeFrames(ts, exp, range.last, MEASURE_NS, durations)
            val thermalAfter = thermalStatus()
            return ProbeOutcome.Measured(stats, classifyProbe(stats, range.last, thermalAfter), counter.failed.get(),
                thermalBefore, thermalAfter, counter.snapshot())
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
            val cleanupStart = SystemClock.elapsedRealtime()
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
            readerThread.quitSafely()
            cleanupMs[0] = SystemClock.elapsedRealtime() - cleanupStart
        }
    }

    /** Thread-safe counters filled by camera and reader callbacks. */
    private class Counters {
        val captureStarted = AtomicInteger()
        val results = AtomicInteger()
        val failed = AtomicInteger()
        val buffersLost = AtomicInteger()
        val imagesAvailable = AtomicInteger()
        val imagesAcquired = AtomicInteger()
        val acquireErrors = AtomicInteger()
        @Volatile var firstAcquireError: String? = null
        @Volatile var firstResultDelayMs: Long? = null
        @Volatile var sequenceAborted = false

        fun snapshot() = ProbeCounters(
            captureStarted = captureStarted.get(), results = results.get(), failed = failed.get(),
            buffersLost = buffersLost.get(), imagesAvailable = imagesAvailable.get(),
            imagesAcquired = imagesAcquired.get(), acquireErrors = acquireErrors.get(),
            firstAcquireError = firstAcquireError, firstResultDelayMs = firstResultDelayMs,
            sequenceAborted = sequenceAborted
        )
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
                    if (cont.isActive) cont.resume(OpenResult.Opened(camera)) { camera.close() } else camera.close()
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
                        if (cont.isActive) cont.resume(session) { session.close() } else session.close()
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
        const val MEASURE_NS = PROBE_MEASURE_NS
        /** The stream format the probe opens; sizes and ceilings in the plan come from the same format. */
        const val PROBE_FORMAT = ImageFormat.YUV_420_888
        private const val READER_MAX_IMAGES = 4
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
            val sizes = runCatching { map?.getOutputSizes(PROBE_FORMAT)?.map { ProbeSize(it.width, it.height) } }
                .getOrNull().orEmpty()
            val durations = sizes.associateWith { size ->
                runCatching { map?.getOutputMinFrameDuration(PROBE_FORMAT, android.util.Size(size.width, size.height)) ?: 0L }
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
