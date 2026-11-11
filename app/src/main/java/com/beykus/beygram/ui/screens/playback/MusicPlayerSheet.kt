package com.beykus.beygram.ui.screens.playback

import androidx.activity.compose.BackHandler
import com.beykus.beygram.ui.design.styledShape
import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import com.beykus.beygram.media.bgbeat.BgBeatSection
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackRepeatMode
import com.beykus.beygram.playback.SLEEP_TIMER_MAX_MINUTES
import com.beykus.beygram.playback.SLEEP_TIMER_MINUTES
import com.beykus.beygram.playback.SleepTimerState
import com.beykus.beygram.playback.sleepTimerRemainingMinutes
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.accessibility.systemReducedMotion
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

private const val TRANSPORT_MOTION_MS = 150
private const val SEEK_STEP_MS = 5_000L
private val QUEUE_ROW_HEIGHT = 76.dp

private enum class PlayerPanel { Player, Queue }

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

/** The full player sheet. Its tokens come from the cover, so every row inside follows the tint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverPlayerSheet(
    cover: CoverTokens,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalBeyGramTokens provides cover.tokens) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = cover.surface,
            contentColor = cover.tokens.text,
            dragHandle = { BottomSheetDefaults.DragHandle(color = cover.tokens.muted) }
        ) {
            content()
        }
    }
}

/**
 * Large now-playing surface. The same composable owns the queue panel so the sheet is not recreated when the user
 * moves between the player and the queue, and the source title/artwork are resolved from the same account origin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicPlayerSheet(
    onDismiss: () -> Unit,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenMusicSearch: (() -> Unit)? = null,
    onOpenMyMusic: (() -> Unit)? = null
) {
    val player = rememberMusicPlayerUi()
    LaunchedEffect(player == null) { if (player == null) onDismiss() }
    val current = player ?: return
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.Player) }
    val source = rememberPlaybackSourceLabel(
        origin = current.item.origin,
        isProviderTrack = current.item.key.startsWith(YANDEX_TRACK_KEY_PREFIX)
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cover = rememberCoverTokens(
        artworkPath = current.item.artworkPath,
        audioPath = current.item.path.takeIf { it.isNotBlank() },
        enabled = true,
        maxAmount = FULL_COVER_TINT,
        base = sheetSurfaceColor()
    )
    BackHandler(enabled = panel == PlayerPanel.Queue) { panel = PlayerPanel.Player }
    CoverPlayerSheet(cover = cover, onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .nestedScroll(SheetOverscrollGuard)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
        ) {
            when (panel) {
                PlayerPanel.Player -> {
                    PlayerHeader(
                        player = current,
                        source = source,
                        onCollapse = onDismiss,
                        onOpenSource = current.item.origin?.let { origin -> { PlaybackEngine.requestShowInChat(origin); onDismiss() } },
                        onOpenMusicSearch = onOpenMusicSearch?.let { open -> { onDismiss(); open() } },
                        onOpenMyMusic = onOpenMyMusic?.let { open -> { onDismiss(); open() } }
                    )
                    PlayerBody(
                        modifier = Modifier.weight(1f),
                        player = current,
                        onOpenQueue = { panel = PlayerPanel.Queue },
                        onOpenEqualizer = onOpenEqualizer?.let { open ->
                            {
                                // Close the modal sheet before navigating to the equalizer route; otherwise it stays
                                // on top of the new screen and Back returns to an already-open player.
                                onDismiss()
                                open()
                            }
                        },
                        onShowInChat = current.item.origin?.let { origin -> { PlaybackEngine.requestShowInChat(origin); onDismiss() } }
                    )
                }

                PlayerPanel.Queue -> QueuePanel(
                    source = source,
                    onBack = { panel = PlayerPanel.Player },
                    onDismiss = onDismiss,
                    onSearch = onOpenMusicSearch?.let { open ->
                        {
                            onDismiss()
                            open()
                        }
                    }
                )
            }
        }
    }
}

/** Minutes left on the running sleep timer, refreshed every half minute. The one counter both the menu and the status line read. */
@Composable
private fun rememberSleepMinutes(sleepTimer: SleepTimerState?): Int {
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
private fun sleepTimerStatusText(sleepTimer: SleepTimerState, minutesRemaining: Int): String =
    if (sleepTimer.untilTrackEnd) stringResource(R.string.player_sleep_status_track_end)
    else stringResource(R.string.player_sleep_remaining, minutesRemaining)

@Composable
private fun PlayerHeader(
    player: MusicPlayerUi,
    source: PlaybackSourceLabel,
    onCollapse: () -> Unit,
    onOpenSource: (() -> Unit)?,
    onOpenMusicSearch: (() -> Unit)?,
    onOpenMyMusic: (() -> Unit)?
) {
    val palette = beyGramPalette()
    var speedMenu by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    val sleepTimer by PlaybackEngine.sleepTimer.collectAsStateWithLifecycle()
    val minutesRemaining = rememberSleepMinutes(sleepTimer)
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
                .clip(styledShape(12.dp))
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
        if (onOpenMusicSearch != null) IconButton(onClick = onOpenMusicSearch) {
            Icon(Icons.Rounded.Search, stringResource(R.string.music_search_action), tint = palette.textSecondary)
        }
        Box {
            IconButton(onClick = { speedMenu = true }) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.player_menu), tint = palette.textSecondary)
            }
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                if (onOpenMyMusic != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_library_title), color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = palette.accent) },
                        onClick = {
                            speedMenu = false
                            onOpenMyMusic()
                        }
                    )
                    HorizontalDivider(color = palette.divider)
                }
                Text(
                    stringResource(R.string.player_speed_title),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (player.bgMix) {
                    Text(
                        stringResource(R.string.player_speed_bgmix_hint),
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
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
                        enabled = !player.bgMix,
                        onClick = {
                            PlaybackEngine.setMusicSpeed(speed)
                            speedMenu = false
                        }
                    )
                }
                HorizontalDivider(color = palette.divider)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.player_sleep_timer), color = palette.textPrimary)
                            sleepTimer?.let { timer ->
                                Text(
                                    sleepTimerStatusText(timer, minutesRemaining),
                                    color = palette.textSecondary, fontSize = 11.sp
                                )
                            }
                        }
                    },
                    onClick = {
                        speedMenu = false
                        sleepDialog = true
                    }
                )
            }
        }
    }
    if (sleepDialog) {
        var customOpen by remember { mutableStateOf(false) }
        if (customOpen) {
            var text by remember { mutableStateOf("") }
            val minutes = text.toIntOrNull()
            val valid = minutes != null && minutes in 1..SLEEP_TIMER_MAX_MINUTES
            AlertDialog(
                onDismissRequest = { customOpen = false },
                containerColor = palette.surface,
                title = { Text(stringResource(R.string.player_sleep_custom_title), color = palette.textPrimary) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = text,
                        onValueChange = { value -> text = value.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.player_sleep_custom_label, SLEEP_TIMER_MAX_MINUTES)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = valid,
                        onClick = {
                            PlaybackEngine.setSleepTimer(minutes)
                            customOpen = false
                            sleepDialog = false
                        }
                    ) { Text(stringResource(R.string.player_sleep_custom_set), color = palette.accentInk) }
                },
                dismissButton = {
                    TextButton(onClick = { customOpen = false }) {
                        Text(stringResource(R.string.common_cancel), color = palette.textSecondary)
                    }
                }
            )
        }
        AlertDialog(
            onDismissRequest = { sleepDialog = false },
            containerColor = palette.surface,
            title = { Text(stringResource(R.string.player_sleep_timer), color = palette.textPrimary) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SLEEP_TIMER_MINUTES.sorted().forEach { duration ->
                        TextButton(
                            onClick = {
                                PlaybackEngine.setSleepTimer(duration)
                                sleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.player_sleep_in_minutes, duration), color = palette.accentInk)
                        }
                    }
                    TextButton(
                        onClick = {
                            PlaybackEngine.setSleepTimerUntilTrackEnd()
                            sleepDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.player_sleep_until_track_end), color = palette.accentInk)
                    }
                    TextButton(
                        onClick = { customOpen = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.player_sleep_custom), color = palette.accentInk)
                    }
                    if (sleepTimer != null) {
                        HorizontalDivider(color = palette.divider)
                        TextButton(
                            onClick = {
                                PlaybackEngine.extendSleepTimer(SLEEP_EXTEND_MINUTES)
                                sleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.player_sleep_extend, SLEEP_EXTEND_MINUTES), color = palette.accentInk)
                        }
                        TextButton(
                            onClick = {
                                PlaybackEngine.cancelSleepTimer()
                                sleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.player_sleep_cancel_plain), color = palette.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sleepDialog = false }) {
                    Text(stringResource(R.string.common_close), color = palette.textSecondary)
                }
            }
        )
    }
}

