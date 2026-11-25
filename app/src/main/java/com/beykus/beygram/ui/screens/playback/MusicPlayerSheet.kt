package com.beykus.beygram.ui.screens.playback

import androidx.activity.compose.BackHandler
import com.beykus.beygram.ui.design.styledShape
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.beykus.beygram.ui.screens.music.WaveBackdrop
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.ui.motion.MotionSpec
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import com.beykus.beygram.media.bgbeat.BgBeatSection
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.BeyGramPopover
import com.beykus.beygram.ui.components.BeyGramPopoverItem
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.canvas.BgCanvasMode
import com.beykus.beygram.data.canvas.CanvasLoadState
import com.beykus.beygram.media.bgbeat.BgBeatSection
import com.beykus.beygram.media.bgwave.BgWaveHub
import androidx.compose.material.icons.rounded.GraphicEq
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.playback.SLEEP_TIMER_MAX_MINUTES
import com.beykus.beygram.playback.SleepTimerState
import com.beykus.beygram.playback.sleepTimerRemainingMinutes
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.ui.screens.music.rememberProviderCover
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.beykus.beygram.data.music.MusicLibraries
import com.beykus.beygram.data.music.provider.YANDEX_TRACK_KEY_PREFIX
import com.beykus.beygram.data.music.MusicOutcome
import com.beykus.beygram.data.music.toPlaylistTrack
import com.beykus.beygram.ui.screens.music.AddToPlaylistSheet
import com.beykus.beygram.ui.screens.music.containsTrack
import com.beykus.beygram.ui.screens.music.rememberMusicLibrary
import com.beykus.beygram.ui.screens.music.reportMusicOutcome
import com.beykus.beygram.ui.screens.profile.LocalAddProfileAudio
import com.beykus.beygram.ui.screens.profile.PROFILE_AUDIO_KEY_PREFIX
import com.beykus.beygram.ui.text.userMessage
import com.beykus.beygram.ui.screens.music.LocalOpenProviderAlbum
import com.beykus.beygram.ui.screens.music.PlayerProviderTrackMenuItems

private const val SEEK_STEP_MS = 5_000L
private val QUEUE_ROW_HEIGHT = 76.dp
private val QUEUE_RISE_DP = 48.dp

internal enum class PlayerPanel { Player, Queue, Lyrics }

/**
 * Stops the sheet from taking over a list's overscroll at its end. When a list reaches its bottom, the upward
 * leftover of a drag or fling was passed to the ModalBottomSheet's own drag, so the sheet bounced back and forth.
 * This node sits inside the sheet and consumes only that upward leftover (negative y) after the child has scrolled.
 * Downward leftover still reaches the sheet, so dragging it down from the top keeps dismissing it.
 */
internal val SheetOverscrollGuard: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, available.y.coerceAtMost(0f))

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y.coerceAtMost(0f))
}

/**
 * The body of the full music player: the header with the player or the queue. The overlay in PlayerExpansionHost
 * owns the container, the drag and the back gesture, so this only lays out what it is given.
 */
@Composable
internal fun MusicPlayerContent(
    player: MusicPlayerUi,
    panel: PlayerPanel,
    onPanel: (PlayerPanel) -> Unit,
    onCollapse: () -> Unit,
    onLeave: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onOpenMusicSearch: (() -> Unit)?,
    onOpenMyMusic: (() -> Unit)?,
    modifier: Modifier = Modifier,
    ambientAccent: Color = beyGramPalette().surface
) {
    val source = rememberPlaybackSourceLabel(
        origin = player.item.origin,
        isProviderTrack = player.item.key.startsWith(YANDEX_TRACK_KEY_PREFIX),
        // Not a state: read again on each recomposition, which every queue change causes.
        radio = PlaybackEngine.activeRadio
    )
    BackHandler(enabled = panel != PlayerPanel.Player) { onPanel(PlayerPanel.Player) }
    // The endless radio (a wave) gets soft drifting ribbons behind the player while it is audible.
    val endless = PlaybackEngine.activeRadio != null && PlaybackEngine.currentQueueIsEndless()
    val waveRibbons = endless && player.playing
    val palette = beyGramPalette()
    val queueSpec = rememberMotionSpec(MotionToken.PlayerExpand)
    val risePx = with(LocalDensity.current) { QUEUE_RISE_DP.toPx() }
    // 0 = player, 1 = queue. Only layer lambdas read it, so a frame of the transition never recomposes the content.
    val queueMotion = remember { Animatable(if (panel == PlayerPanel.Queue) 1f else 0f) }
    // The panel at rest. It lags the requested panel while a transition runs, and both panels are composed then.
    var settled by remember { mutableStateOf(panel) }
    // The content's own coordinates: the queue flights are laid out and drawn in them.
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var queueArtworkPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(panel) {
        val target = if (panel == PlayerPanel.Queue) 1f else 0f
        if (queueMotion.value != target) {
            // Both panels are composed already when a move is reversed half way; otherwise wait two frames, so the
            // incoming panel is laid out before it starts to move.
            if (queueMotion.value == 0f || queueMotion.value == 1f) {
                withFrameNanos { }
                withFrameNanos { }
            }
            if (queueSpec.isInstant) {
                queueMotion.snapTo(target)
            } else {
                val root = contentCoords?.takeIf { it.isAttached }
                val roles = if (root != null) queueRolesNow(root) else emptySet()
                if (FlightRole.Artwork in roles && root != null) {
                    val from = PlayerExpansion.targets.artwork?.rectIn(root)?.width ?: 0f
                    val to = PlayerExpansion.queueTargets.artwork?.rectIn(root)?.width ?: 0f
                    queueArtworkPx = maxOf(from, to)
                }
                PlayerExpansion.setQueueRoles(roles)
                try {
                    queueMotion.animateTo(target, animationSpec = queueSpec.animationSpec<Float>())
                } finally {
                    // The real elements come back in the frame the flights stop; a reversed move recomputes them.
                    if (queueMotion.value == target) settled = panel
                    PlayerExpansion.setQueueRoles(emptySet())
                }
            }
        }
        settled = panel
    }
    val moving = (settled == PlayerPanel.Queue) != (panel == PlayerPanel.Queue)
    // The mini bar's shared elements do not fly while the queue moves; the container still does.
    SideEffect { PlayerExpansion.queueBusy = moving }
    DisposableEffect(Unit) {
        onDispose {
            PlayerExpansion.queueBusy = false
            PlayerExpansion.setQueueRoles(emptySet())
        }
    }
    val showPlayer = settled == PlayerPanel.Player || panel == PlayerPanel.Player
    val showQueue = settled == PlayerPanel.Queue || panel == PlayerPanel.Queue
    val lyricsVisible = panel == PlayerPanel.Lyrics
    val playerOverlayAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible) 0f else 1f,
        animationSpec = queueSpec.animationSpec(),
        label = "playerLyricsFade"
    )
    // Precomputed lightweight gradient from the cached cover palette: no realtime blur, single draw pass.
    val ambientBrush = remember(ambientAccent, palette.surface) {
        if (ambientAccent == palette.surface) null
        else Brush.verticalGradient(
            0.0f to ambientAccent,
            0.55f to palette.surface,
            1.0f to palette.surface
        )
    }
    // Kept across Player and Queue, so the list keeps its place while the overlay stays open.
    val queueList = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var queuePlaced by rememberSaveable { mutableStateOf(false) }
    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    val rhythmSignal = rememberBgRhythmSignal(
        item = player.item,
        playing = player.playing,
        speed = player.speed,
        fallbackDurationMs = fallbackDurationMs,
        active = panel == PlayerPanel.Player,
    )
    val rhythmGlowBrush = remember(palette.accent) {
        Brush.verticalGradient(
            0.0f to palette.accent,
            0.65f to palette.accent.copy(alpha = 0.2f),
            1.0f to Color.Transparent,
        )
    }
    val (canvasMode, canvasState) = rememberBgCanvasState(
        item = player.item,
        active = panel == PlayerPanel.Player,
    )
    val cardCanvasState = if (canvasMode == BgCanvasMode.Card) canvasState else CanvasLoadState.Idle
    Box(
        modifier
            .fillMaxWidth()
            .then(if (ambientBrush != null) Modifier.drawBehind { drawRect(ambientBrush) } else Modifier)
            .drawBehind {
                if (rhythmSignal.isActive && rhythmSignal.phraseEnergy > 0f) {
                    drawRect(
                        brush = rhythmGlowBrush,
                        alpha = (rhythmSignal.phraseEnergy * 0.16f).coerceIn(0f, 0.16f),
                    )
                }
            }
            .onGloballyPositioned { contentCoords = it }
    ) {
        if (canvasMode == BgCanvasMode.Fullscreen && canvasState is CanvasLoadState.Ready) {
            BgCanvasOverlay(
                state = canvasState,
                playing = player.playing,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.48f))
            )
        }
        if (waveRibbons) {
            WaveBackdrop(
                color = palette.accent,
                secondary = palette.accentSoft,
                modifier = Modifier.matchParentSize().alpha(0.5f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
        ) {
            if (showPlayer) {
                // The player fades out over the first part of the transition, before the queue comes up.
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - ramp(queueMotion.value, 0f, 0.45f)) * playerOverlayAlpha }
                        .blockInputWhile(moving || lyricsVisible)
                ) {
                    val openProviderAlbum = LocalOpenProviderAlbum.current
                    val providerAlbumId = remember(player.item.key) {
                        ProviderTracks.byKey(player.item.key)
                            ?.takeIf { ProviderTracks.isProviderKey(player.item.key) }
                            ?.albumId
                            ?.takeIf { it.isNotBlank() }
                    }
                    val openSourceAction: (() -> Unit)? = when {
                        player.item.origin != null -> {
                            val origin = player.item.origin
                            { PlaybackEngine.requestShowInChat(origin); onLeave() }
                        }
                        providerAlbumId != null && openProviderAlbum != null -> {
                            { onLeave(); openProviderAlbum(providerAlbumId) }
                        }
                        else -> null
                    }
                    val openSourceLabel = if (player.item.origin != null) {
                        stringResource(R.string.media_show_in_chat)
                    } else {
                        stringResource(R.string.music_provider_open_album)
                    }
                    PlayerHeader(
                        player = player,
                        source = source,
                        onCollapse = onCollapse,
                        onLeave = onLeave,
                        onOpenSource = openSourceAction,
                        openSourceLabel = openSourceLabel,
                        onOpenMyMusic = onOpenMyMusic?.let { open -> { onLeave(); open() } }
                    )
                    PlayerBody(
                        modifier = Modifier.weight(1f),
                        player = player,
                        rhythmSignal = rhythmSignal,
                        cardCanvasState = cardCanvasState,
                        onOpenQueue = { onPanel(PlayerPanel.Queue) },
                        onOpenLyrics = { onPanel(PlayerPanel.Lyrics) },
                        onOpenEqualizer = onOpenEqualizer?.let { open ->
                            {
                                // Leave the player before navigating to the equalizer route; otherwise it stays
                                // on top of the new screen and Back returns to an already-open player.
                                onLeave()
                                open()
                            }
                        },
                        onShowInChat = player.item.origin?.let { origin -> { PlaybackEngine.requestShowInChat(origin); onLeave() } }
                    )
                }
            }
            AnimatedVisibility(
                visible = lyricsVisible,
                enter = queueSpec.fadeEnter(),
                exit = queueSpec.fadeExit(),
                modifier = Modifier.fillMaxSize()
            ) {
                LyricsPanel(
                    player = player,
                    onBack = { onPanel(PlayerPanel.Player) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (showQueue) {
                // The queue fades in while it rises; it starts only after the player has faded, so the two never overlap.
                QueuePanel(
                    source = source,
                    onBack = { onPanel(PlayerPanel.Player) },
                    onDismiss = onLeave,
                    onSearch = onOpenMusicSearch?.let { open ->
                        {
                            onLeave()
                            open()
                        }
                    },
                    // Until the transition settles, a faded-out layer must not take touches meant for the visible one.
                    modifier = Modifier
                        .blockInputWhile(moving)
                        .graphicsLayer {
                            val q = queueMotion.value
                            alpha = ramp(q, 0.55f, 1f)
                            translationY = (1f - q) * risePx
                        },
                    listState = queueList,
                    placed = queuePlaced,
                    onPlaced = { queuePlaced = true }
                )
            }
        }
        if (moving) {
            Box(Modifier.matchParentSize()) {
                QueueFlights(
                    player = player,
                    queueProgress = { queueMotion.value },
                    rootCoords = { contentCoords },
                    artworkPx = queueArtworkPx
                )
            }
        }
    }
}

