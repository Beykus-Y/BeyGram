package com.beykus.beygram.ui.screens.chat

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

/**
 * Floating draggable in-app Picture-in-Picture video overlay. Lives in the root window above chats so the user
 * can keep watching a video while browsing other conversations.
 */
@Composable
internal fun InAppVideoPipHost() {
    val request by InAppVideoPip.current.collectAsStateWithLifecycle()
    val active = request ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val pipWidthDp = 188.dp
    val pipHeightDp = 112.dp

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
            // Center play/pause tap target
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
            // Top-end close button
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
