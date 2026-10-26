package com.beykus.beygram.ui.screens.chat

import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.media.AutoDownloadNetworkStatus
import com.beykus.beygram.data.settings.AutoDownloadSettingsStore
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.SpeechRecognitionFailure
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.playbackItem
import com.beykus.beygram.playback.playbackKey
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.PlaybackSpeedMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.DateFormat
import java.util.Date

private val IDLE_SIZE = 220.dp
internal val VideoNoteBackground = Brush.linearGradient(listOf(Color(0xFF2B2640), Color(0xFF17151D)))
/** Состояние для кружка без миниатюры: не наблюдаем фиктивный файл с id 0, которого в хранилище нет. */
private val NoThumbnailState = MutableStateFlow(MediaFileState(0)).asStateFlow()

/** Голосовое и кружок играют прямо в ленте, поэтому тап по пузырю для них не открывает просмотрщик. */
internal fun MediaKind.playsInline(): Boolean = this == MediaKind.Voice || this == MediaKind.VideoNote


/** Кольцо прогресса поверх кружка: серая дорожка и дуга, которая заполняется до [fraction]. */
@Composable
private fun ProgressRing(fraction: Float) {
    val palette = beyGramPalette()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx()
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val inset = Offset(stroke / 2, stroke / 2)
        drawArc(palette.textSecondary.copy(alpha = 0.3f), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
        drawArc(palette.accent, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
    }
}

/** Автоматический кружок до приёма TDLib: кадр-заглушка с индикатором обработки или отправки. Его заменяет сообщение в ленте. */
@Composable
internal fun PendingVideoNoteBubble(pending: PendingVideoNote) {
    val palette = beyGramPalette()
    // Время фиксируется при появлении кружка и не меняется, пока он переходит от обработки к отправке.
    val time = remember(pending.sessionName) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.errorAnnouncement()) {
            Box(Modifier.size(IDLE_SIZE).clip(CircleShape).background(VideoNoteBackground), contentAlignment = Alignment.Center) {
                pending.previewPath?.let { LocalMediaImage(it, Modifier.fillMaxSize()) }
                Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
                    if (pending.processing) Text(stringResource(R.string.chat_video_note_processing), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(
                Modifier.padding(top = 4.dp).background(palette.surface, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(time, color = palette.messageTime, fontSize = 12.sp)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.chat_sending_description), tint = palette.messageTime, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Кружок в ленте: круг 220 dp с отметкой непросмотренного; при проигрывании растёт и показывает кольцо прогресса. Звук идёт через общий плеер, поэтому кружок продолжает играть вне ленты. */
@Composable
internal fun VideoNoteBubble(
    media: MessageMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onPlayed: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    time: String = "",
    statusIcon: ImageVector? = null,
    statusDescription: String? = null,
    unseen: Boolean = false,
    highlighted: Boolean = false,
    origin: PlaybackOrigin? = null,
    transcriptionRequestPending: Boolean = false,
    transcriptionRequestError: SpeechRecognitionFailure? = null,
    transcriptionRated: Boolean = false,
    onTranscribe: (() -> Unit)? = null,
    onRateTranscription: ((Boolean) -> Unit)? = null,
    speechRecognitionUi: SpeechRecognitionUi = SpeechRecognitionUi(),
    /** Repository-backed automatic thumbnail download; explicit playback still uses [downloadFile]. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null
) {
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val hostContext = LocalContext.current
    val accountId = LocalPlaybackAccountId.current
    val policySettings by remember(hostContext, accountId) {
        AutoDownloadSettingsStore.getInstance(hostContext.applicationContext, accountId).settings
    }.collectAsStateWithLifecycle()
    val policyNetwork by AutoDownloadNetworkStatus.network.collectAsStateWithLifecycle()
    LaunchedEffect(hostContext) { AutoDownloadNetworkStatus.ensureStarted(hostContext) }
    val autoDownload = autoDownloadFile ?: LocalAutoDownloadFile.current
    val label = media.kind.label(hostContext)
    val key = media.playbackKey()
    val playbackState = PlaybackEngine.state.collectAsStateWithLifecycle()
    // Общий плеер читает только кружок, который играет: у остальных значение null, и на каждом шаге прогресса они не перерисовываются.
    val circle = remember(key) { derivedStateOf { playbackState.value.takeIf { it.item?.key == key } } }.value
    val started = circle?.started == true
    val playing = circle?.playing == true
    // Кружок показан, пока он начат и не доиграл до конца или не сломался.
    val showing = started && circle?.ended != true && circle?.failed != true
    val fraction = circle?.fraction ?: 0f
    // Строка пузыря в общем ожидании загрузки: ждёт только последнее нажатое, как голосовое.
    val row = remember(media.fileId) { Any() }
    val waitingForCircle = pendingPlaybackRow.value === row
    // Отметку «прослушано» получает только кружок, по которому нажали, и только когда плеер сообщил о старте. Плеер вызывает её сам, поэтому пузырь может уйти с экрана раньше.
    var played by remember(media.fileId) { mutableStateOf(false) }
    val path = file.localPath
    val currentOnPlayed by rememberUpdatedState(onPlayed)
    val markPlayed: () -> Unit = { if (!played) { played = true; currentOnPlayed() } }
    val playingSize = LocalConfiguration.current.screenWidthDp.dp
    var showControls by remember(media.fileId) { mutableStateOf(false) }
    LaunchedEffect(showing, playing) {
        showControls = showing
        delay(500)
        showControls = false
    }
    val size by animateDpAsState(targetValue = if (showing) playingSize else IDLE_SIZE, label = "video note size")
    // Секретный кружок во время проигрывания не должен попасть на скриншот или запись экрана.
    val secureWhilePlaying = media.isSecret && showing
    SecureWindowWhileVisible(secureWhilePlaying)
    // Секретный кружок не играет, когда пузырь ушёл из списка.
    DisposableEffect(key) {
        onDispose { if (media.isSecret && PlaybackEngine.state.value.item?.key == key) PlaybackEngine.stopSecret() }
    }
    // Миниатюра лежит под кадрами видео: пока кадра нет, например сразу после старта или возврата в ленту, виден не чёрный круг. У секретного кружка её не грузим.
    val thumbnailId = media.thumbnailFileId?.takeUnless { media.isSecret }
    val thumbnail by remember(thumbnailId) { thumbnailId?.let(observeFile) ?: NoThumbnailState }.collectAsStateWithLifecycle()
    LaunchedEffect(thumbnailId, thumbnail.size, media.isSecret, media.isSecretChat, policySettings, policyNetwork) {
        if (thumbnailId != null) {
            val request = AutoDownloadRequest(
                fileId = thumbnailId,
                kind = MediaKind.Photo,
                sizeBytes = thumbnail.size,
                isSecret = media.isSecret,
                isSecretChat = media.isSecretChat,
                hasSpoiler = false
            )
            if (autoDownload != null) autoDownload(request) else downloadFile(thumbnailId)
        }
    }
    // Запуск заменяет файл в общем плеере, а тот же кружок ставит на паузу или продолжает.
    fun startCircle(localPath: String) {
        val state = PlaybackEngine.state.value
        val starting = !(state.item?.key == key && state.playing)
        PlaybackEngine.toggle(hostContext, media.playbackItem(localPath, label, origin), onStarted = if (starting) markPlayed else null)
    }
    fun togglePlay() {
        if (path == null) { pendingPlaybackRow.value = row; downloadFile(media.fileId) } else startCircle(path)
    }
    // Нажатие до окончания загрузки запоминается: как только файл готов, кружок начинает играть. Ждёт только последнее нажатое.
    LaunchedEffect(path, waitingForCircle) {
        if (waitingForCircle && path != null) {
            pendingPlaybackRow.value = null
            startCircle(path)
        }
    }
    // Ошибка загрузки снимает ожидание: спиннер не крутится вечно, повтор — новым нажатием.
    LaunchedEffect(file.error) { if (file.error != null && pendingPlaybackRow.value === row) pendingPlaybackRow.value = null }
    DisposableEffect(row) {
        onDispose { if (pendingPlaybackRow.value === row) pendingPlaybackRow.value = null }
    }
    // Во время проигрывания показывается оставшееся время, как в оригинальном клиенте.
    val shownSeconds = if (circle != null && showing && circle.durationMs > 0L) ((circle.durationMs - circle.positionMs + 500L) / 1000L).toInt() else media.durationSeconds
    val playbackStateLabel = when {
        showing -> stringResource(if (playing) R.string.a11y_state_playing else R.string.a11y_state_paused)
        path == null && (waitingForCircle || file.isDownloading) -> stringResource(R.string.a11y_state_loading)
        else -> null
    }
    val circleDescription = stringResource(R.string.a11y_video_note_message, duration(shownSeconds))
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.size(size).then(if (highlighted) Modifier.border(2.dp, palette.accent, CircleShape) else Modifier)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(VideoNoteBackground)
                    .combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress)
                    .semantics {
                        contentDescription = circleDescription
                        playbackStateLabel?.let { stateDescription = it }
                    },
                contentAlignment = Alignment.Center
            ) {
                val thumbnailPath = thumbnail.localPath
                // Миниатюра ещё не скачана: вместо чёрного круга показываем размытое минипревью из TDLib, кроме секретных кружков.
                if (thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize())
                else if (!media.isSecret) media.minithumbnail?.let { MinithumbnailImage(it, Modifier.fillMaxSize()) }
                // Кадры выводятся в общий плеер, пока кружок показан. Звук от них не зависит: без экрана он продолжает играть.
                if (showing) AndroidView(
                    factory = { TextureView(it).apply { setOpaque(false) } },
                    update = { PlaybackEngine.attachVideo(row, it) },
                    onRelease = { PlaybackEngine.detachVideo(row) },
                    modifier = Modifier.fillMaxSize()
                )
                // Свой кружок во время загрузки на сервер: кольцо показывает прогресс, после приёма кружок принимает обычный вид.
                if (showing) ProgressRing(fraction)
                else if (file.isUploading) ProgressRing(file.uploadProgress)
                if (showing && showControls) Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                if (path == null && (waitingForCircle || file.isDownloading)) CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
                if (path == null && file.error != null && !waitingForCircle) Text(stringResource(R.string.video_note_download_failed), color = Color.White, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                if (!showing) Icon(Icons.Rounded.VolumeOff, contentDescription = null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(6.dp))
            }
            if (unseen && !played) Box(Modifier.align(Alignment.CenterEnd).size(10.dp).background(palette.accent, CircleShape))
        }
        Row(
            Modifier.padding(top = 4.dp).background(palette.surface, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(duration(shownSeconds), color = palette.textSecondary, fontSize = 12.sp)
            PlaybackSpeedMenu(Modifier.padding(start = 8.dp))
            if (time.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(time, color = palette.messageTime, fontSize = 12.sp)
            }
            if (statusIcon != null) {
                Spacer(Modifier.width(3.dp))
                // The sending clock takes the time colour and the read ticks keep the accent, as in BubbleMeta.
                Icon(statusIcon, contentDescription = statusDescription, tint = if (statusIcon == Icons.Rounded.Schedule) palette.messageTime else palette.accent, modifier = Modifier.size(14.dp))
            }
        }
        TranscriptionSection(
            media = media,
            requestPending = transcriptionRequestPending,
            requestError = transcriptionRequestError,
            rated = transcriptionRated,
            onRequest = onTranscribe,
            onRate = onRateTranscription,
            ui = speechRecognitionUi,
            // Keep the note's circle alignment: a full-width section would push an incoming note to the right.
            modifier = Modifier.width(size)
        )
    }
}