/**
 * Takes every touch while [blocked] holds, before the children see it: a layer that a transition fades out then
 * receives no tap, drag or long press, so an invisible row never acts for the visible panel.
 */
private fun Modifier.blockInputWhile(blocked: Boolean): Modifier = if (!blocked) this else pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** Minutes left on the running sleep timer, refreshed every half minute. The one counter both the menu and the status line read. */
@Composable
internal fun rememberSleepMinutes(sleepTimer: SleepTimerState?): Int {
    val minutes by produceState(0, sleepTimer) {
        val activeTimer = sleepTimer
        while (activeTimer != null && !activeTimer.untilTrackEnd) {
            value = sleepTimerRemainingMinutes(activeTimer.deadlineElapsedMs, SystemClock.elapsedRealtime())
            delay(30_000L)
        }
    }
    return minutes
}

@Composable
internal fun sleepTimerStatusText(sleepTimer: SleepTimerState, minutesRemaining: Int): String =
    if (sleepTimer.untilTrackEnd) stringResource(R.string.player_sleep_status_track_end)
    else stringResource(R.string.player_sleep_remaining, minutesRemaining)

@Composable
private fun PlayerHeader(
    player: MusicPlayerUi,
    source: PlaybackSourceLabel,
    onCollapse: () -> Unit,
    onLeave: () -> Unit,
    onOpenSource: (() -> Unit)?,
    openSourceLabel: String = stringResource(R.string.media_show_in_chat),
    onOpenMyMusic: (() -> Unit)?
) {
    val palette = beyGramPalette()
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
    val sourceLabel = source.caption ?: stringResource(R.string.player_source_from)
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
                .clip(styledShape(12.dp))
                .then(
                    if (onOpenSource != null) Modifier.clickable(role = Role.Button, onClickLabel = openSourceLabel) { onOpenSource() }
                    else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.isRadio) {
                    Icon(Icons.Rounded.Waves, null, tint = palette.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    sourceLabel,
                    color = if (source.isRadio) palette.accent else palette.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source.text,
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onOpenSource != null) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = palette.textSecondary.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        PlayerTrackMenu(item = player.item, onCollapse = onLeave, onOpenMyMusic = onOpenMyMusic)
    }
}

/**
 * The «⋮» menu of the full player. It holds only what the track itself can do; speed, the sleep timer and the equalizer
 * live in the sound sheet. «Моя музыка» comes last, under a divider, because it leaves the player for another screen.
 */
