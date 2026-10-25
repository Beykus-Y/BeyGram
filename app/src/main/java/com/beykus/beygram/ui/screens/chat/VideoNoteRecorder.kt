package com.beykus.beygram.ui.screens.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.util.Range
import android.util.Rational
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.ResolutionInfo
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.R
import com.beykus.beygram.data.media.VideoNoteSensorTap
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.data.media.VideoMessageSettings
import com.beykus.beygram.data.media.VideoNoteDiagnostics
import com.beykus.beygram.data.media.VideoNoteCameraSurvey
import com.beykus.beygram.data.media.VideoNoteSourceQuality
import com.beykus.beygram.data.media.VideoNoteUseCaseGeometry
import com.beykus.beygram.data.media.videoNoteUseCaseGeometrySummary
import com.beykus.beygram.data.media.VideoNoteFpsCheck
import com.beykus.beygram.data.media.VideoNoteFpsDecision
import com.beykus.beygram.data.media.VideoNoteStatus
import com.beykus.beygram.data.media.previewFrameRateFor
import com.beykus.beygram.data.media.yesNoUnknown
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.VideoQualityPreset
import com.beykus.beygram.data.media.videoMirrorToggleSupported
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

private const val NORMAL_MAX_DURATION_MS = 60_000L
private const val LONG_MAX_DURATION_MS = 3_600_000L
private const val MIN_DURATION_SECONDS = 1
private const val SIDE_PX = 480
/** Сдвиг пальца влево, после которого отпускание отменяет запись. */
private const val CANCEL_DRAG_DP = 96f
private const val TAG = "BeyGramRecorder"
// The FPS check settles a pending open (5 s) and then waits for the close callback (2 s).
private const val FPS_CHECK_RELEASE_TIMEOUT_MS = 8_000L
/** Кадр обрезается по кругу при обработке (маска в VideoNoteProcessor), поэтому запись кружков включена. */
internal const val VIDEO_NOTE_RECORDING_ENABLED = true

internal val videoNotePermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

internal fun videoNoteAllowed(context: Context): Boolean =
    videoNotePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Одна запись: пишется или уже останавливается и ждёт финализации CameraX. */
private class VideoTake(val file: File, val settings: VideoMessageSettings, val sessionName: String) {
    var discard = false
    var recording: Recording? = null
    var previewFirst = false
    var stopRequested = false
}

