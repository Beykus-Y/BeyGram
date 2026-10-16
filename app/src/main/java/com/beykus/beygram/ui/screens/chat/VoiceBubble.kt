package com.beykus.beygram.ui.screens.chat

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val BAR_COUNT = 40

/** Состояние общего проигрывателя голосовых: в каждый момент играет не больше одного сообщения. */
internal data class VoiceState(
    val path: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val failed: Boolean = false
)

/** Один MediaPlayer на процесс: запуск другого голосового останавливает текущее. */
internal object VoicePlayback {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var progress: Job? = null
    private var pendingFraction = 0f

    /** Пауза или продолжение того же файла; другой файл начинается сначала. */
    fun toggle(path: String, onStarted: () -> Unit) {
        val current = _state.value
        val active = player
        if (current.path == path && active != null && current.durationMs > 0) {
            if (current.playing) pause(active) else resume(active, onStarted)
        } else {
            start(path, 0f, onStarted)
        }
    }

    /** Перемотка по доле длины; если файл ещё не выбран, он запускается с этой точки. */
    fun seek(path: String, fraction: Float, onStarted: () -> Unit) {
        val current = _state.value
        val active = player
        if (current.path == path && active != null && current.durationMs > 0) {
            val target = (fraction * current.durationMs).toInt()
            runCatching { active.seekTo(target) }
            _state.update { it.copy(positionMs = target) }
            if (!current.playing) resume(active, onStarted)
        } else {
            start(path, fraction, onStarted)
        }
    }

    fun stop() {
        progress?.cancel()
        progress = null
        runCatching { player?.release() }
        player = null
        _state.value = VoiceState()
    }

    private fun pause(active: MediaPlayer) {
        runCatching { active.pause() }
        _state.update { it.copy(playing = false) }
    }

    private fun resume(active: MediaPlayer, onStarted: () -> Unit) {
        runCatching { active.start() }
        _state.update { it.copy(playing = true) }
        onStarted()
    }

    private fun start(path: String, fraction: Float, onStarted: () -> Unit) {
        stop()
        pendingFraction = fraction
        _state.value = VoiceState(path = path)
        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            created.setDataSource(path)
            created.setOnPreparedListener { mp ->
                val total = mp.duration.coerceAtLeast(1)
                val startMs = (pendingFraction * total).toInt()
                if (startMs > 0) mp.seekTo(startMs)
                _state.update { it.copy(durationMs = total, positionMs = startMs) }
                startProgress()
                resume(mp, onStarted)
            }
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, _, _ ->
                stop()
                _state.value = VoiceState(path = path, failed = true)
                true
            }
            created.prepareAsync()
        } catch (_: Exception) {
            stop()
            _state.value = VoiceState(path = path, failed = true)
        }
    }

    private fun startProgress() {
        progress?.cancel()
        progress = scope.launch {
            while (true) {
                val active = player ?: break
                val position = runCatching { active.currentPosition }.getOrDefault(0)
                _state.update { if (it.playing) it.copy(positionMs = position) else it }
                delay(200)
            }
        }
    }
}

/** Пузырь голосового: кнопка, волна с перемоткой по нажатию и время. Звук идёт через [VoicePlayback]. */
@Composable
internal fun VoiceBubble(media: MessageMedia, file: MediaFileState, downloadFile: (Int) -> Unit, onPlayed: () -> Unit, onLongPress: (() -> Unit)?) {
    val palette = beyGramPalette()
    val voice by VoicePlayback.state.collectAsStateWithLifecycle()
    val path = file.localPath
    var wantPlay by remember(media.fileId) { mutableStateOf(false) }
    val mine = path != null && voice.path == path
    val playing = mine && voice.playing
    val loading = (mine && voice.durationMs == 0 && !voice.failed) || (wantPlay && path == null)
    val fraction = if (mine && voice.durationMs > 0) (voice.positionMs.toFloat() / voice.durationMs).coerceIn(0f, 1f) else 0f
    val shownSeconds = if (mine && voice.positionMs > 0) voice.positionMs / 1000 else media.durationSeconds
    val bars = remember(media.fileId, media.waveform) { resampleWaveform(media.waveform, BAR_COUNT) }
    fun togglePlay() {
        if (path == null) { wantPlay = true; downloadFile(media.fileId) } else VoicePlayback.toggle(path, onPlayed)
    }
    // Нажатие до окончания загрузки запоминается: как только файл готов, он начинает играть.
    LaunchedEffect(path, wantPlay) {
        if (wantPlay && path != null) {
            wantPlay = false
            VoicePlayback.toggle(path, onPlayed)
        }
    }
    // Промежутки между кнопкой и волной тоже запускают воспроизведение, а не открывают просмотрщик вложения.
    Row(
        Modifier.width(244.dp).padding(vertical = 4.dp).combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(palette.accentSoft)
                .combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress),
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = palette.accentInk, strokeWidth = 2.dp)
            else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Пауза" else "Воспроизвести", tint = palette.accentInk, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth().height(28.dp).pointerInput(media.fileId, path) {
                    detectTapGestures(
                        onTap = { offset -> if (path == null) togglePlay() else VoicePlayback.seek(path, (offset.x / size.width).coerceIn(0f, 1f), onPlayed) },
                        onLongPress = { onLongPress?.invoke() }
                    )
                }
            ) {
                val step = size.width / bars.size
                val barWidth = step * 0.55f
                bars.forEachIndexed { index, sample ->
                    val height = (sample / 31f).coerceIn(0.15f, 1f) * size.height
                    drawRoundRect(
                        color = if ((index + 0.5f) / bars.size <= fraction) palette.accent else palette.textSecondary.copy(alpha = 0.4f),
                        topLeft = Offset(index * step + (step - barWidth) / 2f, (size.height - height) / 2f),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }
            }
            Text(
                duration(shownSeconds), color = palette.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp).combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress)
            )
        }
    }
}

/** Сводит волну к нескольким столбцам: в каждом берётся максимум отсчётов его участка. */
internal fun resampleWaveform(samples: List<Int>, count: Int): List<Int> {
    if (samples.isEmpty()) return List(count) { 8 }
    return List(count) { index ->
        val from = index * samples.size / count
        val until = maxOf(from + 1, (index + 1) * samples.size / count).coerceAtMost(samples.size)
        samples.subList(from, until).maxOrNull() ?: 0
    }
}
