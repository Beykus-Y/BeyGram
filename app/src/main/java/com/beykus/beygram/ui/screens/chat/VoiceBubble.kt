package com.beykus.beygram.ui.screens.chat

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.playbackItem
import com.beykus.beygram.playback.playbackKey
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.PlaybackSpeedMenu

private const val BAR_COUNT = 40

/** Пузырь голосового: кнопка, волна с перемоткой по нажатию и время. Звук идёт через общий плеер. */
@Composable
internal fun VoiceBubble(
    media: MessageMedia,
    file: MediaFileState,
    downloadFile: (Int) -> Unit,
    onPlayed: () -> Unit,
    onLongPress: (() -> Unit)?,
    origin: PlaybackOrigin? = null,
    transcriptionRequestPending: Boolean = false,
    transcriptionRequestError: String? = null,
    transcriptionRated: Boolean = false,
    onTranscribe: (() -> Unit)? = null,
    onRateTranscription: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val path = file.localPath
    val label = media.kind.label(context)
    val key = media.playbackKey()
    val playbackState = PlaybackEngine.state.collectAsStateWithLifecycle()
    // Общий плеер читает только пузырь, который играет: у остальных значение null, и на каждом шаге прогресса они не перерисовываются.
    val voice = remember(key) { derivedStateOf { playbackState.value.takeIf { it.item?.key == key } } }.value
    // Строка пузыря в общем ожидании загрузки: ждёт только последнее нажатое, даже если такой же файл стоит в другом сообщении.
    val row = remember(media.fileId) { Any() }
    val waitingForVoice = pendingPlaybackRow.value === row
    // Отметку «прослушано» получает только пузырь, по которому нажали, и только когда плеер сообщил о старте.
    var played by remember(media.fileId) { mutableStateOf(false) }
    val playing = voice?.playing == true
    val loading = (voice != null && voice.durationMs == 0L && !voice.failed) || (waitingForVoice && path == null)
    val fraction = voice?.fraction ?: 0f
    val shownSeconds = if (voice != null && voice.positionMs > 0L) (voice.positionMs / 1000).toInt() else media.durationSeconds
    val bars = remember(media.fileId, media.waveform) { resampleWaveform(media.waveform, BAR_COUNT) }
    val currentOnPlayed by rememberUpdatedState(onPlayed)
    // Голосовое считается прослушанным, когда плеер сообщил, что оно играет. Плеер вызывает отметку сам, поэтому пузырь может уйти с экрана раньше.
    val markPlayed: () -> Unit = { if (!played) { played = true; currentOnPlayed() } }
    // Новый запуск заменяет файл в общем плеере, в том числе кружок в ленте.
    fun startVoice(localPath: String) {
        val state = PlaybackEngine.state.value
        val starting = !(state.item?.key == key && state.playing)
        PlaybackEngine.toggle(context, media.playbackItem(localPath, label, origin), onStarted = if (starting) markPlayed else null)
    }
    // Перемотка по волне: в текущем файле двигает позицию, другой файл начинает с этой точки.
    fun seekVoice(localPath: String, target: Float) {
        val state = PlaybackEngine.state.value
        if (state.item?.key == key) {
            PlaybackEngine.seekTo(target)
            if (!state.playing) {
                PlaybackEngine.resume(key, markPlayed)
            }
        } else {
            PlaybackEngine.play(context, media.playbackItem(localPath, label, origin), target, markPlayed)
        }
    }
    fun togglePlay() {
        if (path == null) { pendingPlaybackRow.value = row; downloadFile(media.fileId) } else startVoice(path)
    }
    // Нажатие до окончания загрузки запоминается: как только файл готов, он начинает играть.
    LaunchedEffect(path, waitingForVoice) {
        if (waitingForVoice && path != null) {
            pendingPlaybackRow.value = null
            startVoice(path)
        }
    }    // Ошибка загрузки снимает ожидание: пузырь предлагает повтор, а не крутит спиннер. Повторное нажатие начинает загрузку заново.
    LaunchedEffect(file.error) { pendingPlaybackRow.value = voicePendingAfterError(pendingPlaybackRow.value, row, file.error != null) }
    DisposableEffect(row) {
        onDispose { if (pendingPlaybackRow.value === row) pendingPlaybackRow.value = null }
    }
    val playbackStateLabel = when {
        loading -> stringResource(R.string.a11y_state_loading)
        playing -> stringResource(R.string.a11y_state_playing)
        else -> stringResource(R.string.a11y_state_paused)
    }
    val voiceDescription = stringResource(R.string.a11y_voice_message, duration(shownSeconds))
    // Промежутки между кнопкой и волной тоже запускают воспроизведение, а не открывают просмотрщик вложения.
    Column(Modifier.width(244.dp)) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(palette.accentSoft)
                    .combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress)
                    .semantics { contentDescription = voiceDescription; stateDescription = playbackStateLabel },
                contentAlignment = Alignment.Center
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = palette.accentInk, strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier.fillMaxWidth().height(28.dp).pointerInput(media.fileId, path) {
                            detectTapGestures(
                                onTap = { offset -> if (path == null) togglePlay() else seekVoice(path, (offset.x / size.width).coerceIn(0f, 1f)) },
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
                }
                val failed = voiceDownloadFailed(path, file.error != null, waitingForVoice)
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (failed) stringResource(R.string.voice_download_failed) else duration(shownSeconds),
                        color = if (failed) palette.danger else palette.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.weight(1f).then(if (failed) Modifier.errorAnnouncement() else Modifier)
                    )
                    PlaybackSpeedMenu(Modifier.padding(start = 4.dp))
                }
            }
        }
        // The result and controls live inside the same bubble, directly under the waveform.
        TranscriptionSection(
            media = media,
            requestPending = transcriptionRequestPending,
            requestError = transcriptionRequestError,
            rated = transcriptionRated,
            onRequest = onTranscribe,
            onRate = onRateTranscription,
            modifier = Modifier.fillMaxWidth()
        )
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

/** Файла нет, загрузка не идёт и никто её не ждёт: пузырь предлагает повторить. */
internal fun voiceDownloadFailed(path: String?, hasError: Boolean, waiting: Boolean): Boolean = path == null && hasError && !waiting

/** Ошибка загрузки снимает ожидание только у пузыря, который нажал: чужое ожидание остаётся. */
internal fun voicePendingAfterError(pending: Any?, row: Any, hasError: Boolean): Any? = if (hasError && pending === row) null else pending