private const val SLEEP_EXTEND_MINUTES = 15

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
                        // Center the artwork and controls when they fit; the scroll container still fills
                        // the viewport and scrolls on short screens or at larger system font scales.
                        .align(Alignment.Center)
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
    // The position is read only inside the provider, which the wave calls while drawing. The rest of the details
    // (title, controls, actions) therefore does not recompose four times a second.
    val positionState = rememberPlaybackPositionState()
    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    val playedProvider = {
        val position = positionState.value
        val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
        if (duration > 0L) (position.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    TrackMetadata(player, reduced)
    PlayerSeekSection(player)
    if (player.bgMix) BgMixTransitionLine()
    Spacer(Modifier.height(6.dp))
    TransportControls(player, reduced)
    Spacer(Modifier.height(14.dp))
    BgBeatSection(
        item = player.item,
        playbackFractionProvider = playedProvider,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
    Spacer(Modifier.height(10.dp))
    PlayerActions(player.item, onOpenQueue, onOpenEqualizer, onShowInChat)
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
            .shadow(6.dp, styledShape(20.dp), clip = false)
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
    val sleepTimer by PlaybackEngine.sleepTimer.collectAsStateWithLifecycle()
    val minutesRemaining = rememberSleepMinutes(sleepTimer)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

        player.waitingForNetwork -> Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.accent)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_waiting_network), color = palette.textSecondary, fontSize = 13.sp)
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
    val activeSpeed = player.speed.takeIf { it != 1f }?.let { stringResource(R.string.player_speed_option, speedLabel(it)) }
    val sleepText = sleepTimer?.let { sleepTimerStatusText(it, minutesRemaining) }
    val statusParts = listOfNotNull(activeSpeed, sleepText)
    if (statusParts.isNotEmpty()) {
        Text(
            statusParts.joinToString(" · "),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    if (player.bgMix || player.bgMixPreparing) {
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
                .padding(top = 4.dp)
                .clip(styledShape(6.dp))
                .clickable(role = Role.Button) { explain = true }
                .semantics { contentDescription = markDescription }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        if (explain) AlertDialog(
            onDismissRequest = { explain = false },
            containerColor = palette.surface,
            title = { Text(stringResource(R.string.player_bgmix_badge), color = palette.textPrimary) },
            text = {
                Text(
                    stringResource(if (player.bgMix) R.string.player_bgmix_explain else R.string.player_bgmix_preparing_explain),
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { explain = false }) {
                    Text(stringResource(R.string.common_close), color = palette.textSecondary)
                }
            }
        )
    }
    }
}