@Composable
private fun PlayerTrackMenu(item: PlaybackItem, onCollapse: () -> Unit, onOpenMyMusic: (() -> Unit)?) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<PendingTrackShare?>(null) }
    var pendingProviderShare by remember { mutableStateOf<PendingProviderLinkShare?>(null) }
    var waveSharing by remember { mutableStateOf(false) }
    // Sharing sends from the chat source the player uses. A track from a chat is offered only once that chat is known
    // not to protect its content; a chat that cannot be checked here gets no share.
    val originChat = rememberOriginChat(item.origin)
    val protectedChat = originChat?.hasProtectedContent == true
    val shareSource = LocalPlaybackRepositorySource.current
    val shareAllowed = shareSource != null &&
        canShareTrack(completedShareItem(item), originUnprotected = item.origin == null || originChat?.hasProtectedContent == false)
    // A source track is shared as its link; the file never leaves the app.
    val providerTrack = ProviderTracks.byKey(item.key)?.takeIf { ProviderTracks.isProviderKey(item.key) }
    val providerSharePayload = providerTrack?.let(::providerTrackSharePayload)
    val shareLabel = stringResource(R.string.player_share)
    val waveActive = rememberWaveActive()
    val waveShareLabel = stringResource(R.string.bgwave_share_action)
    val waveStopLabel = stringResource(R.string.bgwave_action_stop_wave)
    val shareChooser = stringResource(R.string.music_provider_share_link)
    val showInChatLabel = stringResource(R.string.media_show_in_chat)
    val addToProfile = LocalAddProfileAudio.current
    val addToProfileLabel = stringResource(R.string.player_add_to_profile)
    val addToProfileDone = stringResource(R.string.player_add_to_profile_done)
    // The shared player may be bound to another account; its file ids mean nothing to the active one.
    val activeAccount = PlaybackEngine.activeAccount()
    val myMusicLabel = stringResource(R.string.music_library_title)
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.player_menu), tint = palette.textSecondary)
        }
        BeyGramPopover(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // Wave by track and album of a source track. The like is in the bottom row, and the share is merged below.
            PlayerProviderTrackMenuItems(
                key = item.key,
                onDismiss = { menuOpen = false },
                // The album opens behind the player, so the player closes first.
                onLeavePlayer = onCollapse
            )
            if (providerSharePayload != null) PlayerMenuEntry(shareLabel, Icons.Rounded.Share) {
                menuOpen = false
                val source = shareSource
                if (source != null) pendingProviderShare = PendingProviderLinkShare(providerSharePayload, source)
                else if (!ProviderTracks.sharePublicText(context, providerSharePayload.text, shareChooser)) {
                    Toast.makeText(context, R.string.share_external_unavailable, Toast.LENGTH_SHORT).show()
                }
            } else if (canOfferLocalAudioShare(item.key, shareAllowed)) PlayerMenuEntry(shareLabel, Icons.Rounded.Share) {
                menuOpen = false
                shareSource?.let { source ->
                    pendingShare = PendingTrackShare(completedShareItem(item), shareTimecode(PlaybackEngine.state.value.positionMs), source)
                }
            }
            if (waveActive) PlayerMenuEntry(waveStopLabel, Icons.Rounded.GraphicEq) {
                menuOpen = false
                scope.launch { BgWaveHub.stop() }
            } else if (shareSource != null && canShareWave(item, originUnprotected = item.origin == null || originChat?.hasProtectedContent == false, isProviderTrack = ProviderTracks.isProviderKey(item.key))) {
                PlayerMenuEntry(waveShareLabel, Icons.Rounded.GraphicEq) {
                    menuOpen = false
                    waveSharing = true
                }
            }
            item.origin?.let { origin ->
                PlayerMenuEntry(showInChatLabel, Icons.Rounded.ChatBubble) {
                    menuOpen = false
                    PlaybackEngine.requestShowInChat(origin)
                    onCollapse()
                }
            }
            // Only a music file the active account can see from a chat is a Telegram profile track. A track that already
            // plays from a profile is in a profile, so the action would only add a copy.
            if (addToProfile != null && item.isMusic && item.fileId != null && !item.systemPrivate && !protectedChat &&
                !item.key.startsWith(PROFILE_AUDIO_KEY_PREFIX) && (activeAccount == null || item.accountId == activeAccount)
            ) PlayerMenuEntry(addToProfileLabel, Icons.Rounded.PersonAdd) {
                menuOpen = false
                scope.launch {
                    addToProfile(item)
                        .onSuccess { Toast.makeText(context, addToProfileDone, Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.userMessage(context, R.string.player_add_to_profile_failed), Toast.LENGTH_SHORT).show() }
                }
            }
            if (onOpenMyMusic != null) {
                HorizontalDivider(color = palette.divider)
                PlayerMenuEntry(myMusicLabel, Icons.Rounded.MusicNote) {
                    menuOpen = false
                    onOpenMyMusic()
                }
            }
        }
    }
    if (waveSharing && shareSource != null) {
        ShareWaveDialog(shareSource.repository, shareSource.accountId, onClose = { waveSharing = false })
    }
    pendingShare?.let { share ->
        ShareTrackDialog(
            repository = share.source.repository,
            item = share.item,
            caption = share.caption,
            onClose = { pendingShare = null }
        )
    }
    pendingProviderShare?.let { share ->
        ShareProviderLinkDialog(share, onClose = { pendingProviderShare = null })
    }
}

@Composable
private fun PlayerMenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    val palette = beyGramPalette()
    BeyGramPopoverItem(
        text = label,
        leadingIcon = { Icon(icon, null, tint = palette.accent) },
        onClick = onClick
    )
}

internal const val SLEEP_EXTEND_MINUTES = 15

