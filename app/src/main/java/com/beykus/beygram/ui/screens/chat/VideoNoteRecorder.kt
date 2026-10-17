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
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.data.media.VideoMessageSettings
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.media.VideoQualityPreset
import com.beykus.beygram.data.media.VideoNoteProcessor
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
/** Кадр обрезается по кругу при обработке, поэтому запись кружков включена. */
internal const val VIDEO_NOTE_RECORDING_ENABLED = true

internal val videoNotePermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

internal fun videoNoteAllowed(context: Context): Boolean =
    videoNotePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Одна запись: пишется или уже останавливается и ждёт финализации CameraX. */
private class VideoTake(val file: File, val settings: VideoMessageSettings) {
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
    private val onFinished: (List<OutgoingMedia>, previewFirst: Boolean) -> Unit,
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
    /** Выбор камеры сохраняется между записями, пока жив этот рекордер (экран чата). Фронтальная — по умолчанию. */
    var preferFrontCamera by mutableStateOf(true)
        private set
    /** Переключатель показывается, только если на устройстве есть и фронтальная, и основная камера. */
    var canSwitchCamera by mutableStateOf(false)
        private set

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
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

    /** Смена камеры только до начала записи: CameraX не переключает камеру без обрыва текущего кадра. */
    fun switchCamera() {
        if (active) return
        preferFrontCamera = !preferFrontCamera
        Log.i(TAG, "video_camera_switched front=$preferFrontCamera")
    }

    /** Нажатие: камера открывается асинхронно, запись начинается, когда она готова. */
    fun press() {
        if (active || take != null) return
        val settings = currentSettings()
        Log.i(TAG, "video_start_requested preset=${settings.preset} long=${settings.longRecordingEnabled}")
        maxDurationMs = if (settings.longRecordingEnabled) LONG_MAX_DURATION_MS else NORMAL_MAX_DURATION_MS
        val requiredBytes = if (settings.longRecordingEnabled) 2_500L * 1024L * 1024L else 150L * 1024L * 1024L
        if (StatFs(context.filesDir.absolutePath).availableBytes < requiredBytes) {
            onError(context.getString(R.string.recorder_storage_low))
            return
        }
        active = true
        locked = false
        canceling = false
        elapsedMs = 0L
        val current = ++attempt
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Ответ камеры от более раннего нажатия игнорируется, чтобы не начать вторую запись.
            if (!active || current != attempt) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                bind(cameraProvider, settings)
                startRecording(settings)
            } catch (_: Exception) {
                Log.e(TAG, "video_camera_open_failed")
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

    private fun fail(message: String) {
        active = false
        locked = false
        canceling = false
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

    private fun bind(cameraProvider: ProcessCameraProvider, settings: VideoMessageSettings) {
        cameraProvider.unbindAll()
        val useFront = preferFrontCamera && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val side = when (settings.preset) {
            VideoQualityPreset.Standard -> 384
            VideoQualityPreset.High -> 480
            VideoQualityPreset.Maximum -> 640
            VideoQualityPreset.Custom -> settings.customSide
        }
        val bitrate = when (settings.preset) {
            VideoQualityPreset.Standard -> 1_000_000
            VideoQualityPreset.High -> 1_400_000
            VideoQualityPreset.Maximum -> 4_000_000
            VideoQualityPreset.Custom -> settings.customVideoBitrateKbps * 1_000
        }
        val quality = if (side > 480) Quality.HD else Quality.SD
        val requestedFps = settings.requestedFrameRateRange()
        val supportedFps = cameraProvider.getCameraInfo(selector).supportedFrameRateRanges
        val chosenFps = chooseSupportedFrameRateRange(
            requestedFps,
            supportedFps.map { it.lower..it.upper }
        )
        // An exact 24–60 / 35–60 / 55–60 AE range is often unavailable.
        // Use a camera-advertised range; CameraX still makes the final session decision.
        Log.i(TAG, "video_fps requested=$requestedFps selected=$chosenFps available=$supportedFps")
        val fpsRange = chosenFps?.let { Range(it.first, it.last) }
        val qualitySelector = QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
        val previewBuilder = Preview.Builder()
        if (fpsRange != null) previewBuilder.setTargetFrameRate(fpsRange)
        val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .setTargetVideoEncodingBitRate(bitrate)
            .build()
        val captureBuilder = VideoCapture.Builder(recorder)
        if (fpsRange != null) captureBuilder.setTargetFrameRate(fpsRange)
        val capture = captureBuilder.build()
        videoCapture = capture
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
        // Файл лежит в uploads, как и вложения: кэш система может очистить до отправки, а поставленное в очередь сообщение тогда сломается.
        val directory = File(context.filesDir, "uploads").resolve(UUID.randomUUID().toString()).apply { mkdirs() }
        val current = VideoTake(directory.resolve("video-note.mp4"), settings)
        take = current
        try {
            current.recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(current.file).build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Start && take === current && active) startClock()
                    if (event is VideoRecordEvent.Finalize) onFinalize(current, event)
                }
            Log.i(TAG, "video_recording_started session=${directory.name}")
        } catch (_: Exception) {
            Log.e(TAG, "video_recording_start_failed session=${directory.name}")
            take = null
            current.file.parentFile?.deleteRecursively()
            fail(context.getString(R.string.recorder_start_failed))
        }
    }