@Composable
private fun PlayerSeekSection(player: MusicPlayerUi) {
    // The drag lives here, so the time labels follow the finger as well as the thumb.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = rememberPlaybackPositionUi()
    val fallbackDurationMs = player.item.durationSeconds.coerceAtLeast(0) * 1000L
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val smooth = rememberSmoothPositionMs(position.positionMs, duration, player.playing, player.speed)
    PlayerSeekBar(
        fallbackDurationSeconds = player.item.durationSeconds,
        contentLabel = stringResource(R.string.player_progress),
        dragging = dragging,
        onDragging = { dragging = it },
        smoothMs = smooth
    )
    PlayerTimeRow(fallbackDurationSeconds = player.item.durationSeconds, dragFraction = dragging, smoothMs = smooth)
}

/**
 * The playhead between two position ticks. The engine reports the position four times a second, which makes the thumb
 * of a short track move in steps; while the sound plays, the position is carried forward every frame at the playback speed
 * and corrected by each new tick.
 */
@Composable
private fun rememberSmoothPositionMs(positionMs: Long, durationMs: Long, playing: Boolean, speed: Float): State<Long> {
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
    fallbackDurationSeconds: Int,
    contentLabel: String,
    dragging: Float?,
    onDragging: (Float?) -> Unit,
    smoothMs: State<Long>
) {
    val palette = beyGramPalette()
    val position = rememberPlaybackPositionUi()
    val fallbackDurationMs = (fallbackDurationSeconds.coerceAtLeast(0) * 1000L)
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    val enabled = duration > 0L
    val played = dragging ?: if (duration > 0L) (smoothMs.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
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
                    var current: Float = fractionAt(down.position.x)
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
                    onDragging(null)
                    seekTo(current)
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
private fun PlayerTimeRow(fallbackDurationSeconds: Int, dragFraction: Float?, smoothMs: State<Long>) {
    val palette = beyGramPalette()
    val position = rememberPlaybackPositionUi()
    val fallbackDurationMs = (fallbackDurationSeconds.coerceAtLeast(0) * 1000L)
    val duration = position.durationMs.takeIf { it > 0L } ?: fallbackDurationMs
    // Whole seconds only, so the labels recompose once a second even though the playhead moves every frame.
    val playedSecond by remember(smoothMs) { derivedStateOf { smoothMs.value / 1_000L } }
    // While the thumb is dragged, the labels show the place under the finger, not the place the music is at.
    val shownMs = dragFraction?.let { (it * duration).toLong() } ?: (playedSecond * 1_000L)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackClock(shownMs), color = if (dragFraction != null) palette.accent else palette.textSecondary, fontSize = 12.sp)
            Text(playbackRemaining(shownMs, duration), color = palette.textSecondary, fontSize = 12.sp)
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
    item: PlaybackItem,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: (() -> Unit)?,
    onShowInChat: (() -> Unit)?
) {
    val queueLabel = stringResource(R.string.player_queue)
    val moreLabel = stringResource(R.string.player_more)
    val equalizerLabel = stringResource(R.string.player_equalizer)
    val showInChatLabel = stringResource(R.string.media_show_in_chat)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Heart and add need a Telegram origin and a loaded library for this account; both stay hidden until then.
    // Items of a chat with protected content are never kept in the library, so they stay hidden too. The chat is only
    // known for the selected account; otherwise the buttons stay.
    val originChat = rememberOriginChat(item.origin)
    val protectedChat = originChat?.hasProtectedContent == true
    val track = remember(item.occurrenceId, item.origin) { item.toPlaylistTrack() }?.takeUnless { protectedChat }
    val library = rememberMusicLibrary(item.accountId)
    var addOpen by remember(item.occurrenceId) { mutableStateOf(false) }
    // The picked track, timecode and source are fixed when the button is tapped. Nothing here is keyed to the track, so
    // the picker stays open when the player moves on to the next track.
    var pendingShare by remember { mutableStateOf<PendingTrackShare?>(null) }
    val shareLabel = stringResource(R.string.player_share_timecode)
    // Sharing sends from the chat source the player uses. A track from a chat is offered only once that chat is known
    // not to protect its content; a chat that cannot be checked here gets no share.
    val shareSource = LocalPlaybackRepositorySource.current
    val shareAllowed = shareSource != null &&
        canShareTrack(completedShareItem(item), originUnprotected = item.origin == null || originChat?.hasProtectedContent == false)
    val favorite = track != null && containsTrack(library?.favorites, track)
    val favoriteLabel = stringResource(R.string.music_favorite_label)
    val favoriteOnState = stringResource(R.string.music_favorite_state_on)
    val favoriteOffState = stringResource(R.string.music_favorite_state_off)
    val addLabel = stringResource(R.string.music_playlist_label)
    val addToProfile = LocalAddProfileAudio.current
    val addToProfileLabel = stringResource(R.string.player_add_to_profile)
    val addToProfileDone = stringResource(R.string.player_add_to_profile_done)
    // The shared player may be bound to another account; its file ids mean nothing to the active one.
    val activeAccount = PlaybackEngine.activeAccount()
    val actions = buildList {
        if (track != null && library != null) {
            add(PlayerActionUi(
                icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                label = favoriteLabel,
                onClick = { scope.launch { context.reportMusicOutcome(MusicLibraries.get(context).toggleFavorite(track)) } },
                active = favorite,
                stateDescription = if (favorite) favoriteOnState else favoriteOffState,
                primary = true
            ))
            add(PlayerActionUi(Icons.Rounded.Add, addLabel, onClick = { addOpen = true }, primary = true))
        }
        // Only a music file the active account can see from a chat is a Telegram profile track. A track that already
        // plays from a profile is in a profile, so the action would only add a copy.
        if (addToProfile != null && item.isMusic && item.fileId != null && !item.systemPrivate && !protectedChat &&
            !item.key.startsWith(PROFILE_AUDIO_KEY_PREFIX) && (activeAccount == null || item.accountId == activeAccount)
        ) add(PlayerActionUi(Icons.Rounded.PersonAdd, addToProfileLabel, onClick = {
            scope.launch {
                addToProfile(item)
                    .onSuccess { Toast.makeText(context, addToProfileDone, Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, it.userMessage(context, R.string.player_add_to_profile_failed), Toast.LENGTH_SHORT).show() }
            }
        }))
        add(PlayerActionUi(Icons.Rounded.QueueMusic, queueLabel, onOpenQueue, primary = true))
        if (shareAllowed) {
            add(PlayerActionUi(Icons.Rounded.Share, shareLabel, onClick = {
                shareSource?.let { source ->
                    pendingShare = PendingTrackShare(completedShareItem(item), shareTimecode(PlaybackEngine.state.value.positionMs), source)
                }
            }))
        }
        if (onOpenEqualizer != null) add(PlayerActionUi(Icons.Rounded.Equalizer, equalizerLabel, onOpenEqualizer))
        if (onShowInChat != null) add(PlayerActionUi(Icons.Rounded.ChatBubble, showInChatLabel, onShowInChat))
    }
    if (actions.isEmpty()) return
    // Three frequent actions stay under the controls. The rare ones live in "More" with their full labels, so no
    // caption is cut off on a narrow screen.
    val frequent = actions.filter { it.primary }
    val rare = actions.filterNot { it.primary }
    var moreOpen by remember { mutableStateOf(false) }
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        frequent.forEach { action ->
            PlayerAction(action, Modifier.weight(1f))
        }
        if (rare.isNotEmpty()) Box(Modifier.weight(1f)) {
            PlayerAction(
                PlayerActionUi(Icons.Rounded.MoreHoriz, moreLabel, onClick = { moreOpen = true }),
                Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                rare.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label, color = palette.textPrimary) },
                        leadingIcon = { Icon(action.icon, null, tint = palette.accent) },
                        onClick = {
                            moreOpen = false
                            action.onClick()
                        }
                    )
                }
            }
        }
    }
    if (addOpen && track != null) AddToPlaylistSheet(accountId = item.accountId, track = track, onDismiss = { addOpen = false })
    pendingShare?.let { share ->
        ShareTrackDialog(
            repository = share.source.repository,
            item = share.item,
            caption = share.caption,
            onClose = { pendingShare = null }
        )
    }
}