@Composable
private fun PlayerBody(
    player: MusicPlayerUi,
    rhythmSignal: BgRhythmSignal,
    cardCanvasState: CanvasLoadState,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onShowInChat: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val mediaMotion = rememberMotionSpec(MotionToken.MediaExpand)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        if (landscape) {
            val artwork = minOf(maxHeight * 0.74f, maxWidth * 0.42f, 360.dp)
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerArtwork(player, artwork, mediaMotion, rhythmSignal, cardCanvasState)
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
                        mediaMotion = mediaMotion,
                        onOpenQueue = onOpenQueue,
                        onOpenLyrics = onOpenLyrics,
                        onOpenEqualizer = onOpenEqualizer,
                        onShowInChat = onShowInChat
                    )
                }
            }
        } else {
            val artwork = minOf(maxWidth - 64.dp, maxHeight * 0.40f, 360.dp).coerceAtLeast(140.dp)
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(
                    Modifier
                        // Center the artwork and controls when they fit; the scroll container still fills
                        // the viewport and scrolls on short screens or at larger system font scales.
                        .align(Alignment.Center)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerArtwork(player, artwork, mediaMotion, rhythmSignal, cardCanvasState)
                    Spacer(Modifier.height(18.dp))
                    PlayerDetails(
                        player = player,
                        mediaMotion = mediaMotion,
                        onOpenQueue = onOpenQueue,
                        onOpenLyrics = onOpenLyrics,
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
    mediaMotion: MotionSpec,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onShowInChat: (() -> Unit)?
) {
    // The position is read only inside the provider, which the wave calls while drawing. The rest of the details
    // (title, controls, actions) therefore does not recompose four times a second.
    val positionState = rememberPlaybackPositionState()
    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    val playedProvider = {
        val position = positionState.value
        val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
        if (duration > 0L) (position.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    TrackMetadata(player, mediaMotion)
    PlayerSeekSection(player)
    if (player.bgMix) BgMixTransitionLine()
    Spacer(Modifier.height(6.dp))
    TransportControls(player, mediaMotion)
    Spacer(Modifier.height(14.dp))
    PlayerActions(player, onOpenQueue, onOpenLyrics, onOpenEqualizer)
    Spacer(Modifier.height(10.dp))
    BgBeatSection(
        item = player.item,
        playbackFractionProvider = playedProvider,
        onSeekFraction = { fraction ->
            PlaybackEngine.seekTo(fraction.coerceIn(0f, 1f), fallbackDurationMs = fallbackDurationMs)
        },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

internal const val ARTWORK_DOUBLE_TAP_SEEK_MS = 10_000L

/** Computes the clamped target seek fraction for a relative seek delta, or null when duration is unknown. */
internal fun seekDeltaFraction(positionMs: Long, durationMs: Long, deltaMs: Long): Float? {
    if (durationMs <= 0L) return null
    val target = (positionMs + deltaMs).coerceIn(0L, durationMs)
    return (target.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun PlayerArtwork(
    player: MusicPlayerUi,
    size: Dp,
    mediaMotion: MotionSpec,
    rhythmSignal: BgRhythmSignal,
    cardCanvasState: CanvasLoadState = CanvasLoadState.Idle,
) {
    val palette = beyGramPalette()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val targets = PlayerExpansion.targets
    SideEffect {
        targets.artworkBackground = palette.accentSoft
        targets.artworkTint = palette.accentInk
        targets.artworkBorder = palette.divider.copy(alpha = 0.55f)
        targets.artworkRadiusPx = with(density) { 20.dp.toPx() }
    }
    DisposableEffect(Unit) { onDispose { targets.artwork = null } }
    val reduced = mediaMotion.isInstant
    val entryScale = remember(player.item.key) {
        Animatable(if (reduced) 1f else 1f - 0.06f * mediaMotion.intensity)
    }
    LaunchedEffect(player.item.key, reduced) {
        if (reduced) entryScale.snapTo(1f)
        else entryScale.animateTo(1f, mediaMotion.animationSpec())
    }
    val playing = player.playing || player.intentPlaying
    val breatheScale by animateFloatAsState(
        targetValue = if (reduced || playing) 1f else 0.94f,
        animationSpec = mediaMotion.animationSpec(),
        label = "artworkBreathe"
    )
    val elevation by animateDpAsState(
        targetValue = if (reduced || playing) 10.dp else 4.dp,
        animationSpec = mediaMotion.animationSpec(),
        label = "artworkElevation"
    )
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    var dragOffsetX by remember(player.item.key) { mutableFloatStateOf(0f) }
    val animatedDragX by animateFloatAsState(
        targetValue = if (reduced) 0f else dragOffsetX,
        animationSpec = mediaMotion.animationSpec(),
        label = "artworkSwipe"
    )
    // Short visual feedback badge when double-tapping left/right half of the artwork (±10s).
    var seekFlashDirection by remember(player.item.key) { mutableIntStateOf(0) }
    var seekFlashNonce by remember(player.item.key) { mutableIntStateOf(0) }
    LaunchedEffect(seekFlashNonce) {
        if (seekFlashNonce > 0) {
            delay(650L)
            seekFlashDirection = 0
        }
    }
    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    Box(
        Modifier
            .size(size)
            .pointerInput(player.item.key, fallbackDurationMs) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val state = PlaybackEngine.state.value
                        val duration = state.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
                        val forward = offset.x >= this.size.width / 2f
                        val delta = if (forward) ARTWORK_DOUBLE_TAP_SEEK_MS else -ARTWORK_DOUBLE_TAP_SEEK_MS
                        val fraction = seekDeltaFraction(state.positionMs, duration, delta) ?: return@detectTapGestures
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PlaybackEngine.seekTo(fraction, fallbackDurationMs = fallbackDurationMs)
                        seekFlashDirection = if (forward) 1 else -1
                        seekFlashNonce += 1
                    }
                )
            }
            .pointerInput(player.item.key, player.canNext, player.canPrevious) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = {
                        total = 0f
                        dragOffsetX = 0f
                    },
                    onDragEnd = {
                        val dx = total
                        total = 0f
                        dragOffsetX = 0f
                        when {
                            dx <= -swipeThresholdPx && player.canNext -> {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                PlaybackEngine.next()
                            }
                            dx >= swipeThresholdPx && player.canPrevious -> {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                PlaybackEngine.previous()
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                        val maxShift = swipeThresholdPx * 0.65f
                        dragOffsetX = (total * 0.45f).coerceIn(-maxShift, maxShift)
                    }
                )
            }
            .graphicsLayer {
                val rhythmScale = if (rhythmSignal.isActive) {
                    1f + rhythmSignal.beatPulse * 0.025f + rhythmSignal.downbeatPulse * 0.015f
                } else {
                    1f
                }
                val combined = entryScale.value * breatheScale * rhythmScale
                scaleX = combined
                scaleY = combined
                translationX = animatedDragX
                alpha = if (PlayerExpansion.fullSharedHidden(FlightRole.Artwork)) 0f else 1f
            }
            .shadow(elevation, styledShape(20.dp), clip = false)
            .onGloballyPositioned { targets.artwork = it }
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
        BgCanvasOverlay(
            state = cardCanvasState,
            playing = player.playing,
            modifier = Modifier
                .fillMaxSize()
                .clip(styledShape(20.dp))
        )
        MascotOverlay(
            item = player.item,
            modifier = Modifier
                .fillMaxSize()
                .clip(styledShape(20.dp))
        )
        AnimatedVisibility(
            visible = seekFlashDirection != 0,
            enter = mediaMotion.fadeEnter(),
            exit = mediaMotion.fadeExit(),
            modifier = Modifier
                .align(if (seekFlashDirection < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 18.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = if (seekFlashDirection < 0) "−10s" else "+10s",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        MascotToggleChip(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
        )
    }
}

private data class TrackMetadataUi(
    val title: String,
    val artist: String,
    val album: String
)

@Composable
private fun TrackMetadata(player: MusicPlayerUi, mediaMotion: MotionSpec) {
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
    DisposableEffect(Unit) {
        onDispose {
            PlayerExpansion.targets.title = null
            PlayerExpansion.targets.artist = null
        }
    }
    val localStyle = LocalTextStyle.current
    val titleStyle = localStyle.merge(
        TextStyle(color = palette.textPrimary, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    )
    val artistStyle = localStyle.merge(TextStyle(color = palette.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center))
    Crossfade(
        targetState = metadata,
        animationSpec = mediaMotion.animationSpec(),
        label = "trackMetadata"
    ) { shown ->
        // Only the target text registers for the flight; the outgoing copy of a crossfade must never be used.
        val isTarget = shown == metadata
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isTarget) SideEffect {
                val targets = PlayerExpansion.targets
                targets.titleText = shown.title
                targets.titleStyle = titleStyle
                targets.artistText = shown.artist
                targets.artistStyle = artistStyle
            }
            Text(
                shown.title,
                color = palette.textPrimary,
                fontSize = 23.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .semantics { contentDescription = shown.title }
                    .onGloballyPositioned { if (isTarget) PlayerExpansion.targets.title = it }
                    .graphicsLayer { alpha = if (PlayerExpansion.fullSharedHidden(FlightRole.Title)) 0f else 1f }
            )
            if (shown.artist.isNotBlank()) {
                Text(
                    shown.artist,
                    color = palette.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .onGloballyPositioned { if (isTarget) PlayerExpansion.targets.artist = it }
                        .graphicsLayer { alpha = if (PlayerExpansion.fullSharedHidden(FlightRole.Artist)) 0f else 1f }
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
    val sleepTimer by PlaybackEngine.sleepTimer.collectAsStateWithLifecycle()
    val minutesRemaining = rememberSleepMinutes(sleepTimer)
    Column(
        modifier = Modifier.heightIn(min = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
    when {
        player.failed -> Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.player_failed), color = palette.danger, fontSize = 13.sp)
            TextButton(onClick = { PlaybackEngine.togglePlayPause() }) {
                Text(stringResource(R.string.player_retry))
            }
        }

        player.waitingForNetwork -> Row(
            Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_waiting_network), color = palette.textSecondary, fontSize = 13.sp)
        }

        player.intentPlaying && player.buffering -> Row(
            Modifier.padding(top = 4.dp),
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
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    val activeSpeed = player.speed.takeIf { it != 1f }?.let { stringResource(R.string.player_speed_option, speedLabel(it)) }
    val sleepText = sleepTimer?.let { sleepTimerStatusText(it, minutesRemaining) }
    val statusParts = listOfNotNull(activeSpeed, sleepText)
    val showBgMix = player.bgMix || player.bgMixPreparing
    if (statusParts.isNotEmpty() || showBgMix) {
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (statusParts.isNotEmpty()) {
                Text(
                    statusParts.joinToString(" · "),
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
            }
            if (showBgMix) {
                var explain by remember { mutableStateOf(false) }
                // Two letters instead of a badge: grey while BG Mix prepares the track, the theme accent once BG Mix plays it.
                val active = player.bgMix
                val markDescription = stringResource(if (active) R.string.player_bgmix_icon_active else R.string.player_bgmix_icon_preparing)
                Text(
                    stringResource(R.string.player_bgmix_mark),
                    color = if (active) palette.accent else palette.textSecondary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .clip(styledShape(6.dp))
                        .clickable(role = Role.Button) { explain = true }
                        .semantics { contentDescription = markDescription }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (explain) BeyGramDialog(
                    onDismissRequest = { explain = false },
                    title = stringResource(R.string.player_bgmix_badge),
                    message = stringResource(if (player.bgMix) R.string.player_bgmix_explain else R.string.player_bgmix_preparing_explain),
                    confirmText = stringResource(R.string.common_close),
                    onConfirm = { explain = false }
                )
            }
        }
    }
    }
}

@Composable
internal fun PlayerSeekSection(player: MusicPlayerUi, modifier: Modifier = Modifier) {
    // The drag and pending seek live here, so the time labels and thumb stay in sync without snapping back on release.
    var dragging by remember(player.item.key) { mutableStateOf<Float?>(null) }
    var pendingSeek by remember(player.item.key) { mutableStateOf<Float?>(null) }
    val position = rememberPlaybackPositionUi()
    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val smooth = rememberSmoothPositionMs(position.positionMs, duration, player.playing, player.speed)
    LaunchedEffect(position.positionMs) {
        if (pendingSeek != null) pendingSeek = null
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek != null) {
            delay(600L)
            pendingSeek = null
        }
    }
    val overrideFraction = dragging ?: pendingSeek
    Column(modifier.fillMaxWidth()) {
        PlayerSeekBar(
            position = position,
            fallbackDurationSeconds = player.item.durationSeconds,
            contentLabel = stringResource(R.string.player_progress),
            dragging = dragging,
            overrideFraction = overrideFraction,
            onDragging = { dragging = it },
            onSeekCommitted = { fraction ->
                pendingSeek = fraction
                PlaybackEngine.seekTo(fraction.coerceIn(0f, 1f), fallbackDurationMs = fallbackDurationMs)
            },
            smoothMs = smooth
        )
        PlayerTimeRow(
            position = position,
            fallbackDurationSeconds = player.item.durationSeconds,
            overrideFraction = overrideFraction,
            isDragging = dragging != null,
            smoothMs = smooth
        )
    }
}

/**
 * The playhead between two position ticks. The engine reports the position four times a second, which makes the thumb
 * of a short track move in steps; while the sound plays, the position is carried forward every frame at the playback speed
 * and corrected by each new tick.
 */
@Composable
internal fun rememberSmoothPositionMs(positionMs: Long, durationMs: Long, playing: Boolean, speed: Float): State<Long> {
    val shown = remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, durationMs, playing, speed) {
        shown.longValue = positionMs
        if (!playing) return@LaunchedEffect
        val startFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val advanced = ((frame - startFrame) / 1_000_000.0 * speed).toLong()
            val next = positionMs + advanced
            shown.longValue = if (durationMs > 0L) next.coerceAtMost(durationMs) else next
        }
    }
    return shown
}

@Composable
private fun PlayerSeekBar(
    position: PlaybackPositionUi,
    fallbackDurationSeconds: Int,
    contentLabel: String,
    dragging: Float?,
    overrideFraction: Float?,
    onDragging: (Float?) -> Unit,
    onSeekCommitted: (Float) -> Unit,
    smoothMs: State<Long>
) {
    val palette = beyGramPalette()
    val haptic = LocalHapticFeedback.current
    val microMotion = rememberMotionSpec(MotionToken.MicroFeedback)
    val fallbackDurationMs = (fallbackDurationSeconds.coerceAtLeast(0) * 1000L)
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val enabled = duration > 0L
    val played = overrideFraction ?: if (duration > 0L) (smoothMs.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffered = if (duration > 0L) position.bufferedFraction.coerceIn(0f, 1f) else 0f
    val trackHeightDp by animateDpAsState(
        targetValue = if (dragging != null) 6.dp else 4.dp,
        animationSpec = microMotion.animationSpec(),
        label = "seekTrackHeight"
    )
    val thumbRadiusDp by animateDpAsState(
        targetValue = if (dragging != null) 9.dp else 7.dp,
        animationSpec = microMotion.animationSpec(),
        label = "seekThumbRadius"
    )
    val stateText = stringResource(
        R.string.player_progress_a11y,
        playbackClock((played * duration).toLong()),
        playbackClock(duration)
    )
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { 10.dp.toPx() }
    fun seekTo(fraction: Float) {
        if (!enabled) return
        onSeekCommitted(fraction.coerceIn(0f, 1f))
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
                    var current: Float = fractionAt(down.position.x)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDragging(current)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            current = fractionAt(change.position.x)
                            onDragging(current)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    seekTo(current)
                    onDragging(null)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val targets = PlayerExpansion.targets
        SideEffect {
            targets.seekTrack = palette.divider
            targets.seekAccent = palette.accent
            targets.seekSurface = palette.surface
        }
        DisposableEffect(Unit) { onDispose { targets.seek = null } }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onGloballyPositioned { targets.seek = it }
                .graphicsLayer { alpha = if (PlayerExpansion.fullSharedHidden(FlightRole.Progress)) 0f else 1f }
        ) {
            val padding = 10.dp.toPx()
            val trackWidth = (size.width - padding * 2f).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val trackHeight = trackHeightDp.toPx()
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
                    radius = thumbRadiusDp.toPx(),
                    center = Offset(barEnd(played).x, centerY)
                )
                drawCircle(
                    color = palette.surface,
                    radius = (thumbRadiusDp * 0.42f).toPx(),
                    center = Offset(barEnd(played).x, centerY)
                )
            }
        }
    }
}

@Composable
private fun PlayerTimeRow(
    position: PlaybackPositionUi,
    fallbackDurationSeconds: Int,
    overrideFraction: Float?,
    isDragging: Boolean,
    smoothMs: State<Long>
) {
    val palette = beyGramPalette()
    val fallbackDurationMs = (fallbackDurationSeconds.coerceAtLeast(0) * 1000L)
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    // Whole seconds only, so the labels recompose once a second even though the playhead moves every frame.
    val playedSecond by remember(smoothMs) { derivedStateOf { smoothMs.value / 1_000L } }
    // While the thumb is dragged or settling, the labels show the target time, not the old position.
    val shownMs = overrideFraction?.let { (it * duration).toLong() } ?: (playedSecond * 1_000L)
    val timeStyle = remember { TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum") }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                playbackClock(shownMs),
                color = if (isDragging) palette.accent else palette.textSecondary,
                style = timeStyle
            )
            Text(
                playbackRemaining(shownMs, duration),
                color = palette.textSecondary,
                style = timeStyle
            )
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
private fun TransportControls(player: MusicPlayerUi, mediaMotion: MotionSpec) {
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
        PlayPauseButton(player, mediaMotion)
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
private fun PlayPauseButton(player: MusicPlayerUi, mediaMotion: MotionSpec) {
    val palette = beyGramPalette()
    val icon = when {
        player.failed -> Icons.Rounded.Replay
        player.ended -> Icons.Rounded.Replay
        player.playing || player.intentPlaying -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    val label = when {
        player.failed -> stringResource(R.string.player_retry)
        player.ended -> stringResource(R.string.player_replay)
        player.playing || player.intentPlaying -> stringResource(R.string.common_pause)
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
            .onGloballyPositioned { PlayerExpansion.targets.play = it }
            // Must sit before clip/background so the static circle hides completely while the flight circle animates.
            .graphicsLayer { alpha = if (PlayerExpansion.fullSharedHidden(FlightRole.Play)) 0f else 1f }
            .clip(CircleShape)
            .background(palette.accent)
            .clickable(role = Role.Button) { PlaybackEngine.togglePlayPause() }
            .semantics {
                contentDescription = label
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center
    ) {
        val targets = PlayerExpansion.targets
        val density = LocalDensity.current
        SideEffect {
            targets.playBackground = palette.accent
            targets.playTint = palette.onAccent
            targets.playIconPx = with(density) { 38.dp.toPx() }
        }
        DisposableEffect(Unit) { onDispose { targets.play = null } }
        Crossfade(
            targetState = icon,
            animationSpec = mediaMotion.animationSpec(),
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

internal enum class PlayerActionKind {
    ProviderLike,
    TelegramFavorite,
    RadioDislike,
    AddToPlaylist,
    Lyrics,
    Queue,
    Sound
}

/**
 * Pure policy for which bottom-row actions are shown for a track, ensuring unified UX across Telegram and Yandex
 * Music while exposing only capabilities that genuinely depend on the source.
 */
internal fun resolvePlayerActionKinds(
    isProviderTrack: Boolean,
    isEndlessRadio: Boolean,
    lyricsEnabled: Boolean
): List<PlayerActionKind> = buildList {
    add(if (isProviderTrack) PlayerActionKind.ProviderLike else PlayerActionKind.TelegramFavorite)
    when {
        isProviderTrack && isEndlessRadio -> {
            add(PlayerActionKind.RadioDislike)
            if (lyricsEnabled) add(PlayerActionKind.Lyrics)
        }
        isProviderTrack -> {
            if (lyricsEnabled) add(PlayerActionKind.Lyrics)
        }
        else -> {
            add(PlayerActionKind.AddToPlaylist)
            if (lyricsEnabled) add(PlayerActionKind.Lyrics)
        }
    }
    add(PlayerActionKind.Queue)
    add(PlayerActionKind.Sound)
}

@Composable
private fun PlayerActions(
    player: MusicPlayerUi,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEqualizer: (() -> Unit)?
) {
    val item = player.item
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queueLabel = stringResource(R.string.player_queue)
    val soundLabel = stringResource(R.string.player_sound)
    val lyricsLabel = stringResource(R.string.player_lyrics)
    val originChat = rememberOriginChat(item.origin)
    val protectedChat = originChat?.hasProtectedContent == true
    val track = remember(item.occurrenceId, item.origin) { item.toPlaylistTrack() }?.takeUnless { protectedChat }
    val library = rememberMusicLibrary(item.accountId)
    var addOpen by remember(item.occurrenceId) { mutableStateOf(false) }
    var soundOpen by remember { mutableStateOf(false) }
    val favorite = track != null && containsTrack(library?.favorites, track)
    val favoriteLabel = stringResource(R.string.music_favorite_label)
    val favoriteOnState = stringResource(R.string.music_favorite_state_on)
    val favoriteOffState = stringResource(R.string.music_favorite_state_off)
    val addLabel = stringResource(R.string.music_playlist_label)
    // Provider tracks (radio, album, playlist, search) all support liking on the service; dislike is radio-only.
    val isProviderKey = ProviderTracks.isProviderKey(item.key)
    val providerTrack = ProviderTracks.byKey(item.key)?.takeIf { isProviderKey }
    val isEndlessRadio = isProviderKey && PlaybackEngine.currentQueueIsEndless()
    val likes by ProviderTracks.liked.collectAsStateWithLifecycle()
    val providerLiked = providerTrack?.let { likes[it.trackId] ?: it.liked ?: false }
    val likeLabel = stringResource(R.string.music_provider_like)
    val likeFailed = stringResource(R.string.music_provider_like_failed)
    val dislikeLabel = stringResource(R.string.music_radio_dislike)
    val sleepTimer by PlaybackEngine.sleepTimer.collectAsStateWithLifecycle()
    val minutesRemaining = rememberSleepMinutes(sleepTimer)
    // The sound button shows the timer when one runs, else a speed other than 1×, else the plain word.
    val soundStatus: String? = sleepTimer?.let { timer ->
        if (timer.untilTrackEnd) stringResource(R.string.player_sleep_until_track_end)
        else stringResource(R.string.player_sleep_minutes_short, minutesRemaining)
    } ?: player.speed.takeIf { it != 1f }?.let { stringResource(R.string.player_speed_option, speedLabel(it)) }
    val libraryTrack = track != null && library != null && !isProviderKey

    val kinds = resolvePlayerActionKinds(
        isProviderTrack = isProviderKey,
        isEndlessRadio = isEndlessRadio,
        lyricsEnabled = if (isProviderKey) YANDEX_LYRICS_ENABLED else (item.isMusic && !item.systemPrivate)
    )
    val actions = kinds.map { kind ->
        when (kind) {
            PlayerActionKind.ProviderLike -> if (providerTrack != null && providerLiked != null) {
                PlayerActionUi(
                    icon = if (providerLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = likeLabel,
                    onClick = {
                        ProviderTracks.setLikedInBackground(context, providerTrack, !providerLiked) {
                            Toast.makeText(context.applicationContext, likeFailed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    active = providerLiked,
                    stateDescription = if (providerLiked) favoriteOnState else favoriteOffState
                )
            } else {
                PlayerActionUi(
                    icon = Icons.Rounded.FavoriteBorder,
                    label = likeLabel,
                    onClick = {},
                    enabled = false
                )
            }
            PlayerActionKind.TelegramFavorite -> if (libraryTrack && track != null) {
                PlayerActionUi(
                    icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    label = favoriteLabel,
                    onClick = { scope.launch { context.reportMusicOutcome(MusicLibraries.get(context).toggleFavorite(track)) } },
                    active = favorite,
                    stateDescription = if (favorite) favoriteOnState else favoriteOffState
                )
            } else {
                PlayerActionUi(
                    icon = Icons.Rounded.StarBorder,
                    label = favoriteLabel,
                    onClick = {},
                    enabled = false
                )
            }
            PlayerActionKind.RadioDislike -> PlayerActionUi(
                icon = Icons.Rounded.ThumbDown,
                label = dislikeLabel,
                onClick = { providerTrack?.let { ProviderTracks.dislikeWithUndo(context, it) } },
                enabled = providerTrack != null
            )
            PlayerActionKind.AddToPlaylist -> PlayerActionUi(
                icon = Icons.Rounded.Add,
                label = addLabel,
                onClick = { addOpen = true },
                enabled = libraryTrack
            )
            PlayerActionKind.Lyrics -> PlayerActionUi(
                icon = Icons.Rounded.Lyrics,
                label = lyricsLabel,
                onClick = onOpenLyrics
            )
            PlayerActionKind.Queue -> PlayerActionUi(
                icon = Icons.Rounded.QueueMusic,
                label = queueLabel,
                onClick = onOpenQueue
            )
            PlayerActionKind.Sound -> PlayerActionUi(
                icon = Icons.Rounded.Tune,
                label = soundStatus ?: soundLabel,
                onClick = { soundOpen = true },
                active = soundStatus != null
            )
        }
    }
    val pendingDislike by ProviderTracks.pendingDislike.collectAsStateWithLifecycle()
    if (pendingDislike != null) DislikeUndoBar(onUndo = { ProviderTracks.undoDislike(context) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { action ->
            PlayerAction(action, Modifier.weight(1f))
        }
    }
    if (addOpen && track != null) AddToPlaylistSheet(accountId = item.accountId, track = track, onDismiss = { addOpen = false })
    if (soundOpen) PlayerSoundSheet(player = player, onOpenEqualizer = onOpenEqualizer, onDismiss = { soundOpen = false })
}

@Composable
private fun DislikeUndoBar(onUndo: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.music_radio_disliked),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onUndo) { Text(stringResource(R.string.music_radio_dislike_undo), color = palette.accent) }
    }
}

private data class PlayerActionUi(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val active: Boolean = false,
    val stateDescription: String? = null,
    val enabled: Boolean = true
)

@Composable
private fun PlayerAction(action: PlayerActionUi, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val described = action.stateDescription
    Column(
        modifier
            .heightIn(min = 56.dp)
            .alpha(if (action.enabled) 1f else 0.4f)
            .clip(styledShape(14.dp))
            .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
            .padding(vertical = 7.dp)
            .then(if (described != null) Modifier.semantics { stateDescription = described } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(action.icon, contentDescription = null, tint = if (action.active) palette.accent else palette.accentInk, modifier = Modifier.size(22.dp))
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
    onDismiss: () -> Unit,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    placed: Boolean,
    onPlaced: () -> Unit
) {
    val queue = rememberMusicQueueUi()
    LaunchedEffect(queue == null) { if (queue == null) onDismiss() }
    val current = queue ?: return
    val palette = beyGramPalette()
    val loadingOlder by PlaybackEngine.queuePageLoading.collectAsStateWithLifecycle()
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dropTarget by remember { mutableIntStateOf(-1) }
    var queueMenuOpen by remember { mutableStateOf(false) }
    var saveQueueOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(current.size) {
        if (current.size <= 1) {
            searchOpen = false
            searchQuery = ""
        }
    }
    // Tracks that already played stay reachable above the current one; with shuffle there is no meaningful "before".
    val playedRows = if (current.shuffleEnabled || current.currentIndex <= 0) emptyList() else current.rows.take(current.currentIndex)
    val normalizedQuery = searchQuery.trim()
    val filtering = normalizedQuery.isNotEmpty()
    val visiblePlayed = remember(playedRows, normalizedQuery) { filterQueueRows(playedRows, normalizedQuery) }
    val visibleCurrent = remember(current.current, normalizedQuery) {
        current.current?.takeIf { filterQueueRows(listOf(it), normalizedQuery).isNotEmpty() }
    }
    val visibleUpcoming = remember(current.upcoming, normalizedQuery) { filterQueueRows(current.upcoming, normalizedQuery) }
    val currentCardListIndex = if (playedRows.isEmpty()) 1 else playedRows.size + 2
    val showJumpToCurrent by remember(current.current, normalizedQuery, currentCardListIndex) {
        derivedStateOf {
            val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
            shouldShowJumpToCurrent(
                currentItemListIndex = currentCardListIndex,
                visibleIndices = visibleIndices,
                queryBlank = normalizedQuery.isEmpty(),
                hasCurrent = current.current != null,
            )
        }
    }
    // The first open starts at the current track; later opens keep the place the list was left at.
    LaunchedEffect(Unit) {
        if (!placed) {
            listState.scrollToItem(if (playedRows.isEmpty()) 0 else playedRows.size + 1)
            onPlaced()
        }
    }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { QUEUE_ROW_HEIGHT.toPx() }
    if (saveQueueOpen) SaveQueueDialog(current.rows) { saveQueueOpen = false }
    val haptic = LocalHapticFeedback.current
    fun resetDrag() {
        dragFrom = -1
        dragOffset = 0f
        dropTarget = -1
    }
    // An opaque panel: the screen under the sheet must not show through the list.
    Column(modifier.fillMaxSize().background(palette.surface.copy(alpha = 1f))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Both side slots share the weight, so the title sits in the middle of the sheet.
            TextButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus).weight(1f)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.queue_back_to_player),
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.queue_title),
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val countLabel = stringResource(R.string.queue_count, current.size)
                val readyLabel = if (current.readyCount > 0) {
                    stringResource(R.string.queue_ready_count, current.readyCount)
                } else ""
                Text(
                    listOf(countLabel, readyLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    color = palette.textSecondary,
                    fontSize = 11.sp
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (current.size > 1) {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }) {
                        Icon(
                            if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            stringResource(R.string.queue_search_action),
                            tint = if (searchOpen) palette.accent else palette.textSecondary
                        )
                    }
                }
                Box {
                    IconButton(onClick = { queueMenuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.queue_actions), tint = palette.textSecondary)
                    }
                    BeyGramPopover(expanded = queueMenuOpen, onDismissRequest = { queueMenuOpen = false }) {
                        BeyGramPopoverItem(
                            text = stringResource(R.string.queue_clear),
                            enabled = current.size > 1,
                            onClick = {
                                queueMenuOpen = false
                                PlaybackEngine.clearUpcoming()
                            }
                        )
                        BeyGramPopoverItem(
                            text = stringResource(R.string.queue_save_as_playlist),
                            enabled = current.rows.any { it.item.toPlaylistTrack() != null },
                            onClick = {
                                queueMenuOpen = false
                                saveQueueOpen = true
                            }
                        )
                    }
                }
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
        if (searchOpen) {
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.queue_search_hint), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = palette.textSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.queue_search_clear), tint = palette.textSecondary)
                        }
                    }
                },
                shape = styledShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (current.shuffleEnabled) {
            Text(
                stringResource(
                    if (current.playbackOrderApplied) R.string.queue_playback_order_hint
                    else R.string.queue_shuffled
                ),
                color = palette.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 56.dp)
        ) {
            if (visiblePlayed.isNotEmpty()) {
                item(key = "played-heading") { QueueSectionTitle(stringResource(R.string.queue_played)) }
                items(visiblePlayed, key = { "played:${it.item.occurrenceId}:${it.index}" }) { row ->
                    QueuePlayedRow(row) { PlaybackEngine.playQueueItem(row.item.key) }
                }
            }
            if (visibleCurrent != null) {
                item(key = "current-heading") {
                    QueueSectionTitle(stringResource(R.string.queue_now_playing))
                }
                item(key = "current-${visibleCurrent.item.occurrenceId}") {
                    QueueCurrentCard(
                        row = visibleCurrent,
                        showSourcePosition = current.shuffleEnabled && visibleCurrent.index >= 0,
                        onPlayPause = { PlaybackEngine.togglePlayPause() },
                        onShowInChat = visibleCurrent.item.origin?.let { origin ->
                            {
                                PlaybackEngine.requestShowInChat(origin)
                                onDismiss()
                            }
                        }
                    )
                }
            }
            if (!filtering || visibleUpcoming.isNotEmpty()) {
                item(key = "next-heading") {
                    QueueSectionTitle(stringResource(R.string.queue_next))
                }
            }
            if (filtering && visiblePlayed.isEmpty() && visibleCurrent == null && visibleUpcoming.isEmpty()) {
                item(key = "queue-search-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.queue_search_empty),
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { searchQuery = "" }, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(R.string.queue_search_clear))
                        }
                    }
                }
            } else if (!filtering && visibleUpcoming.isEmpty() && !loadingOlder) {
                item(key = "queue-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.queue_empty),
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (onSearch != null) {
                            TextButton(onClick = onSearch, modifier = Modifier.padding(top = 4.dp)) {
                                Text(stringResource(R.string.music_search_action))
                            }
                        }
                    }
                }
            }
            items(visibleUpcoming, key = { "${it.item.occurrenceId}:${it.index}" }) { row ->
                val swipeState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart && row.index >= 0) {
                            PlaybackEngine.removeFromQueue(row.index)
                            true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = swipeState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = dragFrom < 0,
                    backgroundContent = {
                        // Drawn only while the row is swiped: at rest the transparent row would show it under the menu button.
                        if (swipeState.targetValue != SwipeToDismissBoxValue.Settled || swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
                            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Rounded.Delete, stringResource(R.string.queue_remove), tint = palette.danger)
                            }
                        }
                    }
                ) {
                QueueUpcomingRow(
                    row = row,
                    reorderEnabled = !current.shuffleEnabled && !filtering,
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
                    onPlayNow = { PlaybackEngine.playQueueItem(row.item.key) },
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
            if (loadingOlder) {
                item(key = "queue-loading-page") {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.queue_loading_more), color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showJumpToCurrent,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) {
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .shadow(6.dp, styledShape(20.dp))
                    .clip(styledShape(20.dp))
                    .background(palette.accent, styledShape(20.dp))
                    .clickable(role = Role.Button) {
                        scope.launch {
                            listState.animateScrollToItem(if (playedRows.isEmpty()) 0 else playedRows.size + 1)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = palette.accentInk, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.queue_jump_to_current),
                    color = palette.accentInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        }
    }
}

@Composable
private fun QueuePlayedRow(row: QueueItemUi, onPlay: () -> Unit) {
    val palette = beyGramPalette()
    val artist = row.item.musicArtist()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(styledShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .alpha(0.6f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QueueArtwork(row.item, 36.dp, palette)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.item.musicTitle(),
                color = palette.textPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.isNotBlank()) {
                Text(
                    artist,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val queueSaveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

/** Asks for a name and saves the queue's music, in the current order, as a new playlist. */
@Composable
private fun SaveQueueDialog(rows: List<QueueItemUi>, onDismiss: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val defaultTitle = stringResource(R.string.queue_title)
    var title by remember { mutableStateOf(defaultTitle) }
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.queue_save_as_playlist),
        content = {
            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                singleLine = true
            )
        },
        confirmText = stringResource(R.string.queue_save_confirm),
        confirmEnabled = title.isNotBlank(),
        onConfirm = {
            val tracks = rows.mapNotNull { it.item.toPlaylistTrack() }
            val name = title.trim()
            onDismiss()
            // The dialog leaves composition at once, so the work runs in a scope that outlives it.
            queueSaveScope.launch {
                val repository = MusicLibraries.get(appContext)
                when (val created = repository.createPlaylist(name)) {
                    is MusicOutcome.Done -> {
                        when (val added = repository.addTracks(created.value.id, tracks)) {
                            is MusicOutcome.Done ->
                                Toast.makeText(appContext, appContext.getString(R.string.queue_saved_as_playlist), Toast.LENGTH_SHORT).show()
                            is MusicOutcome.Failed -> appContext.reportMusicOutcome(added)
                        }
                    }
                    is MusicOutcome.Failed -> appContext.reportMusicOutcome(created)
                }
            }
        },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = onDismiss
    )
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
    showSourcePosition: Boolean,
    onPlayPause: () -> Unit,
    onShowInChat: (() -> Unit)?
) {
    val palette = beyGramPalette()
    val controlIcon = when (row.status) {
        QueueItemStatus.CurrentPlaying, QueueItemStatus.CurrentBuffering -> Icons.Rounded.Pause
        QueueItemStatus.CurrentFailed, QueueItemStatus.CurrentEnded -> Icons.Rounded.Replay
        else -> Icons.Rounded.PlayArrow
    }
    val controlLabel = stringResource(
        when (row.status) {
            QueueItemStatus.CurrentPlaying, QueueItemStatus.CurrentBuffering -> R.string.common_pause
            QueueItemStatus.CurrentFailed -> R.string.player_retry
            QueueItemStatus.CurrentEnded -> R.string.player_replay
            else -> R.string.common_play
        }
    )
    val sourcePosition = if (showSourcePosition) {
        " · " + stringResource(R.string.queue_source_position, row.index + 1)
    } else ""
    // The landing place of the player's artwork and texts when the queue opens, and their start when it closes.
    val queueTargets = PlayerExpansion.queueTargets
    val title = row.item.musicTitle()
    val artist = row.item.musicArtist()
    val density = LocalDensity.current
    val localStyle = LocalTextStyle.current
    SideEffect {
        queueTargets.artworkRadiusPx = with(density) { 10.dp.toPx() }
        queueTargets.artworkBorder = palette.divider.copy(alpha = 0.5f)
        queueTargets.titleText = title
        queueTargets.titleStyle = localStyle.merge(
            TextStyle(color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        )
        queueTargets.artistText = artist
        queueTargets.artistStyle = localStyle.merge(TextStyle(color = palette.textSecondary, fontSize = 12.sp))
    }
    DisposableEffect(Unit) {
        onDispose {
            queueTargets.artwork = null
            queueTargets.title = null
            queueTargets.artist = null
        }
    }
    Surface(
        color = palette.accentSoft,
        shape = styledShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueArtwork(
                row.item,
                52.dp,
                palette,
                modifier = Modifier
                    .onGloballyPositioned { queueTargets.artwork = it }
                    .graphicsLayer { alpha = if (PlayerExpansion.queueSharedHidden(FlightRole.Artwork)) 0f else 1f }
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onGloballyPositioned { queueTargets.title = it }
                        .graphicsLayer { alpha = if (PlayerExpansion.queueSharedHidden(FlightRole.Title)) 0f else 1f }
                )
                if (artist.isNotBlank()) {
                    Text(
                        artist,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .onGloballyPositioned { queueTargets.artist = it }
                            .graphicsLayer { alpha = if (PlayerExpansion.queueSharedHidden(FlightRole.Artist)) 0f else 1f }
                    )
                }
                Text(
                    queueStatusText(row.status) + sourcePosition,
                    color = if (row.status == QueueItemStatus.CurrentFailed) palette.danger else palette.accentInk,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                Icon(controlIcon, controlLabel, tint = palette.accentInk)
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
    reorderEnabled: Boolean,
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
    onPlayNow: () -> Unit,
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
    val playNowLabel = stringResource(R.string.queue_play_now)
    val sourcePositionLabel = stringResource(R.string.queue_source_position, row.index + 1)
    val sourcePositionDescription = stringResource(R.string.queue_source_position_a11y, row.index + 1)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = QUEUE_ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
            .clip(styledShape(12.dp))
            .background(if (highlighted) palette.accentSoft.copy(alpha = 0.65f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reorderEnabled) {
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
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = sourcePositionDescription },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    sourcePositionLabel,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = playNowLabel, onClick = onPlayNow),
            contentAlignment = Alignment.Center
        ) { QueueArtwork(row.item, 40.dp, palette) }
        Spacer(Modifier.width(4.dp))
        Column(
            Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = playNowLabel, onClick = onPlayNow),
            verticalArrangement = Arrangement.Center
        ) {
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
                if (row.status == QueueItemStatus.Downloading || row.status == QueueItemStatus.Unavailable) {
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
            BeyGramPopover(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                BeyGramPopoverItem(
                    text = playNowLabel,
                    onClick = {
                        menuOpen = false
                        onPlayNow()
                    }
                )
                BeyGramPopoverItem(
                    text = stringResource(R.string.queue_play_next),
                    onClick = {
                        menuOpen = false
                        onPlayNext()
                    }
                )
                if (onShowInChat != null) {
                    BeyGramPopoverItem(
                        text = stringResource(R.string.media_show_in_chat),
                        onClick = {
                            menuOpen = false
                            onShowInChat()
                        }
                    )
                }
                BeyGramPopoverItem(
                    text = stringResource(R.string.queue_remove),
                    danger = true,
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
private fun QueueArtwork(item: PlaybackItem, size: Dp, palette: com.beykus.beygram.ui.components.BeyGramPalette, modifier: Modifier = Modifier) {
    // A provider row has no artwork of its own: its cover comes from the provider's list and is cached on its own.
    val providerCover = rememberProviderCover(ProviderTracks.byKey(item.key)?.coverUrl)
    TrackArtwork(
        artworkPath = item.artworkPath?.takeIf { it.isNotBlank() } ?: providerCover,
        audioPath = item.path.takeIf { it.isNotBlank() },
        modifier = modifier.size(size),
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
        QueueItemStatus.NotDownloaded -> R.string.queue_not_downloaded
        QueueItemStatus.Downloading -> R.string.queue_downloading
        QueueItemStatus.Ready -> R.string.queue_ready
        QueueItemStatus.Unavailable -> R.string.queue_unavailable
    }
)

internal fun filterQueueRows(rows: List<QueueItemUi>, query: String): List<QueueItemUi> {
    val needle = query.trim()
    if (needle.isEmpty()) return rows
    return rows.filter { row ->
        row.item.musicTitle().contains(needle, ignoreCase = true) ||
            row.item.musicArtist()?.contains(needle, ignoreCase = true) == true ||
            row.item.album?.contains(needle, ignoreCase = true) == true
    }
}

internal fun shouldShowJumpToCurrent(
    currentItemListIndex: Int,
    visibleIndices: List<Int>,
    queryBlank: Boolean,
    hasCurrent: Boolean,
): Boolean =
    hasCurrent &&
        queryBlank &&
        currentItemListIndex >= 0 &&
        visibleIndices.isNotEmpty() &&
        currentItemListIndex !in visibleIndices
