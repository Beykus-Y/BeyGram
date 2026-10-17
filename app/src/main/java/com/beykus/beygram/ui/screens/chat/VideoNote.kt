package com.beykus.beygram.ui.screens.chat

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private val IDLE_SIZE = 220.dp
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
    statusDescription: String? = null,
    unseen: Boolean = false
) {
    val file by remember(media.fileId) { observeFile(media.fileId) }.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val holder = remember(media.fileId) { VideoNoteHolder() }
    var wantPlay by remember(media.fileId) { mutableStateOf(false) }
    var played by remember(media.fileId) { mutableStateOf(false) }
    val path = file.localPath
    val currentOnPlayed by rememberUpdatedState(onPlayed)
    val playingSize = LocalConfiguration.current.screenWidthDp.dp
    var showControls by remember(media.fileId) { mutableStateOf(false) }
    LaunchedEffect(holder.started, holder.playing) {
        showControls = holder.started
        delay(500)
        showControls = false
    }
    val size by animateDpAsState(targetValue = if (holder.started) playingSize else IDLE_SIZE, label = "video note size")
    DisposableEffect(holder) { onDispose { holder.dispose() } }
    // Нажатие до окончания загрузки запоминается: как только файл готов, кружок начинает играть.
    LaunchedEffect(path, wantPlay) {
        if (wantPlay && path != null) {
            wantPlay = false
            holder.start(path) { played = true; currentOnPlayed() }
        }
    }
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
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.size(size)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(VideoNoteBackground)
                    .combinedClickable(onClick = ::togglePlay, onLongClick = onLongPress),
                contentAlignment = Alignment.Center
            ) {
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
                if (holder.started && showControls) Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Icon(if (holder.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                if (path == null && (wantPlay || file.isDownloading)) CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
                if (!holder.started) Icon(Icons.Rounded.VolumeOff, contentDescription = null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(6.dp))


            }
            if (unseen && !played) Box(Modifier.align(Alignment.CenterEnd).size(10.dp).background(palette.accent, CircleShape))
        }
        Row(
            Modifier.padding(top = 4.dp).background(palette.surface, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(duration(shownSeconds), color = palette.textSecondary, fontSize = 12.sp)
            if (time.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(time, color = palette.textSecondary, fontSize = 12.sp)
            }
            if (statusIcon != null) {
                Spacer(Modifier.width(3.dp))
                Icon(statusIcon, contentDescription = statusDescription, tint = palette.accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}