    private fun onFinalize(current: VideoTake, event: VideoRecordEvent.Finalize) {
        if (take === current) {
            take = null
            provider?.unbindAll()
        }
        val seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
        Log.i(TAG, "video_recording_finalized session=${current.file.parentFile?.name} durationSeconds=$seconds error=${event.error} bytes=${current.file.length()}")
        if (current.discard || seconds < MIN_DURATION_SECONDS) {
            current.file.parentFile?.deleteRecursively()
            if (!current.discard) {
                val message = if (seconds < MIN_DURATION_SECONDS) R.string.recorder_too_short else R.string.recorder_save_failed
                onError(context.getString(message))
            }
            return
        }
        scope.launch {
            try {
                Log.i(TAG, "video_processing_started session=${current.file.parentFile?.name}")
                val media = VideoNoteProcessor(context).process(current.file, current.settings)
                Log.i(TAG, "video_processing_completed session=${current.file.parentFile?.name} segments=${media.size} bytes=${media.sumOf { it.size }}")
                onFinished(media, current.previewFirst || media.size > 1)
            } catch (failure: Exception) {
                Log.e(TAG, "video_processing_failed session=${current.file.parentFile?.name} type=${failure.javaClass.simpleName}", failure)
                onError(context.getString(R.string.recorder_save_failed))
                // The source remains in its private session directory so a failed conversion never destroys it.
            }
        }
    }
}

/** Запись кружка на экране чата: отмена при уходе из композиции, чтобы камера не оставалась открытой. */
@Composable
internal fun rememberVideoNoteRecorder(
    settings: VideoMessageSettingsStore,
    processingScope: CoroutineScope,
    onFinished: (List<OutgoingMedia>, previewFirst: Boolean) -> Unit,
    onError: (String) -> Unit
): VideoNoteRecorder {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnError by rememberUpdatedState(onError)
    val recorder = remember(owner, settings, processingScope) { VideoNoteRecorder(context, owner, processingScope, { settings.settings.value }, { media, preview -> currentOnFinished(media, preview) }, { currentOnError(it) }) }
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

/** Переключатель камеры у кнопки записи: виден только до начала записи, поэтому во время съёмки его нет. */
@Composable
internal fun VideoNoteSwitchCameraButton(recorder: VideoNoteRecorder, enabled: Boolean) {
    val palette = beyGramPalette()
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(palette.surface2)
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
        }
        Spacer(Modifier.height(16.dp))
        if (!recorder.locked) {
            Text(if (recorder.canceling) stringResource(R.string.recorder_release_to_cancel) else stringResource(R.string.recorder_release_to_send), color = palette.textPrimary, fontSize = 14.sp)
            Text(stringResource(R.string.recorder_swipe_up_lock), color = palette.textSecondary, fontSize = 14.sp)
            Text(stringResource(R.string.recorder_swipe_left_cancel), color = if (recorder.canceling) palette.danger else palette.textSecondary, fontSize = 14.sp)
        }
    }
}
