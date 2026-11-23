package com.beykus.beygram.ui.screens.chat

import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

internal data class InAppVideoPipRequest(
    val path: String,
    val startPositionMs: Long = 0L
)

/** Process-local controller for the draggable in-app mini video window. */
internal object InAppVideoPip {
    private val _current = MutableStateFlow<InAppVideoPipRequest?>(null)
    val current: StateFlow<InAppVideoPipRequest?> = _current.asStateFlow()

    fun open(path: String, positionMs: Long = 0L) {
        if (path.isBlank() || !File(path).isFile) return
        _current.value = InAppVideoPipRequest(path, positionMs.coerceAtLeast(0L))
    }

    fun close() {
        _current.value = null
    }
}

/** Tracks whether an inline [VideoNoteBubble] currently owns the video surface inside a visible chat. */
internal object VideoNoteInlineSurfaceRegistry {
    private val _owners = MutableStateFlow<Set<Any>>(emptySet())
    val owners: StateFlow<Set<Any>> = _owners.asStateFlow()

    fun register(owner: Any) {
        _owners.value = _owners.value + owner
    }

    fun unregister(owner: Any) {
        _owners.value = _owners.value - owner
    }
}

/**
 * Floating draggable in-app Picture-in-Picture overlay for both fullscreen videos ([InAppVideoPip])
 * and off-screen playing video notes (кружки) from [PlaybackEngine].
 */
@Composable
internal fun InAppVideoPipHost() {
    val request by InAppVideoPip.current.collectAsStateWithLifecycle()
    if (request != null) {
        FloatingVideoPip(request!!)
        return
    }
    val playback by PlaybackEngine.state.collectAsStateWithLifecycle()
    val inlineOwners by VideoNoteInlineSurfaceRegistry.owners.collectAsStateWithLifecycle()
    val item = playback.item
    val showVideoNotePip = item != null &&
        item.kind == MediaKind.VideoNote &&
        !playback.ended &&
        !playback.failed &&
        inlineOwners.isEmpty()
    if (showVideoNotePip) {
        FloatingVideoNotePip(playback)
    }
}

@Composable
private fun FloatingVideoPip(active: InAppVideoPipRequest) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pipWidthDp = 196.dp
    val pipHeightDp = 116.dp

    val player = remember(active.path) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(active.path))))
            prepare()
            if (active.startPositionMs > 0L) seekTo(active.startPositionMs)
            playWhenReady = true
        }
    }
    var playing by remember(active.path) { mutableStateOf(true) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val marginPx = with(density) { 12.dp.toPx() }
        val widthPx = with(density) { pipWidthDp.toPx() }
        val heightPx = with(density) { pipHeightDp.toPx() }
        val maxX = (constraints.maxWidth - widthPx - marginPx).coerceAtLeast(marginPx)
        val maxY = (constraints.maxHeight - heightPx - with(density) { 96.dp.toPx() }).coerceAtLeast(marginPx)
        var offsetX by remember(active.path) { mutableFloatStateOf(maxX) }
        var offsetY by remember(active.path) { mutableFloatStateOf(with(density) { 72.dp.toPx() }.coerceIn(marginPx, maxY)) }

        val shape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(pipWidthDp, pipHeightDp)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, maxY)
                    }
                }
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(role = Role.Button) {
                        if (playing) {
                            player.pause()
                            playing = false
                        } else {
                            player.play()
                            playing = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.common_pause else R.string.common_play),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(role = Role.Button) { InAppVideoPip.close() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.viewer_pip_close),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingVideoNotePip(playback: PlaybackState) {
    val palette = beyGramPalette()
    val density = LocalDensity.current
    val circleSizeDp = 136.dp
    val pipOwner = remember { Any() }
    val itemKey = playback.item?.key ?: return

    DisposableEffect(pipOwner) {
        onDispose { PlaybackEngine.detachVideo(pipOwner) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val marginPx = with(density) { 14.dp.toPx() }
        val sizePx = with(density) { circleSizeDp.toPx() }
        val maxX = (constraints.maxWidth - sizePx - marginPx).coerceAtLeast(marginPx)
        val maxY = (constraints.maxHeight - sizePx - with(density) { 104.dp.toPx() }).coerceAtLeast(marginPx)
        var offsetX by remember(itemKey) { mutableFloatStateOf(maxX) }
        var offsetY by remember(itemKey) { mutableFloatStateOf(with(density) { 88.dp.toPx() }.coerceIn(marginPx, maxY)) }

        Box(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(circleSizeDp)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, maxY)
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(VideoNoteBackground)
                    .clickable(role = Role.Button) {
                        if (playback.playing) PlaybackEngine.pause() else PlaybackEngine.resume(itemKey) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { TextureView(it).apply { setOpaque(false) } },
                    update = { PlaybackEngine.attachVideo(pipOwner, it) },
                    onRelease = { PlaybackEngine.detachVideo(pipOwner) },
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = palette.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * playback.fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                if (!playback.playing) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.common_play),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(role = Role.Button) { PlaybackEngine.stop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.viewer_pip_close),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
