package com.beykus.beygram.ui.screens.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private val IDLE_SIZE = 220.dp
private val PLAYING_SIZE = 292.dp
internal val VideoNoteBackground = Brush.linearGradient(listOf(Color(0xFF2B2640), Color(0xFF17151D)))

/** Голосовое и кружок играют прямо в ленте, поэтому тап по пузырю для них не открывает просмотрщик. */
internal fun MediaKind.playsInline(): Boolean = this == MediaKind.Voice || this == MediaKind.VideoNote

/** Общий фокус воспроизведения: в ленте играет одно голосовое или кружок, новый запуск останавливает предыдущий. */
internal object InlinePlaybackFocus {
    private var owner: Any? = null
    private var stopOwner: (() -> Unit)? = null

    fun claim(newOwner: Any, stop: () -> Unit) {
        val previousOwner = owner
        val previousStop = stopOwner
        owner = newOwner
        stopOwner = stop
        if (previousOwner !== newOwner) previousStop?.invoke()
    }

    fun release(oldOwner: Any) {
        if (owner === oldOwner) {
            owner = null
            stopOwner = null
        }
    }

    fun stopAll() {
        val previousStop = stopOwner
        owner = null
        stopOwner = null
        previousStop?.invoke()
    }
}

/** Состояние одного кружка: плеер и поверхность живут, пока кружок играет или стоит на паузе. */
private class VideoNoteHolder {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    var started by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    fun start(path: String, onStarted: () -> Unit) {
        InlinePlaybackFocus.claim(this) { stop() }
        releasePlayer()
        val created = MediaPlayer()
        player = created
        started = true
        playing = false
        positionMs = 0
        durationMs = 0
        try {
            created.setDataSource(path)
            surface?.let { created.setSurface(it) }
            created.setOnPreparedListener { mp ->
                durationMs = mp.duration.coerceAtLeast(1)
                mp.start()
                playing = true
                onStarted()
            }
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, _, _ -> stop(); true }
            created.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        playing = false
    }

    fun resume() {
        InlinePlaybackFocus.claim(this) { stop() }
        runCatching { player?.start() }
        playing = player != null
    }

    fun tick() {
        if (playing) positionMs = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    }

    fun stop() {
        releasePlayer()
        started = false
        playing = false
        positionMs = 0
        InlinePlaybackFocus.release(this)
    }

    fun attach(texture: Surface) {
        surface?.release()
        surface = texture
        player?.let { runCatching { it.setSurface(texture) } }
    }

    fun detach() {
        surface?.release()
        surface = null
        stop()
    }

    fun dispose() {
        stop()
        surface?.release()
        surface = null
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** Кружок в ленте: круг 220 dp с отметкой непросмотренного; при проигрывании растёт и показывает кольцо прогресса. */
@Composable
internal fun VideoNoteBubble(
    media: MessageMedia,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onPlayed: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    time: String = "",
    statusIcon: ImageVector? = null,
    unseen: Boolean = false
) {
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val holder = remember(media.fileId) { VideoNoteHolder() }
    var wantPlay by remember(media.fileId) { mutableStateOf(false) }
    var played by remember(media.fileId) { mutableStateOf(false) }
    val path = file.localPath
    val currentOnPlayed by rememberUpdatedState(onPlayed)
    val size by animateDpAsState(targetValue = if (holder.started) PLAYING_SIZE else IDLE_SIZE, label = "video note size")
    DisposableEffect(holder) { onDispose { holder.dispose() } }
    val hostContext = LocalContext.current
    // Секретный кружок во время проигрывания не должен попасть на скриншот или запись экрана.
    val secureWhilePlaying = media.isSecret && holder.started
    DisposableEffect(secureWhilePlaying) {
        val window = hostContext.findActivity()?.window
        if (secureWhilePlaying) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secureWhilePlaying) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // Миниатюра показывается до старта; у секретного кружка её не грузим, иначе кадр виден до просмотра.
    val thumbnailId = media.thumbnailFileId?.takeUnless { media.isSecret }
    val thumbnail by remember(thumbnailId) { observeFile(thumbnailId ?: 0) }.collectAsStateWithLifecycle()
    LaunchedEffect(thumbnailId) { if (thumbnailId != null) downloadFile(thumbnailId) }
    // Нажатие до окончания загрузки запоминается: как только файл готов, кружок начинает играть.
    LaunchedEffect(path, wantPlay) {
        if (wantPlay && path != null) {
            wantPlay = false
            holder.start(path) { played = true; currentOnPlayed() }
        }
    }
    // Ошибка загрузки снимает ожидание: спиннер не крутится вечно, повтор — новым нажатием.
    LaunchedEffect(file.error) { if (file.error != null) wantPlay = false }
    LaunchedEffect(holder.playing) {
        while (holder.playing) {
            holder.tick()
            delay(120)
        }
    }
    fun togglePlay() {
        val local = path
        when {
            local == null -> { wantPlay = true; downloadFile(media.fileId) }
            !holder.started -> holder.start(local) { played = true; currentOnPlayed() }
            holder.playing -> holder.pause()
            else -> holder.resume()
        }
    }
    val fraction = if (holder.durationMs > 0) (holder.positionMs.toFloat() / holder.durationMs).coerceIn(0f, 1f) else 0f
    // Во время проигрывания показывается оставшееся время, как в оригинальном клиенте.
    val shownSeconds = if (holder.started && holder.durationMs > 0) (holder.durationMs - holder.positionMs + 500) / 1000 else media.durationSeconds
    Box(Modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(VideoNoteBackground)
                .combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailPath = thumbnail.localPath
            if (!holder.started && thumbnailPath != null) LocalMediaImage(thumbnailPath, Modifier.fillMaxSize())
            if (holder.started) AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = holder.attach(Surface(texture))
                            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { holder.detach(); return true }
                            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (holder.started) Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val inset = Offset(stroke / 2, stroke / 2)
                drawArc(palette.textSecondary.copy(alpha = 0.3f), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
                drawArc(palette.accent, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = inset, size = arcSize, style = Stroke(stroke))
            }
            if (holder.started) Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(if (holder.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (path == null && (wantPlay || file.isDownloading)) CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
            if (path == null && file.error != null && !wantPlay) Text(stringResource(R.string.video_note_download_failed), color = Color.White, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            if (!holder.started) Icon(Icons.Rounded.VolumeOff, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(6.dp))
            Text(duration(shownSeconds), color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
            if (time.isNotBlank()) Row(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(time, color = Color.White, fontSize = 12.sp)
                if (statusIcon != null) {
                    Spacer(Modifier.width(3.dp))
                    Icon(statusIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (unseen && !played) Box(Modifier.align(Alignment.CenterEnd).size(10.dp).background(palette.accent, CircleShape))
    }
}
