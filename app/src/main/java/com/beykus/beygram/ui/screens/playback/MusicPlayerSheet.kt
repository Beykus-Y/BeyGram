package com.beykus.beygram.ui.screens.playback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.beykus.beygram.R
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette

private const val TRANSPORT_MOTION_MS = 150
private const val SEEK_STEP_MS = 5_000L
private val QUEUE_ROW_HEIGHT = 76.dp

private enum class PlayerPanel { Player, Queue }

/**
 * Large now-playing surface. The same composable owns the queue panel so the sheet is not recreated when the user
 * moves between the player and the queue, and the source title/artwork are resolved from the same account origin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicPlayerSheet(
    onDismiss: () -> Unit,
    onOpenEqualizer: (() -> Unit)? = null
) {
    val player = rememberMusicPlayerUi()
    LaunchedEffect(player == null) { if (player == null) onDismiss() }
    val current = player ?: return
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.Player) }
    val source = rememberPlaybackSourceLabel(current.item.origin)
    val palette = beyGramPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(enabled = panel == PlayerPanel.Queue) { panel = PlayerPanel.Player }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.textSecondary) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
        ) {
            when (panel) {
                PlayerPanel.Player -> {
                    PlayerHeader(
                        player = current,
                        source = source,
                        onCollapse = onDismiss,
                        onOpenSource = current.item.origin?.let { origin -> { PlaybackEngine.requestShowInChat(origin); onDismiss() } }
                    )
                    PlayerBody(
                        modifier = Modifier.weight(1f),
                        player = current,
                        onOpenQueue = { panel = PlayerPanel.Queue },
                        onOpenEqualizer = onOpenEqualizer,
                        onShowInChat = current.item.origin?.let { origin -> { PlaybackEngine.requestShowInChat(origin); onDismiss() } }
                    )
                }

                PlayerPanel.Queue -> QueuePanel(
                    source = source,
                    onBack = { panel = PlayerPanel.Player },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun PlayerHeader(
    player: MusicPlayerUi,
    source: PlaybackSourceLabel,
    onCollapse: () -> Unit,
    onOpenSource: (() -> Unit)?
) {
    val palette = beyGramPalette()
    var speedMenu by remember { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
    val sourceLabel = stringResource(R.string.player_source_from)
    val openSourceLabel = stringResource(R.string.media_show_in_chat)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.focusRequester(closeFocus)) {
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_collapse), tint = palette.textSecondary)
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onOpenSource != null) Modifier.clickable(role = Role.Button, onClickLabel = openSourceLabel) { onOpenSource() }
                    else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                sourceLabel,
                color = palette.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.1.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                source.text,
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Box {
            IconButton(onClick = { speedMenu = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.player_menu), tint = palette.textSecondary)
            }
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                Text(
                    stringResource(R.string.player_speed_title),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                    val selected = player.speed == speed
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.player_speed_option, speedLabel(speed)),
                                color = if (selected) palette.accent else palette.textPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            PlaybackEngine.setMusicSpeed(speed)
                            speedMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBody(
    player: MusicPlayerUi,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onShowInChat: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val reduced = systemReducedMotion()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        if (landscape) {
            val artwork = minOf(maxHeight * 0.74f, maxWidth * 0.42f, 360.dp)
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerArtwork(player, artwork, reduced)
                Spacer(Modifier.width(28.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PlayerDetails(
                        player = player,
                        reduced = reduced,
                        onOpenQueue = onOpenQueue,
                        onOpenEqualizer = onOpenEqualizer,
                        onShowInChat = onShowInChat
                    )
                }
            }
        } else {
            val artwork = minOf(maxWidth - 64.dp, maxHeight * 0.36f, 360.dp).coerceAtLeast(140.dp)
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerArtwork(player, artwork, reduced)
                    Spacer(Modifier.height(18.dp))
                    PlayerDetails(
                        player = player,
                        reduced = reduced,
                        onOpenQueue = onOpenQueue,
                        onOpenEqualizer = onOpenEqualizer,
                        onShowInChat = onShowInChat
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    player: MusicPlayerUi,
    reduced: Boolean,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onShowInChat: (() -> Unit)?
) {
    TrackMetadata(player, reduced)
    PlayerSeekSection(player)
    Spacer(Modifier.height(6.dp))
    TransportControls(player, reduced)
    Spacer(Modifier.height(16.dp))
    PlayerActions(onOpenQueue, onOpenEqualizer, onShowInChat)
}

@Composable
private fun PlayerArtwork(player: MusicPlayerUi, size: Dp, reduced: Boolean) {
    val palette = beyGramPalette()
    val scale = remember(player.item.key) { Animatable(if (reduced) 1f else 0.94f) }
    LaunchedEffect(player.item.key, reduced) {
        if (reduced) scale.snapTo(1f)
        else scale.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .size(size)
            .shadow(6.dp, RoundedCornerShape(20.dp), clip = false)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    ) {
        TrackArtwork(
            artworkPath = player.item.artworkPath,
            audioPath = player.item.path.takeIf { it.isNotBlank() },
            modifier = Modifier.fillMaxSize(),
            background = palette.accentSoft,
            tint = palette.accentInk,
            symbolSize = size * 0.22f,
            cornerRadius = 20.dp,
            border = palette.divider.copy(alpha = 0.55f)
        )
    }
}

private data class TrackMetadataUi(
    val title: String,
    val artist: String,
    val album: String
)

@Composable
private fun TrackMetadata(player: MusicPlayerUi, reduced: Boolean) {
    val palette = beyGramPalette()
    val fallbackTitle = stringResource(R.string.media_kind_audio)
    val metadata = remember(
        player.item.key,
        player.item.trackTitle,
        player.item.title,
        player.item.performer,
        player.item.album,
        fallbackTitle
    ) {
        TrackMetadataUi(
            title = player.item.musicTitle().ifBlank { fallbackTitle },
            artist = player.item.musicArtist(),
            album = player.item.album.trim()
        )
    }
    Crossfade(
        targetState = metadata,
        animationSpec = tween(durationMillis = if (reduced) 0 else 220),
        label = "trackMetadata"
    ) { shown ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                shown.title,
                color = palette.textPrimary,
                fontSize = 23.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = shown.title }
            )
            if (shown.artist.isNotBlank()) {
                Text(
                    shown.artist,
                    color = palette.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (shown.album.isNotBlank() && shown.album != shown.title && shown.album != shown.artist) {
                Text(
                    shown.album,
                    color = palette.textSecondary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
    PlayerStatusLine(player)
}

@Composable
private fun PlayerStatusLine(player: MusicPlayerUi) {
    val palette = beyGramPalette()
    when {
        player.failed -> Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.player_failed), color = palette.danger, fontSize = 13.sp)
            TextButton(onClick = { PlaybackEngine.togglePlayPause() }) {
                Text(stringResource(R.string.player_retry))
            }
        }

        player.intentPlaying && player.buffering -> Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_buffering), color = palette.textSecondary, fontSize = 13.sp)
        }

        player.ended -> Text(
            stringResource(R.string.player_ended),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun PlayerSeekSection(player: MusicPlayerUi) {
    PlayerSeekBar(
        fallbackDurationSeconds = player.item.durationSeconds,
        contentLabel = stringResource(R.string.player_progress)
    )
    PlayerTimeRow(fallbackDurationSeconds = player.item.durationSeconds)
}

@Composable
private fun PlayerSeekBar(fallbackDurationSeconds: Int, contentLabel: String) {
    val palette = beyGramPalette()
    val position = rememberPlaybackPositionUi()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fallbackDurationMs = (fallbackDurationSeconds.coerceAtLeast(0) * 1000L)
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val enabled = duration > 0L
    val played = dragging ?: if (duration > 0L) (position.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffered = if (duration > 0L) position.bufferedFraction.coerceIn(0f, 1f) else 0f
    val stateText = stringResource(
        R.string.player_progress_a11y,
        playbackClock((played * duration).toLong()),
        playbackClock(duration)
    )
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { 10.dp.toPx() }
    fun seekTo(fraction: Float) {
        if (!enabled) return
        PlaybackEngine.seekTo(fraction.coerceIn(0f, 1f), fallbackDurationMs = fallbackDurationMs)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = contentLabel
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f, 0)
                if (enabled) {
                    setProgress { target ->
                        seekTo(target)
                        true
                    }
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -SEEK_STEP_MS
                    Key.DirectionRight -> SEEK_STEP_MS
                    else -> return@onKeyEvent false
                }
                val target = ((position.positionMs + delta).coerceAtLeast(0L))
                    .coerceAtMost(duration)
                    .toFloat() / duration
                seekTo(target)
                true
            }
            .pointerInput(enabled, duration) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val horizontalPadding = horizontalPaddingPx
                    val width = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
                    fun fractionAt(x: Float): Float = ((x - horizontalPadding) / width).coerceIn(0f, 1f)
                    dragging = fractionAt(down.position.x)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            dragging = fractionAt(change.position.x)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    val result = dragging
                    dragging = null
                    result?.let(::seekTo)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val padding = 10.dp.toPx()
            val trackWidth = (size.width - padding * 2f).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()
            val start = Offset(padding, centerY)
            fun barEnd(fraction: Float) = Offset(padding + trackWidth * fraction, centerY)
            val track = if (enabled) palette.divider else palette.divider.copy(alpha = 0.5f)
            drawLine(track, start, Offset(padding + trackWidth, centerY), strokeWidth = trackHeight, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (buffered > 0f) {
                drawLine(
                    palette.accent.copy(alpha = 0.32f),
                    start,
                    barEnd(buffered),
                    strokeWidth = trackHeight,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            if (played > 0f) {
                drawLine(
                    if (enabled) palette.accent else palette.textSecondary,
                    start,
                    barEnd(played),
                    strokeWidth = trackHeight,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            if (enabled) {
                drawCircle(
                    color = palette.accent,
                    radius = 7.dp.toPx(),
                    center = Offset(barEnd(played).x, centerY)
                )
                drawCircle(
                    color = palette.surface,
                    radius = 3.dp.toPx(),
                    center = Offset(barEnd(played).x, centerY)
                )
            }
        }
    }
}

@Composable
private fun PlayerTimeRow(fallbackDurationSeconds: Int) {
    val palette = beyGramPalette()
    val position = rememberPlaybackPositionUi()
    val fallbackDurationMs = (fallbackDurationSeconds.coerceAtLeast(0) * 1000L)
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackClock(position.positionMs), color = palette.textSecondary, fontSize = 12.sp)
            Text(playbackRemaining(position.positionMs, duration), color = palette.textSecondary, fontSize = 12.sp)
        }
        if (duration <= 0L) {
            Text(
                stringResource(R.string.player_seek_after_download),
                color = palette.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TransportControls(player: MusicPlayerUi, reduced: Boolean) {
    val shuffleState = stringResource(
        if (player.shuffleEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off
    )
    val repeatState = stringResource(
        when (player.repeatMode) {
            PlaybackRepeatMode.Off -> R.string.player_repeat_off
            PlaybackRepeatMode.All -> R.string.player_repeat_all
            PlaybackRepeatMode.One -> R.string.player_repeat_one
        }
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleTransportIcon(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(R.string.player_shuffle),
            stateText = shuffleState,
            selected = player.shuffleEnabled,
            enabled = player.queueSize > 1,
            onClick = { PlaybackEngine.setShuffle(!player.shuffleEnabled) }
        )
        TransportIcon(
            icon = Icons.Rounded.SkipPrevious,
            label = stringResource(R.string.player_previous),
            enabled = player.canPrevious,
            onClick = { PlaybackEngine.previous() }
        )
        PlayPauseButton(player, reduced)
        TransportIcon(
            icon = Icons.Rounded.SkipNext,
            label = stringResource(R.string.player_next),
            enabled = player.canNext,
            onClick = { PlaybackEngine.next() }
        )
        ToggleTransportIcon(
            icon = if (player.repeatMode == PlaybackRepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = stringResource(R.string.player_repeat),
            stateText = repeatState,
            selected = player.repeatMode != PlaybackRepeatMode.Off,
            enabled = true,
            onClick = { PlaybackEngine.cycleRepeat() }
        )
    }
}

@Composable
private fun TransportIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).alpha(if (enabled) 1f else 0.35f)
    ) {
        Icon(icon, label, tint = palette.textPrimary, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ToggleTransportIcon(
    icon: ImageVector,
    label: String,
    stateText: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .semantics { stateDescription = stateText }
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) palette.accentSoft else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = if (selected) palette.accentInk else palette.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PlayPauseButton(player: MusicPlayerUi, reduced: Boolean) {
    val palette = beyGramPalette()
    val icon = when {
        player.failed -> Icons.Rounded.Replay
        player.ended -> Icons.Rounded.Replay
        player.playing -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    val label = when {
        player.failed -> stringResource(R.string.player_retry)
        player.ended -> stringResource(R.string.player_replay)
        player.playing -> stringResource(R.string.common_pause)
        else -> stringResource(R.string.common_play)
    }
    val stateText = when {
        player.intentPlaying && player.buffering -> stringResource(R.string.player_buffering)
        player.playing -> stringResource(R.string.a11y_state_playing)
        else -> stringResource(R.string.a11y_state_paused)
    }
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(palette.accent)
            .clickable(role = Role.Button) { PlaybackEngine.togglePlayPause() }
            .semantics {
                contentDescription = label
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = if (reduced) 0 else TRANSPORT_MOTION_MS),
            label = "playPause"
        ) { shown ->
            Icon(shown, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(38.dp))
        }
        if (player.intentPlaying && player.buffering) {
            CircularProgressIndicator(
                Modifier.fillMaxSize().padding(5.dp),
                strokeWidth = 2.dp,
                color = palette.onAccent.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun PlayerActions(
    onOpenQueue: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onShowInChat: (() -> Unit)?
) {
    val queueLabel = stringResource(R.string.player_queue)
    val equalizerLabel = stringResource(R.string.player_equalizer)
    val showInChatLabel = stringResource(R.string.media_show_in_chat)
    val actions = buildList {
        add(PlayerActionUi(Icons.Rounded.QueueMusic, queueLabel, onOpenQueue))
        if (onOpenEqualizer != null) add(PlayerActionUi(Icons.Rounded.Equalizer, equalizerLabel, onOpenEqualizer))
        if (onShowInChat != null) add(PlayerActionUi(Icons.Rounded.ChatBubble, showInChatLabel, onShowInChat))
    }
    if (actions.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            PlayerAction(action, Modifier.weight(1f))
        }
    }
}

private data class PlayerActionUi(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun PlayerAction(action: PlayerActionUi, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(action.icon, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(22.dp))
        Text(
            action.label,
            color = palette.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueuePanel(
    source: PlaybackSourceLabel,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val queue = rememberMusicQueueUi()
    LaunchedEffect(queue == null) { if (queue == null) onDismiss() }
    val current = queue ?: return
    val palette = beyGramPalette()
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dropTarget by remember { mutableIntStateOf(-1) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { QUEUE_ROW_HEIGHT.toPx() }
    val haptic = LocalHapticFeedback.current
    fun resetDrag() {
        dragFrom = -1
        dragOffset = 0f
        dropTarget = -1
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.common_back),
                    tint = palette.textSecondary
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.queue_title),
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val countLabel = stringResource(R.string.queue_count, current.size)
                val loadingLabel = if (current.loadingCount > 0) {
                    stringResource(R.string.queue_loading_count, current.loadingCount)
                } else ""
                Text(
                    listOf(countLabel, loadingLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    color = palette.textSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_collapse), tint = palette.textSecondary)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                source.text,
                color = palette.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (current.shuffleEnabled) {
            Text(
                stringResource(R.string.queue_shuffled),
                color = palette.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)
        ) {
            item(key = "current-heading") {
                QueueSectionTitle(stringResource(R.string.queue_now_playing))
            }
            item(key = "current-${current.current?.item?.occurrenceId}") {
                current.current?.let { row ->
                    QueueCurrentCard(
                        row = row,
                        onRetry = { PlaybackEngine.togglePlayPause() },
                        onShowInChat = row.item.origin?.let { origin ->
                            {
                                PlaybackEngine.requestShowInChat(origin)
                                onDismiss()
                            }
                        }
                    )
                }
            }
            item(key = "next-heading") {
                QueueSectionTitle(stringResource(R.string.queue_next))
            }
            if (current.upcoming.isEmpty()) {
                item(key = "queue-empty") {
                    Text(
                        stringResource(R.string.queue_empty),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            }
            items(current.upcoming, key = { "${it.item.occurrenceId}:${it.index}" }) { row ->
                QueueUpcomingRow(
                    row = row,
                    dragging = dragFrom == row.index,
                    highlighted = dropTarget == row.index,
                    canMoveUp = row.index > current.currentIndex + 1,
                    canMoveDown = row.index < current.size - 1,
                    dragOffset = if (dragFrom == row.index) dragOffset else 0f,
                    onDragStart = {
                        dragFrom = row.index
                        dragOffset = 0f
                        dropTarget = row.index
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { amount ->
                        dragOffset += amount
                        val target = queueDragTarget(
                            from = row.index,
                            dragPx = dragOffset,
                            rowHeightPx = rowHeightPx,
                            currentIndex = current.currentIndex,
                            size = current.size
                        )
                        if (target != dropTarget) {
                            dropTarget = target
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                    },
                    onDragEnd = {
                        val from = dragFrom
                        val target = dropTarget
                        resetDrag()
                        if (from >= 0 && target >= 0 && from != target) PlaybackEngine.moveInQueue(from, target)
                    },
                    onDragCancel = ::resetDrag,
                    onMoveUp = {
                        if (row.index > current.currentIndex + 1) PlaybackEngine.moveInQueue(row.index, row.index - 1)
                    },
                    onMoveDown = {
                        if (row.index < current.size - 1) PlaybackEngine.moveInQueue(row.index, row.index + 1)
                    },
                    onPlayNext = {
                        val item = row.item
                        if (item.isMusic) PlaybackEngine.playNext(item)
                    },
                    onRemove = {
                        if (row.index >= 0) PlaybackEngine.removeFromQueue(row.index)
                    },
                    onShowInChat = row.item.origin?.let { origin ->
                        {
                            PlaybackEngine.requestShowInChat(origin)
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueSectionTitle(text: String) {
    val palette = beyGramPalette()
    Text(
        text,
        color = palette.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun QueueCurrentCard(
    row: QueueItemUi,
    onRetry: () -> Unit,
    onShowInChat: (() -> Unit)?
) {
    val palette = beyGramPalette()
    Surface(
        color = palette.accentSoft,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueArtwork(row.item, 52.dp, palette)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.item.musicTitle(),
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.item.musicArtist().isNotBlank()) {
                    Text(
                        row.item.musicArtist(),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    queueStatusText(row.status),
                    color = if (row.status == QueueItemStatus.CurrentFailed) palette.danger else palette.accentInk,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (row.status == QueueItemStatus.CurrentFailed) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.player_retry), color = palette.accentInk)
                }
            }
            if (onShowInChat != null) {
                IconButton(onClick = onShowInChat) {
                    Icon(Icons.Rounded.ChatBubble, stringResource(R.string.media_show_in_chat), tint = palette.accentInk)
                }
            }
        }
    }
}

@Composable
private fun QueueUpcomingRow(
    row: QueueItemUi,
    dragging: Boolean,
    highlighted: Boolean,
    dragOffset: Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    onShowInChat: (() -> Unit)?
) {
    val palette = beyGramPalette()
    var menuOpen by remember { mutableStateOf(false) }
    val moveUpLabel = stringResource(R.string.queue_move_up)
    val moveDownLabel = stringResource(R.string.queue_move_down)
    val dragLabel = stringResource(R.string.queue_drag)
    val actionsLabel = stringResource(R.string.queue_actions)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = QUEUE_ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) palette.accentSoft.copy(alpha = 0.65f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = dragLabel
                    customActions = listOf(
                        CustomAccessibilityAction(moveUpLabel) {
                            if (canMoveUp) {
                                onMoveUp()
                                true
                            } else false
                        },
                        CustomAccessibilityAction(moveDownLabel) {
                            if (canMoveDown) {
                                onMoveDown()
                                true
                            } else false
                        }
                    )
                }
                .pointerInput(row.index, canMoveUp, canMoveDown) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragCancel = { onDragCancel() },
                        onDragEnd = { onDragEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.DragHandle, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        }
        QueueArtwork(row.item, 40.dp, palette)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.item.musicTitle(),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.item.musicArtist().isNotBlank()) {
                    Text(
                        row.item.musicArtist(),
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                val duration = row.item.durationSeconds
                if (duration > 0) {
                    Text(
                        (if (row.item.musicArtist().isNotBlank()) " · " else "") + playbackClock(duration * 1000L),
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (row.status == QueueItemStatus.Loading || row.status == QueueItemStatus.Unavailable) {
                    Text(
                        " · " + queueStatusText(row.status),
                        color = if (row.status == QueueItemStatus.Unavailable) palette.danger else palette.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, actionsLabel, tint = palette.textSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_play_next)) },
                    onClick = {
                        menuOpen = false
                        onPlayNext()
                    }
                )
                if (onShowInChat != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_show_in_chat)) },
                        onClick = {
                            menuOpen = false
                            onShowInChat()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.queue_remove), color = palette.danger) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueArtwork(item: PlaybackItem, size: Dp, palette: com.beykus.beygram.ui.components.BeyGramPalette) {
    TrackArtwork(
        artworkPath = item.artworkPath,
        audioPath = item.path.takeIf { it.isNotBlank() },
        modifier = Modifier.size(size),
        background = palette.surface2,
        tint = palette.accentInk,
        symbolSize = size * 0.42f,
        cornerRadius = 10.dp,
        border = palette.divider.copy(alpha = 0.5f)
    )
}

@Composable
private fun queueStatusText(status: QueueItemStatus): String = stringResource(
    when (status) {
        QueueItemStatus.CurrentPlaying -> R.string.a11y_state_playing
        QueueItemStatus.CurrentPaused -> R.string.a11y_state_paused
        QueueItemStatus.CurrentBuffering -> R.string.player_buffering
        QueueItemStatus.CurrentEnded -> R.string.music_card_ended
        QueueItemStatus.CurrentFailed -> R.string.queue_failed
        QueueItemStatus.Loading -> R.string.queue_loading
        QueueItemStatus.Ready -> R.string.queue_ready
        QueueItemStatus.Unavailable -> R.string.queue_unavailable
    }
)
