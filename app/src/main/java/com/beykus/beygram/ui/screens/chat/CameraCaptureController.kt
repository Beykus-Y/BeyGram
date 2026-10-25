package com.beykus.beygram.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.beykus.beygram.R
import com.beykus.beygram.data.media.CameraCaptureMode
import com.beykus.beygram.data.media.CameraTakeOutcome
import com.beykus.beygram.data.media.cameraCaptureFileName
import com.beykus.beygram.data.media.cameraRemainingSeconds
import com.beykus.beygram.data.media.cameraTakeOutcome
import com.beykus.beygram.data.media.cameraVideoLimitSeconds
import com.beykus.beygram.data.media.cameraZoomTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * CameraX owner for regular photo/video capture in the composer.
 *
 * Unlike [VideoNoteRecorder] this never records a square note and never processes the result:
 * it hands a plain cache file to the attachment pipeline, which re-encodes photos and compresses
 * videos through the same path as picked media. Photo and video use cases are bound one at a
 * time because Preview + ImageCapture + VideoCapture is not guaranteed on LIMITED hardware.
 */
internal class CameraCaptureController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val onPhoto: (File) -> Unit,
    private val onVideo: (file: File, hasAudio: Boolean) -> Unit,
    private val onError: (String) -> Unit
) : DefaultLifecycleObserver {
    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    var ready by mutableStateOf(false)
        private set
    var mode by mutableStateOf(CameraCaptureMode.Photo)
        private set
    var frontCamera by mutableStateOf(false)
        private set
    var canSwitchCamera by mutableStateOf(false)
        private set
    var flashAvailable by mutableStateOf(false)
        private set
    var flashEnabled by mutableStateOf(false)
        private set
    var zoomRatio by mutableFloatStateOf(1f)
        private set
    var timerSeconds by mutableIntStateOf(0)
        private set
    var videoLimitSeconds by mutableIntStateOf(60)
        private set
    /** Countdown before a photo; 0 while idle. */
    var countdown by mutableIntStateOf(0)
        private set
    var recording by mutableStateOf(false)
        private set
    var startingRecording by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set

    private val executor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var started = false
    private var disposed = false
    private var photoBusy = false
    private var clockJob: Job? = null
    private var countdownJob: Job? = null
    private var activeRecording: Recording? = null
    private var takeFile: File? = null
    private var takeHasAudio = false
    private var takeStopRequested = false
    private var takeDiscard = false
    private var takeStartedAt = 0L
    private var startingTake: File? = null
    private var boundMode: CameraCaptureMode? = null
    private val lifecycleObserver = this

    init {
        cleanupStaleCaptures()
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    fun start() {
        if (started || disposed) return
        started = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            val cameraProvider = runCatching { future.get() }.getOrElse {
                ready = false
                onError(context.getString(R.string.camera_error_open))
                return@addListener
            }
            provider = cameraProvider
            canSwitchCamera = runCatching {
                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) &&
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            }.getOrDefault(false)
            // The PreviewView needs one layout pass before its display rotation is known.
            previewView.post { if (!disposed) bindCurrentMode() }
        }, executor)
    }

    fun selectMode(value: CameraCaptureMode) {
        if (recording || startingRecording || countdown > 0 || photoBusy || value == mode) return
        if (value != mode) {
            camcorderTorchOff()
            flashEnabled = false
        }
        mode = value
        if (started) bindCurrentMode()
    }

    fun nextTimer() {
        timerSeconds = when (timerSeconds) {
            0 -> 3
            3 -> 10
            else -> 0
        }
    }

    fun setVideoLimit(seconds: Int) {
        if (recording || startingRecording) return
        videoLimitSeconds = cameraVideoLimitSeconds(seconds)
    }

    fun toggleFlash() {
        if (!flashAvailable) {
            onError(context.getString(R.string.camera_flash_unavailable))
            return
        }
        flashEnabled = !flashEnabled
        if (mode == CameraCaptureMode.Photo) {
            imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        } else {
            runCatching { camera?.cameraControl?.enableTorch(flashEnabled) }
                .onFailure { onError(context.getString(R.string.camera_flash_failed)) }
        }
    }

    fun switchCamera() {
        if (recording || startingRecording || countdown > 0 || photoBusy || !canSwitchCamera) return
        camcorderTorchOff()
        frontCamera = !frontCamera
        flashEnabled = false
        // Force a rebind: the mode is unchanged, so the cached bound mode would skip it.
        boundMode = null
        bindCurrentMode()
    }

    fun zoomBy(scale: Float) {
        val bound = camera ?: return
        val state = bound.cameraInfo.zoomState.value
        val target = cameraZoomTarget(zoomRatio, scale, state?.minZoomRatio, state?.maxZoomRatio)
        zoomRatio = target
        bound.cameraControl.setZoomRatio(target)
    }

    /** Shutter in photo mode: starts a countdown or cancels a running one. */
    fun shutter() {
        if (mode == CameraCaptureMode.Video) {
            if (recording || startingRecording) stopRecording() else startRecording()
            return
        }
        if (countdown > 0) {
            cancelCountdown()
            return
        }
        if (photoBusy) return
        if (timerSeconds > 0) startCountdown() else takePhoto()
    }

    fun close() {
        cancelCountdown()
        if (recording || startingRecording) {
            takeDiscard = true
            takeStopRequested = true
            runCatching { activeRecording?.stop() }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        cancelCountdown()
        clockJob?.cancel()
        clockJob = null
        takeDiscard = true
        takeStopRequested = true
        runCatching { activeRecording?.stop() }
        activeRecording = null
        camcorderTorchOff()
        runCatching { provider?.unbindAll() }
        camera = null
        imageCapture = null
        videoCapture = null
        ready = false
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
    }

    override fun onPause(owner: LifecycleOwner) {
        cancelCountdown()
        if (recording || startingRecording) {
            // Leaving the app must not keep a camera and a microphone recording in the background.
            takeDiscard = true
            takeStopRequested = true
            runCatching { activeRecording?.stop() }
        }
        camcorderTorchOff()
        ready = false
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!started || disposed) return
        // CameraX reopens the bound use cases itself; only rebind when nothing is bound.
        if (camera != null) ready = true else bindCurrentMode()
    }

    private fun bindCurrentMode() {
        val cameraProvider = provider ?: return
        if (boundMode == mode && camera != null) return
        boundMode = mode
        ready = false
        camera = null
        imageCapture = null
        videoCapture = null
        runCatching {
            cameraProvider.unbindAll()
            val requested = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val selector = if (cameraProvider.hasCamera(requested)) {
                requested
            } else {
                // Rear cameras are optional in the manifest: fall back to the other lens once instead
                // of recursing on the same unavailable selector.
                val alternateFront = !frontCamera
                val alternate = if (alternateFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!cameraProvider.hasCamera(alternate)) {
                    ready = false
                    onError(context.getString(R.string.camera_error_open))
                    return
                }
                frontCamera = alternateFront
                alternate
            }
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val previewUseCase = Preview.Builder()
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                .setTargetRotation(rotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val useCase: androidx.camera.core.UseCase = when (mode) {
                CameraCaptureMode.Photo -> ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .setTargetRotation(rotation)
                    .build()
                    .also { imageCapture = it }
                CameraCaptureMode.Video -> {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
                        // Matches the Balanced preset, so a normal camera take is sent without a second encode.
                        .setTargetVideoEncodingBitRate(2_500_000)
                        .build()
                    VideoCapture.Builder(recorder)
                        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                        .setTargetRotation(rotation)
                        .build()
                        .also { videoCapture = it }
                }
            }
            val bound = cameraProvider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, useCase)
            camera = bound
            flashAvailable = runCatching { bound.cameraInfo.hasFlashUnit() }.getOrDefault(false)
            zoomRatio = 1f
            runCatching { bound.cameraControl.setZoomRatio(1f) }
            if (mode == CameraCaptureMode.Video && flashEnabled) {
                runCatching { bound.cameraControl.enableTorch(true) }
            }
            ready = true
        }.onFailure {
            ready = false
            boundMode = null
            onError(context.getString(R.string.camera_error_open))
        }
    }

    private fun startCountdown() {
        countdown = timerSeconds
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (countdown > 0) {
                delay(1_000)
                countdown -= 1
            }
            if (!disposed && countdown == 0) takePhoto()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown = 0
    }

    private fun takePhoto() {
        if (photoBusy || disposed) return
        val capture = imageCapture ?: return
        val file = newCaptureFile(CameraCaptureMode.Photo)
        photoBusy = true
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                photoBusy = false
                if (disposed) {
                    file.delete()
                    return
                }
                if (file.isFile && file.length() > 0L) onPhoto(file)
                else {
                    file.delete()
                    onError(context.getString(R.string.camera_error_capture))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                photoBusy = false
                file.delete()
                if (!disposed) onError(context.getString(R.string.camera_error_capture))
            }
        })
    }

    private fun startRecording() {
        if (recording || startingRecording || disposed) return
        val capture = videoCapture ?: return
        val file = newCaptureFile(CameraCaptureMode.Video)
        takeFile = file
        startingTake = file
        takeHasAudio = granted(Manifest.permission.RECORD_AUDIO)
        takeStopRequested = false
        takeDiscard = false
        startingRecording = true
        elapsedMs = 0L
        try {
            var pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
            if (takeHasAudio) pending = pending.withAudioEnabled()
            activeRecording = pending.start(executor) { event -> onVideoEvent(file, event) }
        } catch (failure: Exception) {
            startingRecording = false
            startingTake = null
            takeFile = null
            file.delete()
            onError(context.getString(R.string.camera_error_record))
        }
    }

    private fun stopRecording() {
        if (takeStopRequested) return
        if (!recording && !startingRecording) return
        takeStopRequested = true
        clockJob?.cancel()
        clockJob = null
        startingRecording = false
        // Keep `recording` true until CameraX finalizes the file: the shutter must not start a second take.
        runCatching { activeRecording?.stop() }
            .onFailure { onError(context.getString(R.string.camera_error_record)) }
    }

    private fun onVideoEvent(file: File, event: VideoRecordEvent) {
        if (startingTake !== file && takeFile !== file) return
        if (event is VideoRecordEvent.Start) {
            startingRecording = false
            recording = true
            takeStartedAt = SystemClock.elapsedRealtime()
            startClock()
            return
        }
        if (event is VideoRecordEvent.Finalize) finishVideo(file, event)
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = scope.launch {
            while (recording) {
                elapsedMs = SystemClock.elapsedRealtime() - takeStartedAt
                if (elapsedMs >= videoLimitSeconds * 1000L + 250L) {
                    stopRecording()
                    break
                }
                delay(100)
            }
        }
    }

    private fun finishVideo(file: File, event: VideoRecordEvent.Finalize) {
        val wasCurrent = takeFile === file || startingTake === file
        if (!wasCurrent) return
        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
        val outcome = cameraTakeOutcome(event.error, takeDiscard, durationMs, takeStopRequested)
        clockJob?.cancel()
        clockJob = null
        activeRecording = null
        takeFile = null
        startingTake = null
        recording = false
        startingRecording = false
        elapsedMs = 0L
        if (disposed) {
            file.delete()
            return
        }
        when (outcome) {
            CameraTakeOutcome.Send -> onVideo(file, takeHasAudio)
            CameraTakeOutcome.Discarded -> file.delete()
            CameraTakeOutcome.TooShort -> {
                file.delete()
                onError(context.getString(R.string.camera_error_too_short))
            }
            CameraTakeOutcome.Failed -> {
                file.delete()
                onError(context.getString(R.string.camera_error_record))
            }
        }
    }

    fun remainingSeconds(): Int = cameraRemainingSeconds(videoLimitSeconds, elapsedMs)

    private fun newCaptureFile(captureMode: CameraCaptureMode): File {
        val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        return directory.resolve(cameraCaptureFileName(captureMode, UUID.randomUUID().toString()))
    }

    private fun cleanupStaleCaptures() {
        val directory = File(context.cacheDir, CAPTURE_DIRECTORY)
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1_000L
        runCatching {
            directory.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun camcorderTorchOff() {
        if (flashEnabled) runCatching { camera?.cameraControl?.enableTorch(false) }
    }

    companion object {
        private const val CAPTURE_DIRECTORY = "media-capture"
    }
}