private data class PlayerActionUi(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val active: Boolean = false,
    val stateDescription: String? = null,
    /** Shown under the controls; the others go to the "More" menu. */
    val primary: Boolean = false
)

@Composable
private fun PlayerAction(action: PlayerActionUi, modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val described = action.stateDescription
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(styledShape(14.dp))
            .clickable(role = Role.Button, onClick = action.onClick)
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
    onSearch: (() -> Unit)? = null
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
    // Tracks that already played stay reachable above the current one; with shuffle there is no meaningful "before".
    val playedRows = if (current.shuffleEnabled || current.currentIndex <= 0) emptyList() else current.rows.take(current.currentIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (playedRows.isEmpty()) 0 else playedRows.size + 1)
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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) {
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
                    maxLines = 1
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
            Box {
                IconButton(onClick = { queueMenuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.queue_actions), tint = palette.textSecondary)
                }
                DropdownMenu(expanded = queueMenuOpen, onDismissRequest = { queueMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.queue_clear)) },
                        enabled = current.size > 1,
                        onClick = {
                            queueMenuOpen = false
                            PlaybackEngine.clearUpcoming()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.queue_save_as_playlist)) },
                        enabled = current.rows.any { it.item.toPlaylistTrack() != null },
                        onClick = {
                            queueMenuOpen = false
                            saveQueueOpen = true
                        }
                    )
                }
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
                stringResource(
                    if (current.playbackOrderApplied) R.string.queue_playback_order_hint
                    else R.string.queue_shuffled
                ),
                color = palette.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)
        ) {
            if (playedRows.isNotEmpty()) {
                item(key = "played-heading") { QueueSectionTitle(stringResource(R.string.queue_played)) }
                items(playedRows, key = { "played:${it.item.occurrenceId}:${it.index}" }) { row ->
                    QueuePlayedRow(row) { PlaybackEngine.playQueueItem(row.item.key) }
                }
            }
            item(key = "current-heading") {
                QueueSectionTitle(stringResource(R.string.queue_now_playing))
            }
            item(key = "current-${current.current?.item?.occurrenceId}") {
                current.current?.let { row ->
                    QueueCurrentCard(
                        row = row,
                        showSourcePosition = current.shuffleEnabled && row.index >= 0,
                        onPlayPause = { PlaybackEngine.togglePlayPause() },
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
            if (current.upcoming.isEmpty() && !loadingOlder) {
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
            items(current.upcoming, key = { "${it.item.occurrenceId}:${it.index}" }) { row ->
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
                        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.queue_remove), tint = palette.danger)
                        }
                    }
                ) {
                QueueUpcomingRow(
                    row = row,
                    reorderEnabled = !current.shuffleEnabled,
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
    }
}

@Composable
private fun QueuePlayedRow(row: QueueItemUi, onPlay: () -> Unit) {
    val palette = beyGramPalette()
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
        Text(
            row.item.title,
            color = palette.textPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private val queueSaveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

/** Asks for a name and saves the queue's music, in the current order, as a new playlist. */
@Composable
private fun SaveQueueDialog(rows: List<QueueItemUi>, onDismiss: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val defaultTitle = stringResource(R.string.queue_title)
    var title by remember { mutableStateOf(defaultTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.queue_save_as_playlist)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
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
                }
            ) { Text(stringResource(R.string.queue_save_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
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
    Surface(
        color = palette.accentSoft,
        shape = styledShape(16.dp),
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
                    text = { Text(playNowLabel) },
                    onClick = {
                        menuOpen = false
                        onPlayNow()
                    }
                )
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
