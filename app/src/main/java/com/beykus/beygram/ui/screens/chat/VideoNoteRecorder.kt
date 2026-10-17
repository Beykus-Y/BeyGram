package com.beykus.beygram.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.StatFs
import android.os.SystemClock
import android.util.Range
import android.util.Rational
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Delete
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.data.media.VideoMessageSettings
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.VideoQualityPreset
import com.beykus.beygram.data.media.requestedFrameRateRange
import com.beykus.beygram.data.media.chooseSupportedFrameRateRange
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val NORMAL_MAX_DURATION_MS = 60_000L
private const val LONG_MAX_DURATION_MS = 3_600_000L
private const val MIN_DURATION_SECONDS = 1
private const val SIDE_PX = 480
/** Сдвиг пальца влево, после которого отпускание отменяет запись. */
private const val CANCEL_DRAG_DP = 96f
private const val TAG = "BeyGramRecorder"
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

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    /** Диапазон FPS, зашитый в текущий VideoCapture на этапе создания: пересоздать его без обрыва записи нельзя. */
    private var captureFpsRange: Range<Int>? = null
    /** Persistent-запись не отвязывается от VideoCapture при перепривязке камеры. Если устройство её не умеет, переключение скрывается. */
    private var persistentSupported = true
    private var take: VideoTake? = null
    private var timer: Job? = null
    private var startedAt = 0L
    private var maxDurationMs = NORMAL_MAX_DURATION_MS
    private var attempt = 0

    init {
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

    /** Смена камеры только во время записи: один Recorder и VideoCapture живут всю запись, при переключении перепривязывается та же пара. */
    fun switchCamera() {
        val cameraProvider = provider ?: return
        val capture = videoCapture ?: return
        val current = take ?: return
        if (!active || !started || !canSwitchCamera || !persistentSupported) return
        val previousSelector = if (preferFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        // hasCamera/getCameraInfo умеют бросаться, поэтому весь этап capability-проверок тоже внутри try.
        var unbound = false
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
                    onError(context.getString(R.string.recorder_camera_switch_failed))
                    return
                }
            }
            Log.i(TAG, "video_camera_switch_started front=$targetFront")
            cameraProvider.unbindAll()
            unbound = true
            bind(cameraProvider, selector, current.settings, capture)
            preferFrontCamera = targetFront
            Log.i(TAG, "video_camera_switched front=$preferFrontCamera")
        } catch (failure: Exception) {
            Log.e(TAG, "video_camera_switch_failed", failure)
            if (!unbound) {
                // Камера ещё привязана: сбоила только проверка, запись продолжается без изменений.
                onError(context.getString(R.string.recorder_camera_switch_failed))
                return
            }
            val restored = runCatching { bind(cameraProvider, previousSelector, current.settings, capture) }
            if (restored.isSuccess) {
                // Запись продолжается с прежней камерой: persistent-запись пережила отвязку, кадр возвращён.
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
        elapsedMs = 0L
        val current = ++attempt
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
                if (canSwitchCamera) preferFrontCamera = startSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                val capture = createCapture(cameraProvider, startSelector, settings)
                videoCapture = capture
                bind(cameraProvider, startSelector, settings, capture)
                startRecording(settings)
            } catch (failure: Exception) {
                Log.e(TAG, "video_camera_open_failed")
                recordRecordingFailure("camera_open", failure)
                fail(context.getString(R.string.recorder_camera_open_failed))
            }
        }, ContextCompat.getMainExecutor(context))
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
            provider?.unbindAll()
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
        if (active) stop(preview = true)
        locked = false
        timer?.cancel()
        timer = null
        val current = take
        if (current == null) {
            provider?.unbindAll()
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
        active = false
        locked = false
        canceling = false
        started = false
        timer?.cancel()
        timer = null
        if (take == null) provider?.unbindAll()
        onError(message)
    }

    private fun stop(preview: Boolean) {
        if (!active) return
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
        current.stopRequested = true
        current.recording?.stop()
    }

    /** Пара Recorder и VideoCapture создаётся один раз на запись: persistent-запись требует, чтобы при смене камеры она не пересоздавалась. */
    private fun createCapture(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector,
        settings: VideoMessageSettings
    ): VideoCapture<Recorder> {
        val side = when (settings.preset) {
            VideoQualityPreset.Standard -> 384
            VideoQualityPreset.High -> 480
            VideoQualityPreset.Maximum -> 640
            VideoQualityPreset.Custom -> settings.customSide
        }
        val bitrate = videoCameraBitrate(settings)
        val quality = if (side > 480) Quality.HD else Quality.SD
        val requestedFps = settings.requestedFrameRateRange()
        val supportedFps = cameraProvider.getCameraInfo(selector).supportedFrameRateRanges
        val chosenFps = chooseSupportedFrameRateRange(
            requestedFps,
            supportedFps.map { it.lower..it.upper }
        )
        val fpsRange = chosenFps?.let { Range(it.first, it.last) }
        // An exact 24–60 / 35–60 / 55–60 AE range is often unavailable.
        // Use a camera-advertised range; CameraX still makes the final session decision.
        Log.i(TAG, "video_fps requested=$requestedFps selected=$chosenFps available=$supportedFps")
        val qualitySelector = QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
        val captureBuilder = VideoCapture.Builder(
            Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setTargetVideoEncodingBitRate(bitrate)
                .build()
        )
        if (fpsRange != null) captureBuilder.setTargetFrameRate(fpsRange)
        captureFpsRange = fpsRange
        return captureBuilder.build()
    }

    /** Привязывает превью и уже созданный capture к выбранной камере. При переключении вызывается повторно с тем же capture. */
    private fun bind(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector,
        settings: VideoMessageSettings,
        capture: VideoCapture<Recorder>
    ) {
        val requestedFps = settings.requestedFrameRateRange()
        val supportedFps = cameraProvider.getCameraInfo(selector).supportedFrameRateRanges
        val chosenFps = chooseSupportedFrameRateRange(
            requestedFps,
            supportedFps.map { it.lower..it.upper }
        )
        val fpsRange = chosenFps?.let { Range(it.first, it.last) }
        val previewBuilder = Preview.Builder()
        if (fpsRange != null) previewBuilder.setTargetFrameRate(fpsRange)
        val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).setScaleType(ViewPort.FILL_CENTER).build())
            .build()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
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

    private fun startRecording(settings: VideoMessageSettings) {
        val capture = videoCapture ?: return
        // RAW без маски пишется в no_backup: он не должен попадать в резервные копии и на другое устройство.
        // Замаскированные сегменты обработчик кладёт в filesDir/uploads, как и вложения: кэш система может очистить до отправки.
        val sessionName = UUID.randomUUID().toString()
        val rawDirectory = File(context.noBackupFilesDir, "uploads").resolve(sessionName).apply { mkdirs() }
        val current = VideoTake(rawDirectory.resolve("video-note.mp4"), settings, sessionName)
        take = current
        try {
            current.recording = prepare(current, persistent = persistentSupported)
            Log.i(TAG, "video_recording_started session=$sessionName persistent=$persistentSupported")
            recordStarted()
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
                recordStarted()
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
                }
                if (event is VideoRecordEvent.Finalize) onFinalize(current, event)
            }
    }

    private fun onFinalize(current: VideoTake, event: VideoRecordEvent.Finalize) {
        if (take === current) {
            take = null
            provider?.unbindAll()
            // Внеплановый Finalize без stop(): гасим состояние записи, иначе интерфейс остаётся «идёт запись».
            active = false
            locked = false
            canceling = false
            started = false
            timer?.cancel()
            timer = null
        }
        val seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
        Log.i(TAG, "video_recording_finalized session=${current.file.parentFile?.name} durationSeconds=$seconds error=${event.error} bytes=${current.file.length()}")
        val outcome = videoTakeOutcome(event.error, current.discard, seconds, current.stopRequested)
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
    onError: (String) -> Unit
): VideoNoteRecorder {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentChatId by rememberUpdatedState(chatId)
    val currentOnError by rememberUpdatedState(onError)
    val recorder = remember(owner, settings, processingScope) {
        VideoNoteRecorder(context, owner, processingScope, { settings.settings.value },
            onTakeReady = { raw, takeSettings, sessionName, previewFirst ->
                VideoNoteExportCenter.enqueue(context.applicationContext, currentChatId, raw, takeSettings, sessionName, previewFirst)
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

/** Нижняя панель состояния видеосообщения с явной отменой после фиксации. */
@Composable
internal fun VideoNoteRecordingDock(recorder: VideoNoteRecorder, modifier: Modifier = Modifier, onCancel: () -> Unit) {
    val palette = beyGramPalette()
    Row(modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(palette.surface2).padding(horizontal = 12.dp),
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
            Text(stringResource(R.string.recorder_cancel), color = palette.warningText, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 7.dp))
        }
    }
}

/** Экран записи вместо ленты: кадр в круге, кольцо прогресса к лимиту 60 секунд и подсказки. */
@Composable
internal fun VideoNoteRecordingOverlay(recorder: VideoNoteRecorder, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Column(
        modifier.fillMaxSize().background(palette.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val progress = (recorder.elapsedMs.toFloat() / recorder.durationLimitMs).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val inset = Offset(stroke / 2, stroke / 2)
                drawArc(palette.divider, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
                drawArc(palette.danger, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
            }
            Box(Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(VideoNoteBackground), contentAlignment = Alignment.TopCenter) {
                // PreviewView переиспользуется между записями: снимаем его со старого контейнера, иначе addView упадёт.
                AndroidView(factory = { recorder.previewView.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
            }
            // Кнопка живёт вне контейнера с clip(CircleShape): в углу квадрата она оказалась бы за вписанной окружностью и обрезалась.
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
            Text(if (recorder.canceling) stringResource(R.string.recorder_release_to_cancel) else stringResource(R.string.recorder_release_to_send), color = palette.textPrimary, fontSize = 14.sp)
            Text(stringResource(R.string.recorder_swipe_up_lock), color = palette.textSecondary, fontSize = 14.sp)
            Text(stringResource(R.string.recorder_swipe_left_cancel), color = if (recorder.canceling) palette.danger else palette.textSecondary, fontSize = 14.sp)
        }
    }
}