/** Запись кружка через CameraX: квадратный кадр по центру (ViewPort 1:1). Отпускание отправляет, сдвиг влево отменяет. */
internal class VideoNoteRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val currentSettings: () -> VideoMessageSettings,
    /** Сырая запись готова к экспорту: обработку выполняет VideoNoteExportCenter, переживая выход из чата. */
    private val onTakeReady: (raw: File, settings: VideoMessageSettings, sessionName: String, previewFirst: Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    var active by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
        private set
    var canceling by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    val durationLimitMs: Long get() = maxDurationMs
    /** Выбор камеры живёт внутри одной записи: каждая запись начинается с фронтальной камеры, а если её нет, с основной. */
    var preferFrontCamera by mutableStateOf(true)
        private set
    /** Переключатель показывается, только если на устройстве есть и фронтальная, и основная камера. */
    var canSwitchCamera by mutableStateOf(false)
        private set
    /** Запись фактически началась: CameraX сообщил о Start. До этого переключать камеру нельзя. */
    var started by mutableStateOf(false)
        private set
    /** Подсветка привязана к выбранной камере: torch для камер с фонариком, экран — для фронтальной без него. */
    var lightMode by mutableStateOf(VideoNoteLightMode.Unavailable)
        private set
    var lightEnabled by mutableStateOf(false)
        private set
    /** Кратность зума текущей камеры. Жест масштабирования меняет её до и во время записи; при смене камеры сбрасывается к 1x. */
    var zoomRatio by mutableFloatStateOf(1f)
        private set

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    /** Привязанная камера: нужна для управления зумом, после отвязки обнуляется. */
    private var camera: Camera? = null
    /** Диапазон FPS, зашитый в текущий VideoCapture на этапе создания: пересоздать его без обрыва записи нельзя. */
    private var captureFpsRange: Range<Int>? = null
    private var captureFpsDecision: VideoNoteFpsDecision? = null
    /** What the preview asked for; logged next to the recording range so a mismatch is visible. */
    private var previewFpsRange: IntRange? = null
    private var captureCameraId: String? = null
    private var captureSourceSize: Pair<Int, Int>? = null
    /** Actual CameraX stream/crop rectangles, as opposed to the requested SD/HD plan. */
    private var previewGeometry: String? = null
    private var videoGeometry: String? = null
    private var captureQuality: VideoNoteSourceQuality? = null
    /** Only a freshly created HD Recorder can retry with default aspect; persistent recording is never rebuilt mid-take. */
    private var capturePrefersFourThree = false
    private var captureFourThreeFallback = false
    /** Persistent-запись не отвязывается от VideoCapture при перепривязке камеры. Если устройство её не умеет, переключение скрывается. */
    private var persistentSupported = true
    private var take: VideoTake? = null
    private var timer: Job? = null
    private var startedAt = 0L
    private var maxDurationMs = NORMAL_MAX_DURATION_MS
    private var attempt = 0
    /** Callback от старой камеры не должен включать подсветку уже после переключения/закрытия. */
    private var lightRequest = 0
    private val lightLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) { clearLight() }
        override fun onStop(owner: LifecycleOwner) { clearLight() }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(lightLifecycleObserver)
        if (VIDEO_NOTE_RECORDING_ENABLED) probeCameras()
    }

    private fun probeCameras() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            canSwitchCamera = runCatching {
                val cameraProvider = future.get()
                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            }.getOrDefault(false)
        }, ContextCompat.getMainExecutor(context))
    }

    /** Текущий фонарик управляется через CameraX, без отдельного CameraManager и конкурирующей камеры. */
    fun toggleLight() {
        if (!active || !started || camera == null || lightMode == VideoNoteLightMode.Unavailable) return
        requestLight(!lightEnabled)
    }

    private fun requestLight(enabled: Boolean) {
        val bound = camera ?: return
        val mode = lightMode
        if (mode == VideoNoteLightMode.Unavailable) return
        val token = ++lightRequest
        lightEnabled = enabled
        if (mode != VideoNoteLightMode.Torch) return
        val future = try {
            bound.cameraControl.enableTorch(enabled)
        } catch (error: Exception) {
            onTorchFailure(bound, token, enabled, error)
            return
        }
        future.addListener({
            val failure = runCatching { future.get() }.exceptionOrNull()
            if (failure != null) onTorchFailure(bound, token, enabled, failure)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onTorchFailure(bound: Camera, token: Int, enabling: Boolean, cause: Throwable) {
        // Ignore callbacks from a replaced camera or superseded torch requests.
        if (token != lightRequest || camera !== bound) return
        Log.w(TAG, "video_note_torch_failed front=$preferFrontCamera enabling=$enabling", cause)
        if (!enabling) {
            // A rejected disable request does not prove the lamp is off. When CameraX
            // has not published OFF, keep the button lit so users can retry.
            lightEnabled = bound.cameraInfo.torchState.value != TorchState.OFF
            if (lightEnabled) onError(context.getString(R.string.recorder_light_off_failed))
            return
        }
        if (preferFrontCamera) {
            // Some front cameras report a flash unit but reject torch for VideoCapture.
            lightMode = VideoNoteLightMode.Screen
            lightEnabled = true
        } else {
            lightEnabled = false
            onError(context.getString(R.string.recorder_light_failed))
        }
    }

    /** Сброс токена защищает от поздних ответов enableTorch при смене камеры и завершении записи. */
    private fun clearLight(resetMode: Boolean = false) {
        val bound = camera
        val hadTorch = lightMode == VideoNoteLightMode.Torch && bound != null
        val token = ++lightRequest
        lightEnabled = false
        if (hadTorch && bound != null) {
            try {
                val future = bound.cameraControl.enableTorch(false)
                future.addListener({
                    val failure = runCatching { future.get() }.exceptionOrNull()
                    if (failure != null) onTorchFailure(bound, token, enabling = false, cause = failure)
                }, ContextCompat.getMainExecutor(context))
            } catch (error: Exception) {
                onTorchFailure(bound, token, enabling = false, cause = error)
            }
        }
        if (resetMode) lightMode = VideoNoteLightMode.Unavailable
    }

    /** Смена камеры только во время записи: один Recorder и VideoCapture живут всю запись, при переключении перепривязывается та же пара. */
    fun switchCamera() {
        val cameraProvider = provider ?: return
        val capture = videoCapture ?: return
        val current = take ?: return
        if (!active || !started || !canSwitchCamera || !persistentSupported) return
        val previousSelector = if (preferFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        // hasCamera/getCameraInfo умеют бросаться, поэтому весь этап capability-проверок тоже внутри try.
        var unbound = false
        val previouslyLit = lightEnabled
        try {
            val targetFront = !preferFrontCamera
            val selector = if (targetFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!cameraProvider.hasCamera(selector)) return
            // FPS у capture задан на этапе создания; если целевая камера такой диапазон не поддерживает, переключение запрещаем:
            // пересоздать capture без обрыва persistent-записи нельзя.
            val captureFps = captureFpsRange
            if (captureFps != null) {
                val targetSupported = cameraProvider.getCameraInfo(selector).supportedFrameRateRanges.map { it.lower..it.upper }
                val compatible = targetSupported.any { range -> range.first <= captureFps.lower && captureFps.upper <= range.last }
                if (!compatible) {
                    Log.i(TAG, "video_camera_switch_refused fps=$captureFps supported=$targetSupported")
                    VideoNoteDiagnostics.append(current.sessionName, context.getString(R.string.vn_entry_switch_refused))
                    onError(context.getString(R.string.recorder_camera_switch_failed))
                    return
                }
            }
            if (captureFps != null && captureFps.upper >= 50) {
                val size = captureSourceSize
                val selectedQuality = captureQuality
                val targetInfo = cameraProvider.getCameraInfo(selector)
                if (size == null || selectedQuality == null || !VideoNoteCameraSurvey.compatibleHighFpsSwitch(
                        context, targetInfo, selectedQuality, size, captureFps.lower..captureFps.upper
                    )) {
                    Log.i(TAG, "video_camera_switch_refused incompatible high-FPS quality/stream")
                    VideoNoteDiagnostics.append(current.sessionName, context.getString(R.string.vn_entry_switch_refused))
                    onError(context.getString(R.string.recorder_camera_switch_failed))
                    return
                }
            }
            Log.i(TAG, "video_camera_switch_started front=$targetFront")
            zoomRatio = 1f
            unbindCamera()
            unbound = true
            bind(cameraProvider, selector, current.settings, capture)
            preferFrontCamera = targetFront
            if (previouslyLit && lightMode != VideoNoteLightMode.Unavailable) requestLight(true)
            Log.i(TAG, "video_camera_switched front=$preferFrontCamera")
            // Timestamps of different camera devices are not comparable: measure the new camera on its own.
            if (sensorTap.isActive()) sensorTap.begin()
            VideoNoteDiagnostics.append(current.sessionName, context.getString(R.string.vn_entry_switched,
                context.getString(if (preferFrontCamera) R.string.camera_facing_front else R.string.camera_facing_back),
                previewFpsRange?.let { "${it.first}..${it.last}" } ?: context.getString(R.string.camera_report_range_default)))
        } catch (failure: Exception) {
            Log.e(TAG, "video_camera_switch_failed", failure)
            VideoNoteDiagnostics.append(current.sessionName, context.getString(R.string.vn_entry_switch_failed, failure.javaClass.simpleName))
            if (!unbound) {
                // Камера ещё привязана: сбоила только проверка, запись продолжается без изменений.
                onError(context.getString(R.string.recorder_camera_switch_failed))
                return
            }
            val restored = runCatching { bind(cameraProvider, previousSelector, current.settings, capture) }
            if (restored.isSuccess) {
                // Запись продолжается с прежней камерой: persistent-запись пережила отвязку, кадр возвращён.
                if (previouslyLit && lightMode != VideoNoteLightMode.Unavailable) requestLight(true)
                onError(context.getString(R.string.recorder_camera_switch_failed))
                return
            }
            Log.e(TAG, "video_camera_restore_failed", restored.exceptionOrNull())
            // Привязанной камеры нет: аварийно завершаем запись, иначе persistent Recording ждёт кадров,
            // таймер продолжает тикать, а пользователь получает пустой клип и зависший интерфейс.
            current.discard = true
            active = false
            locked = false
            canceling = false
            started = false
            timer?.cancel()
            timer = null
            requestStop(current)
            recordRecordingFailure("camera_switch", failure)
            onError(context.getString(R.string.recorder_camera_switch_aborted))
        }
    }

    /** Нажатие: камера открывается асинхронно, запись начинается, когда она готова. */
    fun press() {
        if (active || take != null) return
        if (VideoNoteExportGate.busy) {
            // Шлюз процесс-глобальный: экспорт прошлой записи продолжается даже из другого чата или после пересоздания экрана.
            onError(context.getString(R.string.recorder_processing_busy))
            return
        }
        // The opt-in camera FPS check may still hold a lens after leaving settings: recording wins,
        // but CameraX binds only after the check has closed that lens.
        val stoppingCheck = VideoNoteFpsCheck.cancelForRecording()
        val settings = currentSettings()
        Log.i(TAG, "video_start_requested preset=${settings.preset} long=${settings.longRecordingEnabled}")
        maxDurationMs = if (settings.longRecordingEnabled) LONG_MAX_DURATION_MS else NORMAL_MAX_DURATION_MS
        // Проверяем пиковый объём: RAW с камеры и перекодированные сегменты живут на диске одновременно.
        val requiredBytes = videoPeakStorageBytes(videoCameraBitrate(settings), maxDurationMs / 1000)
        if (StatFs(context.filesDir.absolutePath).availableBytes < requiredBytes) {
            recordAppEvent(EventKind.UI, "video_note_storage_low", "Кружок: мало места на устройстве", Severity.WARNING)
            onError(context.getString(R.string.recorder_storage_low))
            return
        }
        active = true
        locked = false
        canceling = false
        started = false
        clearLight(resetMode = true)
        zoomRatio = 1f
        capturePrefersFourThree = false
        captureFourThreeFallback = false
        elapsedMs = 0L
        val current = ++attempt
        if (stoppingCheck != null) {
            scope.launch {
                withTimeoutOrNull(FPS_CHECK_RELEASE_TIMEOUT_MS) { stoppingCheck.join() }
                if (active && current == attempt) openCamera(current, settings)
            }
        } else openCamera(current, settings)
    }

    private fun openCamera(current: Int, settings: VideoMessageSettings) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Ответ камеры от более раннего нажатия игнорируется, чтобы не начать вторую запись.
            if (!active || current != attempt) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                canSwitchCamera = videoSwitchCameraAvailable(
                    persistentSupported,
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA),
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                )
                // Каждая запись начинается с фронтальной камеры; без неё — с основной.
                val startSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else CameraSelector.DEFAULT_BACK_CAMERA
                preferFrontCamera = startSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                try {
                    val preferred = createCapture(cameraProvider, startSelector, settings, allowFourThree = true)
                    videoCapture = preferred
                    bind(cameraProvider, startSelector, settings, preferred)
                } catch (bindFailure: Exception) {
                    // Some cameras cannot bind an HD 4:3 VideoCapture together with Preview.
                    // Do not fail the entire recording: retry ONCE with the original CameraX
                    // aspect policy, before a persistent Recording is started.
                    if (!videoNoteShouldRetryDefaultAspect(capturePrefersFourThree, bindFailure)) throw bindFailure
                    Log.w(TAG, "video_fov_4_3_unavailable_fallback", bindFailure)
                    cameraProvider.unbindAll()
                    camera = null
                    val fallback = createCapture(cameraProvider, startSelector, settings, allowFourThree = false)
                    videoCapture = fallback
                    bind(cameraProvider, startSelector, settings, fallback)
                    captureFourThreeFallback = true
                }
                startRecording(settings)
            } catch (failure: Exception) {
                Log.e(TAG, "video_camera_open_failed")
                recordRecordingFailure("camera_open", failure)
                fail(context.getString(R.string.recorder_camera_open_failed))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Жест масштабирования: множитель пальцев умножается на текущий зум и ограничивается диапазоном камеры. */
    fun zoomBy(scale: Float) {
        val bound = camera ?: return
        applyZoom(bound, zoomRatio * scale)
    }

    /** Палец ушёл влево за порог: при отпускании запись будет отменена. */
    fun onDrag(cancelNow: Boolean) {
        canceling = cancelNow
    }

    fun lock() { if (active) { locked = true; canceling = false; Log.i(TAG, "video_recording_locked") } }

    /** Отпускание: [cancel] отменяет запись, иначе кружок уходит в отправку после финализации. */
    fun release(cancel: Boolean) {
        if (!active || locked) return
        Log.i(TAG, "video_recording_released cancel=$cancel elapsedMs=$elapsedMs")
        active = false
        canceling = false
        timer?.cancel()
        timer = null
        val current = take
        if (current == null) {
            // Камера ещё открывается: записи пока нет. Отпускание без отмены сообщает о короткой записи, а не теряет нажатие.
            unbindCamera()
            if (!cancel) onError(context.getString(R.string.recorder_too_short))
            return
        }
        current.discard = current.discard || cancel
        requestStop(current)
    }

    fun stopLocked() { if (active && locked) stop(preview = true) }

    fun sendLocked() { if (active && locked) stop(preview = false) }

    fun stopForRecovery() { if (active) stop(preview = true) }

    fun discardLocked() {
        if (!active || !locked) return
        take?.discard = true
        stop(preview = false)
    }

    fun dispose() {
        lifecycleOwner.lifecycle.removeObserver(lightLifecycleObserver)
        clearLight()
        if (active) stop(preview = true)
        locked = false
        timer?.cancel()
        timer = null
        val current = take
        if (current == null) {
            unbindCamera()
        } else requestStop(current)
    }

    private fun recordStarted() = recordAppEvent(
        kind = EventKind.UI,
        code = "video_note_recording_started",
        text = "Кружок: запись началась",
        attrs = mapOf("persistent" to persistentSupported.toString())
    )

    private fun recordRecordingFailure(stage: String, cause: Exception) = recordAppEvent(
        kind = EventKind.UI,
        code = "video_note_recording_failed",
        text = "Кружок: сбой записи",
        severity = Severity.ERROR,
        attrs = mapOf("stage" to stage, "cause" to cause.javaClass.simpleName)
    )

    private fun fail(message: String) {
        clearLight()
        active = false
        locked = false
        canceling = false
        started = false
        timer?.cancel()
        timer = null
        if (take == null) unbindCamera()
        onError(message)
    }

    private fun stop(preview: Boolean) {
        if (!active) return
        clearLight()
        Log.i(TAG, "video_recording_stopping preview=$preview elapsedMs=$elapsedMs")
        active = false
        locked = false
        canceling = false
        started = false
        timer?.cancel()
        timer = null
        take?.let { current ->
            current.previewFirst = preview
            requestStop(current)
        }
    }

    private fun requestStop(current: VideoTake) {
        if (current.stopRequested) return
        clearLight()
        current.stopRequested = true
        // Results after this point belong to finalization, not to the recorded file.
        sensorTap.stop()
        current.recording?.stop()
    }

    /** Localized light source for the diagnostics report; never claims that a torch was on. */
    private fun lightSourceLabel(): String = context.getString(when (lightMode) {
        VideoNoteLightMode.Torch -> R.string.vn_light_torch
        VideoNoteLightMode.Screen -> R.string.vn_light_screen
        VideoNoteLightMode.Unavailable -> R.string.vn_light_unavailable
    })

    /**
     * Одно правило и для превью, и для записи: иначе отправленный кружок расходится с тем, что видно при съёмке.
     * До API 33 переключателя нет, и фронтальная запись отражается как превью PreviewView.
     */
    private fun mirrorMode(settings: VideoMessageSettings): Int =
        if (settings.mirrorFrontCamera || !videoMirrorToggleSupported()) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF

    /** Пара Recorder и VideoCapture создаётся один раз на запись: persistent-запись требует, чтобы при смене камеры она не пересоздавалась. */
    private fun createCapture(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector,
        settings: VideoMessageSettings,
        allowFourThree: Boolean
    ): VideoCapture<Recorder> {
        val side = when (settings.preset) {
            VideoQualityPreset.Standard -> 384
            VideoQualityPreset.High -> 480
            VideoQualityPreset.Maximum -> 640
            VideoQualityPreset.Custom -> settings.customSide
        }
        val bitrate = videoCameraBitrate(settings)
        val requestedQuality = if (side > 480) Quality.HD else Quality.SD
        val supportedFps = cameraProvider.getCameraInfo(selector).supportedFrameRateRanges
        captureCameraId = VideoNoteCameraSurvey.cameraId(cameraProvider.getCameraInfo(selector))
        // Camera2 normal ranges and H.264 capabilities constrain claims, but cannot guarantee
        // Preview+VideoCapture session combinations or low-light exposure behaviour.
        // The settings screen's diagnostics call the same function, so both always agree.
        val plan = VideoNoteCameraSurvey.recordingPlan(
            context, settings, captureCameraId, supportedFps.map { it.lower..it.upper },
            cameraProvider.getCameraInfo(selector)
        )
        val decision = plan.decision
        val quality = if (plan.quality == VideoNoteSourceQuality.SD) Quality.SD else requestedQuality
        captureSourceSize = plan.width to plan.height
        captureQuality = plan.quality
        val chosenFps = decision.selected
        val fpsRange = chosenFps?.let { Range(it.first, it.last) }
        captureFpsDecision = decision
        Log.i(TAG, "video_fps requested=${decision.requested} selected=$chosenFps cause=${decision.cause} quality=$quality")
        // HD commonly negotiates a 16:9 stream. A square ViewPort then discards
        // more horizontal pixels than a 4:3 source. Prefer 4:3 only for HD at
        // normal <=30 FPS: forcing it at 60 FPS could invalidate the FPS plan.
        // Recorder.setAspectRatio can reject an unsupported combination at bind,
        // so openCamera retries with CameraX's original default aspect once.
        capturePrefersFourThree = allowFourThree && videoNotePreferFourThreeCapture(plan.quality, chosenFps)
        val qualitySelector = QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
        val recorderBuilder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .setTargetVideoEncodingBitRate(bitrate)
        if (capturePrefersFourThree) recorderBuilder.setAspectRatio(AspectRatio.RATIO_4_3)
        val captureBuilder = VideoCapture.Builder(recorderBuilder.build())
        // Запись фронтальной камеры отражается так же, как превью: отправленный кружок совпадает с тем, что видно при записи.
        captureBuilder.setMirrorMode(mirrorMode(settings))
        if (fpsRange != null) captureBuilder.setTargetFrameRate(fpsRange)
        captureFpsRange = fpsRange
        if (settings.diagnosticsEnabled) attachSensorTap(captureBuilder)
        return captureBuilder.build()
    }

    /** Привязывает превью и уже созданный capture к выбранной камере. При переключении вызывается повторно с тем же capture. */
    private fun bind(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector,
        settings: VideoMessageSettings,
        capture: VideoCapture<Recorder>
    ) {
        val supportedFps = cameraProvider.getCameraInfo(selector).supportedFrameRateRanges
        // The preview follows the recording range instead of re-deciding from the raw request:
        // a preview asking for 60 next to a 30 FPS recording used to be possible here.
        val chosenFps = previewFrameRateFor(
            captureFpsRange?.let { it.lower..it.upper },
            supportedFps.map { it.lower..it.upper }
        )
        previewFpsRange = chosenFps
        val fpsRange = chosenFps?.let { Range(it.first, it.last) }
        val previewBuilder = Preview.Builder()
        previewBuilder.setMirrorMode(mirrorMode(settings))
        if (fpsRange != null) previewBuilder.setTargetFrameRate(fpsRange)
        val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).setScaleType(ViewPort.FILL_CENTER).build())
            .build()
        val bound = cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
        camera = bound
        if (settings.diagnosticsEnabled) {
            // Query AFTER binding: requested Quality and the 1:1 ViewPort do not tell us
            // which stream dimensions/crops CameraX actually negotiated on this device.
            previewGeometry = videoNoteUseCaseGeometrySummary(useCaseGeometry(preview.resolutionInfo))
            videoGeometry = videoNoteUseCaseGeometrySummary(useCaseGeometry(capture.resolutionInfo))
            take?.let { recordBoundGeometry(it.sessionName) } // Camera switch during an existing take.
        } else {
            previewGeometry = null
            videoGeometry = null
        }
        lightMode = videoNoteLightMode(selector == CameraSelector.DEFAULT_FRONT_CAMERA,
            runCatching { bound.cameraInfo.hasFlashUnit() }.getOrDefault(false))
        lightEnabled = false
        applyZoom(bound, zoomRatio)
    }

    private fun useCaseGeometry(info: ResolutionInfo?): VideoNoteUseCaseGeometry? {
        info ?: return null
        val bounds = info.cropRect
        val size = info.resolution
        return VideoNoteUseCaseGeometry(
            size.width, size.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
            info.rotationDegrees
        )
    }

    /** These are CameraX output-stream crops; they do not establish the optical sensor FOV. */
    private fun recordBoundGeometry(sessionName: String) {
        val unknown = context.getString(R.string.vn_entry_usecase_geometry_unknown)
        VideoNoteDiagnostics.append(sessionName,
            context.getString(R.string.vn_entry_usecase_geometry, "Preview", previewGeometry ?: unknown))
        VideoNoteDiagnostics.append(sessionName,
            context.getString(R.string.vn_entry_usecase_geometry, "VideoCapture", videoGeometry ?: unknown))
    }

    private fun unbindCamera() {
        clearLight(resetMode = true)
        provider?.unbindAll()
        camera = null
    }

    /** Диапазон зума берётся у конкретной камеры: ультраширокая и фронтальная имеют разные границы. */
    private fun applyZoom(bound: Camera, ratio: Float) {
        val range = bound.cameraInfo.zoomState.value
        // LiveData may still be empty after bind: never silently skip the initial 1x request.
        // We only know that the request was issued, not that Camera2 applied it. The sensor
        // capture results in diagnostics are the evidence for the actual zoom used.
        zoomRatio = videoNoteZoomTarget(ratio, range?.minZoomRatio, range?.maxZoomRatio)
        bound.cameraControl.setZoomRatio(zoomRatio)
    }

    /** Отсчёт идёт с момента, когда CameraX сообщил о старте записи, а не с нажатия: открытие камеры в минуту не входит. */
    private fun startClock() {
        startedAt = SystemClock.elapsedRealtime()
        timer?.cancel()
        timer = scope.launch {
            while (active) {
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                if (elapsedMs >= maxDurationMs) {
                    stop(preview = locked)
                    break
                }
                delay(100)
            }
        }
    }

    private val sensorTap = VideoNoteSensorTap()

    /** Reads sensor timestamps from the capture results of the real session; the numbers never leave the report. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun attachSensorTap(builder: VideoCapture.Builder<Recorder>) {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                if (!sensorTap.isActive()) return
                val crop = result.get(CaptureResult.SCALER_CROP_REGION)
                sensorTap.record(
                    result.get(CaptureResult.SENSOR_TIMESTAMP), result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                    crop?.let { "${it.left},${it.top} ${it.width()}x${it.height()}" },
                    if (android.os.Build.VERSION.SDK_INT >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null,
                    frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
                    targetFpsRange = result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)?.let { it.lower..it.upper },
                    aeState = result.get(CaptureResult.CONTROL_AE_STATE),
                    videoStabilizationMode = if (Build.VERSION.SDK_INT >= 33) {
                        result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)
                    } else null,
                    opticalStabilizationMode = result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
                )
            }
        })
    }

    private fun startRecording(settings: VideoMessageSettings) {
        val capture = videoCapture ?: return
        // RAW без маски пишется в no_backup: он не должен попадать в резервные копии и на другое устройство.
        // Замаскированные сегменты обработчик кладёт в filesDir/uploads, как и вложения: кэш система может очистить до отправки.
        val sessionName = UUID.randomUUID().toString()
        val rawDirectory = File(context.noBackupFilesDir, "uploads").resolve(sessionName).apply { mkdirs() }
        val current = VideoTake(rawDirectory.resolve("video-note.mp4"), settings, sessionName)
        take = current
        VideoNoteDiagnostics.begin(
            context = context,
            sessionId = sessionName,
            settings = settings,
            lens = context.getString(if (preferFrontCamera) R.string.camera_facing_front else R.string.camera_facing_back),
            decision = captureFpsDecision,
            previewRange = previewFpsRange,
            supportedRanges = camera?.cameraInfo?.supportedFrameRateRanges?.joinToString { "${it.lower}..${it.upper}" } ?: "?",
            captureBitrate = videoCameraBitrate(settings)
        )
        if (settings.diagnosticsEnabled) {
            VideoNoteDiagnostics.append(sessionName, context.getString(R.string.vn_entry_light,
                lightSourceLabel(), yesNoUnknown(context, lightEnabled)))
            VideoNoteDiagnostics.append(sessionName, context.getString(R.string.vn_entry_fov_viewport))
            VideoNoteDiagnostics.append(sessionName, context.getString(
                if (captureFourThreeFallback) R.string.vn_entry_fov_capture_aspect_fallback
                else if (capturePrefersFourThree) R.string.vn_entry_fov_capture_aspect_four_three
                else R.string.vn_entry_fov_capture_aspect_default
            ))
            recordBoundGeometry(sessionName)
            val (sourceWidth, sourceHeight) = captureSourceSize ?: VideoNoteCameraSurvey.sourceSize(settings)
            VideoNoteDiagnostics.append(sessionName, context.getString(R.string.vn_entry_source_plan,
                captureQuality?.name ?: "?", sourceWidth, sourceHeight))
            VideoNoteDiagnostics.append(sessionName, context.getString(R.string.vn_entry_camera, captureCameraId ?: "?", sourceWidth, sourceHeight))
            val manager = context.getSystemService(CameraManager::class.java)
            captureCameraId?.let { id ->
                VideoNoteDiagnostics.append(sessionName, VideoNoteCameraSurvey.describeCamera(context, manager, id).take(2600))
            }
            VideoNoteCameraSurvey.thermalLine(context)?.let { VideoNoteDiagnostics.append(sessionName, it) }
        }
        try {
            current.recording = prepare(current, persistent = persistentSupported)
            Log.i(TAG, "video_recording_started session=$sessionName persistent=$persistentSupported")
        } catch (failure: Exception) {
            // Persistent-запись нужна только для смены камеры во время записи: без неё кружок пишется как раньше.
            if (!persistentSupported) {
                Log.e(TAG, "video_recording_start_failed session=$sessionName")
                recordRecordingFailure("recording_start", failure)
                take = null
                current.file.parentFile?.deleteRecursively()
                fail(context.getString(R.string.recorder_start_failed))
                return
            }
            Log.i(TAG, "video_persistent_unavailable session=$sessionName")
            persistentSupported = false
            canSwitchCamera = false
            try {
                current.recording = prepare(current, persistent = false)
                Log.i(TAG, "video_recording_started session=$sessionName persistent=false")
            } catch (retryFailure: Exception) {
                Log.e(TAG, "video_recording_start_failed session=$sessionName")
                recordRecordingFailure("recording_start", retryFailure)
                take = null
                current.file.parentFile?.deleteRecursively()
                fail(context.getString(R.string.recorder_start_failed))
                return
            }
        }
    }

    @OptIn(ExperimentalPersistentRecording::class)
    private fun prepare(current: VideoTake, persistent: Boolean): Recording {
        val capture = videoCapture ?: error("VideoCapture не готов")
        val pending = capture.output.prepareRecording(context, FileOutputOptions.Builder(current.file).build())
        if (persistent) pending.asPersistentRecording()
        return pending.withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Start && take === current && active) {
                    started = true
                    startClock()
                    recordStarted()
                    if (current.settings.diagnosticsEnabled) sensorTap.begin()
                    VideoNoteDiagnostics.append(current.sessionName, context.getString(R.string.vn_entry_recording_started))
                }
                if (event is VideoRecordEvent.Finalize) onFinalize(current, event)
            }
    }

    private fun onFinalize(current: VideoTake, event: VideoRecordEvent.Finalize) {
        if (take === current) {
            take = null
            unbindCamera()
            // Внеплановый Finalize без stop(): гасим состояние записи, иначе интерфейс остаётся «идёт запись».
            active = false
            locked = false
            canceling = false
            started = false
            timer?.cancel()
            timer = null
        }
        val seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
        val sensor = sensorTap.finish(captureFpsRange?.upper ?: 30)
        if (current.settings.diagnosticsEnabled) {
            VideoNoteDiagnostics.recordCrop(current.sessionName, sensor?.crop, sensor?.zoom,
                sensor?.firstCrop, sensor?.firstZoom,
                sensor?.cropChanged == true || sensor?.zoomChanged == true)
            VideoNoteDiagnostics.recordSensor(current.sessionName, sensor?.stats, sensor?.truncated == true, sensor?.ae)
            VideoNoteCameraSurvey.thermalLine(context)?.let {
                VideoNoteDiagnostics.append(current.sessionName, it)
            }
        }
        Log.i(TAG, "video_recording_finalized session=${current.file.parentFile?.name} durationSeconds=$seconds error=${event.error} bytes=${current.file.length()}")
        val outcome = videoTakeOutcome(event.error, current.discard, seconds, current.stopRequested)
        VideoNoteDiagnostics.append(
            current.sessionName,
            context.getString(R.string.vn_entry_recording_finalized, seconds, current.file.length(), event.error),
            when (outcome) {
                VideoTakeOutcome.Send -> VideoNoteStatus.PROCESSING
                VideoTakeOutcome.Discarded, VideoTakeOutcome.TooShort -> VideoNoteStatus.DISCARDED
                VideoTakeOutcome.SaveFailed -> VideoNoteStatus.RECORDING_FAILED
            }
        )
        recordAppEvent(
            kind = EventKind.UI,
            code = "video_note_finalized",
            text = "Кружок: запись остановлена, $seconds с",
            severity = if (outcome == VideoTakeOutcome.SaveFailed) Severity.ERROR else Severity.INFO,
            attrs = mapOf("outcome" to outcome.name, "duration_s" to seconds.toString(), "codec_error" to event.error.toString())
        )
        when (outcome) {
            VideoTakeOutcome.Discarded -> current.file.parentFile?.deleteRecursively()
            VideoTakeOutcome.TooShort -> {
                current.file.parentFile?.deleteRecursively()
                onError(context.getString(R.string.recorder_too_short))
            }
            VideoTakeOutcome.SaveFailed -> {
                // Частичный файл после ошибки кодека или внезапного Finalize отправлять нельзя.
                current.file.parentFile?.deleteRecursively()
                onError(context.getString(R.string.recorder_save_failed))
            }
            VideoTakeOutcome.Send -> onTakeReady(current.file, current.settings, current.sessionName, current.previewFirst)
        }
    }
}

/** Итог завершённой записи. Отдельная политика, чтобы покрывать её юнит-тестами без CameraX. */
internal enum class VideoTakeOutcome { Send, Discarded, TooShort, SaveFailed }

/** Фронтальная камера без фонарика использует свет экрана; задняя без вспышки — недоступна. */
internal enum class VideoNoteLightMode { Unavailable, Torch, Screen }

internal fun videoNoteLightMode(front: Boolean, hasFlashUnit: Boolean): VideoNoteLightMode = when {
    hasFlashUnit -> VideoNoteLightMode.Torch
    front -> VideoNoteLightMode.Screen
    else -> VideoNoteLightMode.Unavailable
}

/**
 * Ошибка важнее отмены: даже отменённая запись с ошибкой кодека не должна отличаться от сломанной.
 * Отправка разрешена только после явного [stopRequested]: внеплановый Finalize без команды пользователя
 * не должен превращаться в отправку, даже если CameraX не сообщил об ошибке.
 */
internal fun videoTakeOutcome(error: Int, discard: Boolean, durationSeconds: Int, stopRequested: Boolean): VideoTakeOutcome = when {
    error != 0 -> VideoTakeOutcome.SaveFailed
    !stopRequested -> VideoTakeOutcome.SaveFailed
    discard -> VideoTakeOutcome.Discarded
    durationSeconds < MIN_DURATION_SECONDS -> VideoTakeOutcome.TooShort
    else -> VideoTakeOutcome.Send
}

/** Переключение камеры доступно только при работающей persistent-записи и наличии обеих камер. */
internal fun videoSwitchCameraAvailable(persistentSupported: Boolean, hasFrontCamera: Boolean, hasBackCamera: Boolean): Boolean =
    persistentSupported && hasFrontCamera && hasBackCamera

/** Видеобитрейт CameraX для пресета: единый источник для качества записи и расчёта места. */
internal fun videoCameraBitrate(settings: VideoMessageSettings): Int = when (settings.preset) {
    VideoQualityPreset.Standard -> 1_000_000
    VideoQualityPreset.High -> 1_400_000
    VideoQualityPreset.Maximum -> 4_000_000
    VideoQualityPreset.Custom -> settings.customVideoBitrateKbps * 1_000
}

/** Пиковый объём на диске: RAW и перекодированные сегменты живут одновременно. Аудио учтено надбавкой 128 кбит/с на каждый поток, сверху запас на контейнер. */
internal fun videoPeakStorageBytes(videoBitrate: Int, durationSeconds: Long): Long {
    val bits = (videoBitrate.toLong() + 128_000L) * durationSeconds
    return bits / 8 * 2 + 256L * 1024L * 1024L
}

/** Запись кружка на экране чата: отмена при уходе из композиции, чтобы камера не оставалась открытой. */
@Composable
internal fun rememberVideoNoteRecorder(
    settings: VideoMessageSettingsStore,
    processingScope: CoroutineScope,
    chatId: Long,
    topicId: Long = 0L,
    accountId: String = com.beykus.beygram.data.tdlib.TdAccountStorageLayout.LEGACY_ACCOUNT_ID,
    onError: (String) -> Unit
): VideoNoteRecorder {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentChatId by rememberUpdatedState(chatId)
    val currentTopicId by rememberUpdatedState(topicId)
    val currentAccountId by rememberUpdatedState(accountId)
    val currentOnError by rememberUpdatedState(onError)
    val recorder = remember(owner, settings, processingScope) {
        VideoNoteRecorder(context, owner, processingScope, { settings.settings.value },
            onTakeReady = { raw, takeSettings, sessionName, previewFirst ->
                VideoNoteExportCenter.enqueue(context.applicationContext, currentChatId, raw, takeSettings, sessionName, previewFirst, currentTopicId, currentAccountId)
            },
            onError = { currentOnError(it) })
    }
    DisposableEffect(recorder) { onDispose { recorder.dispose() } }
    return recorder
}

private fun mediaMetadata(file: File): Triple<Int, Int, Int>? = runCatching {
    MediaMetadataRetriever().run {
        try {
            setDataSource(file.absolutePath)
            val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            Triple(width, height, durationMs?.let { ((it + 999) / 1000).toInt() } ?: 0)
        } finally { release() }
    }
}.getOrNull()

/** Кнопка записи в композере: удержание включает запись, отпускание отправляет, сдвиг влево отменяет. */
@Composable
internal fun VideoNoteRecordButton(recorder: VideoNoteRecorder, enabled: Boolean, onPress: () -> Unit) {
    val palette = beyGramPalette()
    val recording = recorder.active
    val currentOnPress by rememberUpdatedState(onPress)
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (recording) palette.danger else palette.surface2)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val cancelPx = CANCEL_DRAG_DP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnPress()
                    var dragged = 0f
                    var finished = false
                    try {
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            dragged = change.position.x - down.position.x
                            recorder.onDrag(dragged < -cancelPx)
                            pressed = change.pressed
                        }
                        finished = true
                    } finally {
                        // Жест, прерванный извне (например, сменой enabled), отменяет запись, а не оставляет её до лимита.
                        recorder.release(cancel = !finished || dragged < -cancelPx)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Videocam, if (recording) stringResource(R.string.recorder_recording_description) else stringResource(R.string.recorder_start_description),
            tint = if (recording) Color.White else palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/** Переключатель камеры на экране записи: виден с открытия экрана, нажимается после фактического старта записи. */
@Composable
internal fun VideoNoteSwitchCameraButton(recorder: VideoNoteRecorder, enabled: Boolean, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Box(
        modifier.size(40.dp).clip(CircleShape).background(palette.surface2)
            .clickable(enabled = enabled, onClick = recorder::switchCamera),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Cameraswitch,
            stringResource(if (recorder.preferFrontCamera) R.string.recorder_switch_to_back_description else R.string.recorder_switch_to_front_description),
            tint = palette.textSecondary.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Кнопка подсветки доступна рядом с переключением камер, поверх квадрата вне обрезанного кругом превью. */
@Composable
internal fun VideoNoteLightButton(recorder: VideoNoteRecorder, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val available = recorder.lightMode != VideoNoteLightMode.Unavailable
    val enabled = recorder.active && recorder.started && available
    val label = when {
        !available -> R.string.recorder_light_unavailable
        recorder.lightMode == VideoNoteLightMode.Screen && recorder.lightEnabled -> R.string.recorder_screen_light_off
        recorder.lightMode == VideoNoteLightMode.Screen -> R.string.recorder_screen_light_on
        recorder.lightEnabled -> R.string.recorder_torch_off
        else -> R.string.recorder_torch_on
    }
    Box(
        modifier.size(48.dp).clip(CircleShape)
            .background(if (recorder.lightEnabled) Color(0xFFFFD54F) else palette.surface2)
            .clickable(enabled = enabled, onClick = recorder::toggleLight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (recorder.lightEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
            stringResource(label),
            tint = if (recorder.lightEnabled) Color.Black else palette.textSecondary.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** На фронтальной камере временно увеличиваем яркость только текущего окна, без изменения системной настройки. */
@Composable
private fun VideoNoteScreenBrightness(active: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val activity = remember(context) {
        var current: Context? = context
        var result: Activity? = null
        while (current != null) {
            if (current is Activity) {
                result = current
                break
            }
            current = (current as? ContextWrapper)?.baseContext
        }
        result
    }
    DisposableEffect(activity, owner, active) {
        val window = activity?.window
        val oldBrightness = if (active) window?.attributes?.screenBrightness else null
        var restored = false
        fun restore() {
            if (restored || oldBrightness == null || window == null) return
            restored = true
            val attrs = window.attributes
            attrs.screenBrightness = oldBrightness
            window.attributes = attrs
        }
        if (active && window != null) {
            val attrs = window.attributes
            attrs.screenBrightness = 1f
            window.attributes = attrs
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) restore()
        }
        if (active) owner.lifecycle.addObserver(observer)
        onDispose {
            if (active) owner.lifecycle.removeObserver(observer)
            restore()
        }
    }
}

/** Нижняя панель состояния видеосообщения с явной отменой после фиксации. */
@Composable
internal fun VideoNoteRecordingDock(recorder: VideoNoteRecorder, modifier: Modifier = Modifier, onCancel: () -> Unit) {
    val palette = beyGramPalette()
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface2).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.size(9.dp).clip(CircleShape).background(palette.danger))
        Icon(Icons.Rounded.Videocam, null, tint = palette.danger, modifier = Modifier.size(19.dp))
        Text(duration((recorder.elapsedMs / 1000).toInt()), color = palette.textPrimary, fontSize = 16.sp)
        Text(
            if (recorder.locked) "" else stringResource(R.string.recorder_swipe_left_hint),
            color = palette.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (recorder.locked) {
            androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.recorder_cancel), color = palette.warningText, fontSize = 13.sp)
            }
        }
    }
}

/** Экран записи вместо ленты: кадр в круге, кольцо прогресса к лимиту 60 секунд и подсказки. */
@Composable
internal fun VideoNoteRecordingOverlay(recorder: VideoNoteRecorder, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val screenLight = recorder.lightEnabled && recorder.lightMode == VideoNoteLightMode.Screen
    VideoNoteScreenBrightness(screenLight)
    Column(
        modifier.fillMaxSize().background(if (screenLight) Color.White else palette.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val progress = (recorder.elapsedMs.toFloat() / recorder.durationLimitMs).coerceIn(0f, 1f)
        // Жест масштабирования на кадре: щипок двумя пальцами меняет зум, пока камера привязана.
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(recorder) {
                detectTransformGestures { _, _, zoom, _ -> recorder.zoomBy(zoom) }
            },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val inset = Offset(stroke / 2, stroke / 2)
                drawArc(if (screenLight) Color.LightGray else palette.divider, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
                drawArc(palette.danger, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
            }
            Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(VideoNoteBackground), contentAlignment = Alignment.TopCenter) {
                // PreviewView переиспользуется между записями: снимаем его со старого контейнера, иначе addView упадёт.
                AndroidView(factory = { recorder.previewView.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
                if (recorder.zoomRatio != 1f) {
                    Text(
                        stringResource(R.string.recorder_zoom_value, recorder.zoomRatio),
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            // Обе кнопки вне clip(CircleShape), иначе углы квадрата отсекут hit target.
            VideoNoteLightButton(recorder, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
            if (recorder.canSwitchCamera) {
                VideoNoteSwitchCameraButton(
                    recorder,
                    enabled = recorder.started,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!recorder.locked) {
            Text(if (recorder.canceling) stringResource(R.string.recorder_release_to_cancel) else stringResource(R.string.recorder_release_to_send), color = if (screenLight) Color.Black else palette.textPrimary, fontSize = 14.sp)
            Text(stringResource(R.string.recorder_swipe_up_lock), color = if (screenLight) Color.DarkGray else palette.textSecondary, fontSize = 14.sp)
            Text(stringResource(R.string.recorder_swipe_left_cancel), color = if (recorder.canceling) palette.danger else if (screenLight) Color.DarkGray else palette.textSecondary, fontSize = 14.sp)
        }
    }
}

/** Request a wider square field of view for HD while preserving known >=50 FPS combinations.
 * Unknown FPS stays on CameraX defaults; no claim about physical optics is made. */
internal fun videoNotePreferFourThreeCapture(quality: VideoNoteSourceQuality, selectedFrameRate: IntRange?): Boolean =
    quality == VideoNoteSourceQuality.HD && selectedFrameRate != null &&
        selectedFrameRate.last <= 30

/** Only retry incompatible camera configurations. Permission and runtime failures
 * must propagate instead of being misdiagnosed as an unsupported aspect ratio. */
internal fun videoNoteShouldRetryDefaultAspect(preferredFourThree: Boolean, failure: Throwable): Boolean =
    preferredFourThree && failure is IllegalArgumentException

/** Only use the advertised zoom bounds once ZoomState is ready. The first 1x request
 * must still be sent before LiveData publishes its initial value. */
internal fun videoNoteZoomTarget(requested: Float, minimum: Float?, maximum: Float?): Float {
    val fallback = 1f
    val value = if (requested.isFinite()) requested else fallback
    // ZoomState is asynchronous. Do not invent an upper limit of 1× before it arrives.
    if (minimum == null || maximum == null || !minimum.isFinite() ||
        !maximum.isFinite() || minimum <= 0f || maximum < minimum) return value.coerceAtLeast(1f)
    return value.coerceIn(minimum, maximum)
}
