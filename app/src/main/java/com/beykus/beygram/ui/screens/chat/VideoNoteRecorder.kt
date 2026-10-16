package com.beykus.beygram.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Rational
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val MAX_DURATION_MS = 60_000L
private const val MIN_DURATION_SECONDS = 1
private const val SIDE_PX = 480
/** Сдвиг пальца влево, после которого отпускание отменяет запись. */
private const val CANCEL_DRAG_DP = 96f

internal val videoNotePermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

internal fun videoNoteAllowed(context: Context): Boolean =
    videoNotePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Одна запись: пишется или уже останавливается и ждёт финализации CameraX. */
private class VideoTake(val file: File) {
    var discard = false
    var recording: Recording? = null
}

/** Запись кружка через CameraX: квадратный кадр по центру (ViewPort 1:1). Отпускание отправляет, сдвиг влево отменяет. */
internal class VideoNoteRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val onFinished: (OutgoingMedia) -> Unit,
    private val onError: (String) -> Unit
) {
    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    var active by mutableStateOf(false)
        private set
    var canceling by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var take: VideoTake? = null
    private var timer: Job? = null
    private var startedAt = 0L
    private var attempt = 0

    /** Нажатие: камера открывается асинхронно, запись начинается, когда она готова. */
    fun press() {
        if (active || take != null) return
        active = true
        canceling = false
        elapsedMs = 0L
        startedAt = SystemClock.elapsedRealtime()
        val current = ++attempt
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // Ответ камеры от более раннего нажатия игнорируется, чтобы не начать вторую запись.
            if (!active || current != attempt) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                bind(cameraProvider)
                startRecording()
            } catch (_: Exception) {
                fail("Не удалось открыть камеру")
            }
        }, ContextCompat.getMainExecutor(context))
        timer = scope.launch {
            while (active) {
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
                if (elapsedMs >= MAX_DURATION_MS) {
                    release(cancel = false)
                    break
                }
                delay(100)
            }
        }
    }

    /** Палец ушёл влево за порог: при отпускании запись будет отменена. */
    fun onDrag(cancelNow: Boolean) {
        canceling = cancelNow
    }

    /** Отпускание: [cancel] отменяет запись, иначе кружок уходит в отправку после финализации. */
    fun release(cancel: Boolean) {
        if (!active) return
        active = false
        timer?.cancel()
        timer = null
        val current = take
        if (current == null) {
            provider?.unbindAll()
            return
        }
        current.discard = current.discard || cancel
        current.recording?.stop()
    }

    fun dispose() {
        active = false
        timer?.cancel()
        timer = null
        val current = take
        if (current == null) {
            provider?.unbindAll()
        } else {
            current.discard = true
            current.recording?.stop()
        }
    }

    private fun fail(message: String) {
        active = false
        timer?.cancel()
        timer = null
        if (take == null) provider?.unbindAll()
        onError(message)
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        cameraProvider.unbindAll()
        val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).setScaleType(ViewPort.FILL_CENTER).build())
            .build()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        // Файл лежит в uploads, как и вложения: кэш система может очистить до отправки, а поставленное в очередь сообщение тогда сломается.
        val directory = File(context.filesDir, "uploads").resolve(UUID.randomUUID().toString()).apply { mkdirs() }
        val current = VideoTake(directory.resolve("video-note.mp4"))
        take = current
        try {
            current.recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(current.file).build())
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) onFinalize(current, event)
                }
        } catch (_: Exception) {
            take = null
            current.file.parentFile?.deleteRecursively()
            fail("Не удалось начать запись")
        }
    }

    private fun onFinalize(current: VideoTake, event: VideoRecordEvent.Finalize) {
        if (take === current) {
            take = null
            provider?.unbindAll()
        }
        val seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
        if (current.discard || seconds < MIN_DURATION_SECONDS || event.hasError()) {
            current.file.parentFile?.deleteRecursively()
            if (!current.discard && seconds >= MIN_DURATION_SECONDS) onError("Не удалось сохранить кружок")
            return
        }
        onFinished(
            OutgoingMedia(
                path = current.file.absolutePath, fileName = "Видеосообщение.mp4", mimeType = "video/mp4",
                kind = MediaKind.VideoNote, width = SIDE_PX, height = SIDE_PX, durationSeconds = seconds
            )
        )
    }
}

/** Запись кружка на экране чата: отмена при уходе из композиции, чтобы камера не оставалась открытой. */
@Composable
internal fun rememberVideoNoteRecorder(onFinished: (OutgoingMedia) -> Unit, onError: (String) -> Unit): VideoNoteRecorder {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnError by rememberUpdatedState(onError)
    val recorder = remember(owner) { VideoNoteRecorder(context, owner, scope, { currentOnFinished(it) }, { currentOnError(it) }) }
    DisposableEffect(recorder) { onDispose { recorder.dispose() } }
    return recorder
}

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
        Icon(Icons.Rounded.Videocam, if (recording) "Идёт запись кружка" else "Записать кружок",
            tint = if (recording) Color.White else palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/** Строка записи вместо поля ввода: иконка удаления и подсказка про отмену сдвигом влево. */
@Composable
internal fun VideoNoteRecordingHint(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Row(modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Delete, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text("‹ Сдвиньте влево для отмены", color = palette.textSecondary, fontSize = 14.sp, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(palette.surface2).padding(horizontal = 16.dp, vertical = 9.dp))
    }
}

/** Экран записи вместо ленты: кадр в круге, кольцо прогресса к лимиту 60 секунд и подсказки. */
@Composable
internal fun VideoNoteRecordingOverlay(recorder: VideoNoteRecorder, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Column(
        modifier.fillMaxSize().background(palette.screen).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val progress = (recorder.elapsedMs.toFloat() / MAX_DURATION_MS).coerceIn(0f, 1f)
        Box(Modifier.size(228.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val inset = Offset(stroke / 2, stroke / 2)
                drawArc(palette.divider, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
                drawArc(palette.danger, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
            }
            Box(Modifier.size(220.dp).clip(CircleShape).background(VideoNoteBackground), contentAlignment = Alignment.TopCenter) {
                // PreviewView переиспользуется между записями: снимаем его со старого контейнера, иначе addView упадёт.
                AndroidView(factory = { recorder.previewView.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
                Row(
                    Modifier.padding(top = 10.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(palette.danger, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(duration((recorder.elapsedMs / 1000).toInt()), color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (recorder.canceling) "Отпустите, чтобы отменить." else "Отпустите, чтобы отправить.", color = palette.textPrimary, fontSize = 14.sp)
        Text("Влево — отменить", color = if (recorder.canceling) palette.danger else palette.textSecondary, fontSize = 14.sp)
    }
}
