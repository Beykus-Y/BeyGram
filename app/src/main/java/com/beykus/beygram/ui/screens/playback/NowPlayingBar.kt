package com.beykus.beygram.ui.screens.playback

import com.beykus.beygram.ui.design.panelFill
import com.beykus.beygram.ui.design.PanelFloor
import com.beykus.beygram.ui.design.signatureChrome
import com.beykus.beygram.ui.design.HairlineEdge
import com.beykus.beygram.ui.design.GlassPanel
import androidx.compose.animation.AnimatedVisibility
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.playback.PLAYBACK_SPEED_OPTIONS
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackState
import com.beykus.beygram.playback.playbackSpeedFor
import com.beykus.beygram.playback.speedLabel
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.components.navigationBarColor
import com.beykus.beygram.ui.components.navigationSpecFor
import com.beykus.beygram.ui.design.TopBarVariant
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.chat.duration
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/** Цвета управления плеером: на светлом листе приложения и на тёмном фоне просмотрщика. */
internal class NowPlayingColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color
)

internal val ViewerNowPlayingColors = NowPlayingColors(
    primary = Color.White,
    secondary = Color(0xFFA39C95),
    accent = Color(0xFFCFC4FF),
    accentSoft = Color(0x33FFFFFF),
    accentInk = Color(0xFF1E1B2E)
)

/**
 * Opens the equalizer from the full player. The app root provides this once the Equalizer route exists; the
 * optional [NowPlayingBar] parameter stays available as an explicit override for previews/tests.
 */
val LocalOpenEqualizer = compositionLocalOf<(() -> Unit)?> { null }

/** Optional music search destination provided by the root navigation host. */
val LocalOpenMusicSearch = compositionLocalOf<(() -> Unit)?> { null }

/** Optional music library destination provided by the root navigation host. */
val LocalOpenMyMusic = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Мини-плеер 2.0: одна компактная полоса над нижним меню или composer. Слева artwork, отдельные Title/Artist,
 * затем Next (только если он реально доступен), Play/Pause и Close/Stop. Тап по основной части открывает полный
 * плеер, а кнопка Play — нет. Позиция читается отдельным листом и не перерисовывает всю полосу.
 *
 * [bottomInset] = true нужен на экранах без собственного нижнего меню или composer: тогда полоса сама держится
 * выше системной навигации и клавиатуры. В чате composer уже применяет IME inset, поэтому полоса не дублирует его.
 */
@Composable
fun NowPlayingBar(
    bottomInset: Boolean = false,
    hideWhenImeVisible: Boolean = bottomInset,
    onOpenEqualizer: (() -> Unit)? = null,
    /** True above the floating navigation capsule: the bar takes the capsule's margins, corners and material. */
    floatingDock: Boolean = false,
    /** True at the foot of the slim rail of a short window: a round cover with a progress ring replaces the bar. */
    railCover: Boolean = false
) {
    val providedOpenEqualizer = LocalOpenEqualizer.current
    val openEqualizer = onOpenEqualizer ?: providedOpenEqualizer
    val openMusicSearch = LocalOpenMusicSearch.current
    val openMyMusic = LocalOpenMyMusic.current
    val mini = rememberMiniPlayerUi()
    // Root and tab bars yield their space to the keyboard; the chat-integrated bar
    // keeps its own composer layout. Playback and the full-player sheet stay alive.
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val hiddenForIme = hideWhenImeVisible && keyboardVisible
    var lastMini by remember { mutableStateOf(mini) }
    SideEffect { if (mini != null) lastMini = mini }
    var fullPlayerOpen by rememberSaveable { mutableStateOf(false) }
    val enterSpec = rememberMotionSpec(MotionToken.SheetEnter)
    val exitSpec = rememberMotionSpec(MotionToken.SheetExit)

    LaunchedEffect(Unit) {
        MusicPlayerLauncher.openRequests.collect {
            if (PlaybackEngine.state.value.item != null) fullPlayerOpen = true
        }
    }
    LaunchedEffect(mini == null) {
        if (mini == null) fullPlayerOpen = false
    }

    if (railCover) {
        // The slim rail spends no height on a bar: a tap pauses or resumes, a long press opens the full player.
        val shown = mini ?: lastMini
        AnimatedVisibility(visible = mini != null && shouldShowMiniPlayer(true, hideWhenImeVisible, keyboardVisible)) {
            if (shown != null) RailMiniPlayerCover(shown, onOpen = { fullPlayerOpen = true })
        }
    } else AnimatedVisibility(
        visible = shouldShowMiniPlayer(mini != null, hideWhenImeVisible, keyboardVisible),
        // The bar only slides and fades. Instant specs (Off, reduced motion) skip both, so it appears on its first frame.
        enter = if (enterSpec.isInstant) EnterTransition.None else
            enterSpec.fadeEnter() + slideInVertically(enterSpec.animationSpec()) { (it / 2 * enterSpec.intensity).roundToInt() },
        exit = if (hiddenForIme || exitSpec.isInstant) ExitTransition.None else
            exitSpec.fadeExit() + slideOutVertically(exitSpec.animationSpec()) { (it / 2 * exitSpec.intensity).roundToInt() },
        modifier = Modifier.fillMaxWidth()
    ) {
        val shown = mini ?: lastMini
        if (shown != null) {
            MiniPlayerBar(
                ui = shown,
                bottomInset = bottomInset,
                floatingDock = floatingDock,
                onOpen = { fullPlayerOpen = true },
                // A long press is a shortcut to the library for people who never found it in the menus.
                onLongOpen = if (shown.isMusic) openMyMusic else null
            )
        }
    }

    val undoAvailable by PlaybackEngine.undoClose.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !railCover && mini == null && undoAvailable,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = Modifier.fillMaxWidth()
    ) {
        UndoCloseBar(bottomInset = bottomInset, floatingDock = floatingDock)
    }

    if (fullPlayerOpen) {
        if (mini?.isMusic == true) {
            MusicPlayerSheet(
                onDismiss = { fullPlayerOpen = false },
                onOpenEqualizer = openEqualizer,
                onOpenMusicSearch = openMusicSearch,
                onOpenMyMusic = openMyMusic
            )
        } else {
            VoiceNowPlayingSheet(onDismiss = { fullPlayerOpen = false })
        }
    }
}

@Composable
private fun MiniPlayerBar(ui: MiniPlayerUi, bottomInset: Boolean, floatingDock: Boolean, onOpen: () -> Unit, onLongOpen: (() -> Unit)? = null) {
    // Music takes its tint from the cover, or from the theme accent without one. Voice and circles keep the theme.
    val cover = rememberCoverTokens(
        artworkPath = ui.artworkPath,
        audioPath = ui.audioPath,
        enabled = ui.isMusic,
        maxAmount = MINI_COVER_TINT
    )
    // Signature keeps the chrome on the canvas: the cover tint reaches only the artwork and the progress line, so the
    // player never becomes the loudest element on the screen. Other styles tint the whole bar, as before.
    val inkOnly = LocalDesignSystem.current.components.topBar == TopBarVariant.Signature
    val chromeTokens = if (inkOnly) LocalBeyGramTokens.current else cover.tokens
    val artworkTokens = if (inkOnly) cover.tokens else null
    CompositionLocalProvider(LocalBeyGramTokens provides chromeTokens) {
        if (floatingDock) {
            MiniDockCard { MiniPlayerBarContent(ui, bottomInset, floatingDock = true, onOpen = onOpen, onLongOpen = onLongOpen, artworkTokens = artworkTokens) }
        } else {
            MiniPlayerBarContent(ui, bottomInset, floatingDock = false, onOpen = onOpen, onLongOpen = onLongOpen, artworkTokens = artworkTokens)
        }
    }
}

/** Runs [content] under the cover tint when [tokens] is set, and under the surrounding tokens otherwise. */
@Composable
private fun WithCoverTint(tokens: com.beykus.beygram.ui.theme.BeyGramTokens?, content: @Composable () -> Unit) {
    if (tokens == null) content() else CompositionLocalProvider(LocalBeyGramTokens provides tokens) { content() }
}

/**
 * The mini-player over a floating navigation capsule. It takes the capsule's side margins, corner radius, shadow and
 * material, so the two read as one dock. A pinned bar keeps its full-width strip instead.
 */
@Composable
private fun MiniDockCard(content: @Composable () -> Unit) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val spec = remember(design) { navigationSpecFor(design) }
    val glass = design.surfaces.glass
    val shape = RoundedCornerShape(spec.dockRadius)
    val tint = navigationBarColor(design, palette.surface, palette.screen)
    val outer = Modifier.fillMaxWidth().padding(horizontal = spec.horizontalMargin)
    if (glass != null) {
        GlassPanel(
            design = design,
            tint = tint,
            rim = palette.textSecondary,
            // Gaps in the sampled content show the screen colour, never the sharp content under the card.
            backdropBase = palette.screen,
            shape = shape,
            modifier = outer.shadow(glass.shadowElevation, shape)
        ) {
            content()
        }
    } else {
        Box(
            outer.shadow(spec.elevation, shape).clip(shape).background(tint)
                .then(if (spec.hairline) Modifier.border(1.dp, palette.divider, shape) else Modifier)
        ) {
            content()
        }
    }
}

@Composable
private fun MiniPlayerBarContent(
    ui: MiniPlayerUi,
    bottomInset: Boolean,
    floatingDock: Boolean,
    onOpen: () -> Unit,
    onLongOpen: (() -> Unit)? = null,
    artworkTokens: com.beykus.beygram.ui.theme.BeyGramTokens? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val openLabel = stringResource(R.string.player_mini_open)
    val textSpec = rememberMotionSpec(MotionToken.BannerChange)
    val playSpec = rememberMotionSpec(MotionToken.MicroFeedback)
    val subtitle = miniSubtitle(ui)
    // Above a rounded dock the top edge is clipped by the corners, so the track is inset by the radius to stay visible.
    val progressInset = if (floatingDock) navigationSpecFor(design).dockRadius else 0.dp
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (floatingDock || com.beykus.beygram.ui.screens.chat.LocalChatChromeOnGlass.current) Modifier else when (design.components.topBar) {
                    TopBarVariant.Elevated -> Modifier.shadow(design.surfaces.barElevation).background(palette.surface)
                    TopBarVariant.Glass -> Modifier.background(design.surfaces.panelFill(palette.surface, PanelFloor.Player))
                    TopBarVariant.Signature -> Modifier.signatureChrome(palette.screen, palette.divider, HairlineEdge.Top)
                    TopBarVariant.Flat -> Modifier.background(palette.surface)
                }
            )
            .then(
                if (bottomInset) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                else Modifier
            )
    ) {
        // On a pinned bar the track is the top edge. In a floating capsule it runs along the bottom instead, so it does
        // not compete with the glass rim on the top edge and is not cut by the rounded corners.
        if (!floatingDock) WithCoverTint(artworkTokens) { MiniPlayerProgress() }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(styledShape(12.dp))
                    .miniPlayerSwipe(
                        onPrevious = { PlaybackEngine.previous() },
                        onNext = { PlaybackEngine.next() }
                    )
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = openLabel,
                        onLongClickLabel = onLongOpen?.let { stringResource(R.string.player_mini_open_library) },
                        onLongClick = onLongOpen,
                        onClick = onOpen
                    )
                    .padding(start = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WithCoverTint(artworkTokens) {
                    val artPalette = beyGramPalette()
                    TrackArtwork(
                        artworkPath = ui.artworkPath,
                        audioPath = ui.audioPath,
                        modifier = Modifier.size(42.dp),
                        background = artPalette.accentSoft,
                        tint = artPalette.accentInk,
                        symbolSize = 20.dp,
                        cornerRadius = 10.dp,
                        border = artPalette.divider.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Crossfade(
                        targetState = MiniPlayerText(ui.title, subtitle),
                        animationSpec = textSpec.animationSpec(),
                        label = "miniPlayerText"
                    ) { text ->
                        Column {
                            Text(
                                text.title,
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text.subtitle,
                                    color = if (ui.failed) palette.danger else palette.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (ui.failed) {
                                    Icon(
                                        Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = palette.danger,
                                        modifier = Modifier.padding(start = 4.dp).size(13.dp)
                                    )
                                } else if (ui.intentPlaying && ui.buffering) {
                                    CircularProgressIndicator(
                                        Modifier.padding(start = 6.dp).size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = palette.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (ui.canNext) {
                IconButton(onClick = { PlaybackEngine.next() }) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), tint = palette.textSecondary)
                }
            }
            val playIcon = when {
                ui.failed || ui.ended -> Icons.Rounded.Replay
                ui.playing || ui.intentPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            }
            val playLabel = when {
                ui.failed -> stringResource(R.string.player_retry)
                ui.ended -> stringResource(R.string.player_replay)
                ui.playing || ui.intentPlaying -> stringResource(R.string.common_pause)
                else -> stringResource(R.string.common_play)
            }
            IconButton(
                onClick = { PlaybackEngine.togglePlayPause() },
                modifier = Modifier.semantics { contentDescription = playLabel }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(
                        targetState = playIcon,
                        animationSpec = playSpec.animationSpec(),
                        label = "miniPlayPause"
                    ) { icon ->
                        Icon(icon, null, tint = palette.accentInk)
                    }
                    if (ui.intentPlaying && ui.buffering) {
                        CircularProgressIndicator(
                            Modifier.size(26.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent.copy(alpha = 0.55f)
                        )
                    }
                }
            }
            // Close is always there, also while music plays; a slip is cheap because «Вернуть» brings the queue back.
            IconButton(onClick = { PlaybackEngine.close() }) {
                Icon(Icons.Rounded.Close, stringResource(R.string.player_stop), tint = palette.textSecondary)
            }
        }
        if (floatingDock) WithCoverTint(artworkTokens) { MiniPlayerProgress(Modifier.padding(horizontal = progressInset)) }
    }
}

/** Round 40 dp cover with a progress ring for the slim navigation rail: tap = play/pause, long press = full player. */
@Composable
private fun RailMiniPlayerCover(ui: MiniPlayerUi, onOpen: () -> Unit) {
    val cover = rememberCoverTokens(
        artworkPath = ui.artworkPath,
        audioPath = ui.audioPath,
        enabled = ui.isMusic,
        maxAmount = MINI_COVER_TINT
    )
    val playing = ui.playing || ui.intentPlaying
    val label = stringResource(if (playing) R.string.common_pause else R.string.common_play)
    val openLabel = stringResource(R.string.player_mini_open)
    val position = rememberPlaybackPositionUi()
    CompositionLocalProvider(LocalBeyGramTokens provides cover.tokens) {
        val palette = beyGramPalette()
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = label,
                    onLongClickLabel = openLabel,
                    onLongClick = onOpen,
                    onClick = { PlaybackEngine.togglePlayPause() }
                ),
            contentAlignment = Alignment.Center
        ) {
            TrackArtwork(
                artworkPath = ui.artworkPath,
                audioPath = ui.audioPath,
                modifier = Modifier.size(36.dp),
                background = palette.accentSoft,
                tint = palette.accentInk,
                symbolSize = 18.dp,
                cornerRadius = 18.dp,
                border = palette.divider.copy(alpha = 0.55f)
            )
            Canvas(Modifier.size(44.dp)) {
                val stroke = 2.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(palette.divider, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                if (position.fraction > 0f) {
                    drawArc(
                        palette.accent, -90f, 360f * position.fraction.coerceIn(0f, 1f), false, topLeft, arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            if (!playing) {
                Icon(Icons.Rounded.PlayArrow, null, tint = palette.textPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Shown for a few seconds after the mini-player was closed: the queue and the place are still there to bring back. */
@Composable
private fun UndoCloseBar(bottomInset: Boolean, floatingDock: Boolean) {
    val palette = beyGramPalette()
    val context = androidx.compose.ui.platform.LocalContext.current
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.player_closed),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { PlaybackEngine.undoClose(context) }) {
                Text(
                    stringResource(R.string.player_closed_undo),
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    // The same card as the mini-player and the dock (glass included), so closing does not swap it for a flat strip.
    Column(
        Modifier.then(
            if (bottomInset) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier
        )
    ) {
        if (floatingDock) MiniDockCard(row) else Box(Modifier.fillMaxWidth().background(palette.surface)) { row() }
    }
}

@Composable
private fun miniSubtitle(ui: MiniPlayerUi): String {
    if (ui.failed) return stringResource(R.string.player_failed)
    if (!ui.isMusic) return duration(ui.durationSeconds)
    if (ui.artist.isNotBlank()) return ui.artist
    if (ui.album.isNotBlank()) return ui.album
    if (ui.durationSeconds > 0) return duration(ui.durationSeconds)
    return stringResource(R.string.media_kind_audio)
}

private data class MiniPlayerText(val title: String, val subtitle: String)

/** Real playback progress, not buffering. Buffered data is a faint underlay so the two are distinguishable. */
@Composable
private fun MiniPlayerProgress(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val position = rememberPlaybackPositionUi()
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawLine(palette.divider, start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), strokeWidth = size.height, cap = StrokeCap.Butt)
        if (position.bufferedFraction > 0f) {
            drawLine(
                palette.accent.copy(alpha = 0.22f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width * position.bufferedFraction, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Butt
            )
        }
        if (position.fraction > 0f) {
            drawLine(
                palette.accent,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width * position.fraction, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Butt
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceNowPlayingSheet(onDismiss: () -> Unit) {
    val state by PlaybackEngine.state.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val item = state.item ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetSurfaceColor()
    ) {
        NowPlayingControls(
            state = state,
            item = item,
            colors = NowPlayingColors(palette.textPrimary, palette.textSecondary, palette.accent, palette.accentSoft, palette.accentInk),
            enabled = true,
            onPlayPause = { PlaybackEngine.togglePlayPause() },
            modifier = Modifier.nestedScroll(SheetOverscrollGuard)
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
            item.origin?.let { origin ->
                TextButton(onClick = { onDismiss(); PlaybackEngine.requestShowInChat(origin) }) {
                    Text(stringResource(R.string.media_show_in_chat), color = palette.accent)
                }
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = palette.textSecondary)
            }
        }
    }
}

/**
 * Управление файлом: название, перемотка, пауза и скорость. Используется и в полном плеере, и в просмотрщике.
 * [enabled] = false, когда файл не играет в общем плеере: перемотка и скорость относятся к другому файлу, поэтому выключены.
 */
@Composable
internal fun NowPlayingControls(
    state: PlaybackState,
    item: PlaybackItem,
    colors: NowPlayingColors,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TrackArtwork(
            artworkPath = item.artworkPath,
            audioPath = item.path.takeIf { item.kind == MediaKind.Audio },
            modifier = Modifier.size(144.dp),
            background = colors.accentSoft, tint = colors.accent, symbolSize = 56.dp,
            cornerRadius = 16.dp
        )
        Spacer(Modifier.height(16.dp))
        Text(item.title, color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (state.failed) Text(stringResource(R.string.media_audio_play_failed), color = colors.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        // The seek is sent when the finger is lifted: a seek on every movement made each step of a drag reopen the stream.
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        val totalMs = if (state.durationMs > 0L) state.durationMs else item.durationSeconds * 1000L
        Slider(
            value = dragFraction ?: state.fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { PlaybackEngine.seekTo(it, fallbackDurationMs = item.durationSeconds * 1000L) }
                dragFraction = null
            },
            enabled = enabled && (state.durationMs > 0L || item.durationSeconds > 0),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shownSeconds = dragFraction?.let { (it * totalMs / 1000).toInt() } ?: (state.positionMs / 1000).toInt()
            Text(duration(shownSeconds), color = colors.secondary, fontSize = 12.sp)
            Text(duration(totalSeconds(state)), color = colors.secondary, fontSize = 12.sp)
        }
        Box(
            Modifier.padding(top = 12.dp).size(64.dp).clip(CircleShape).background(colors.accent)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.playing || state.intentPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (state.playing || state.intentPlaying) R.string.common_pause else R.string.common_play),
                tint = colors.accentInk,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(stringResource(R.string.player_speed), color = colors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        val selectedSpeed = playbackSpeedFor(item.kind, state.musicSpeed, state.voiceSpeed)
        // BG Mix has no playback speed, so the chips stay off while it plays music instead of changing only the saved value.
        val speedEnabled = enabled && !(state.bgMixActive && item.kind == MediaKind.Audio)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp).alpha(if (speedEnabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                SpeedChip(
                    speed = speed,
                    selected = selectedSpeed == speed,
                    enabled = speedEnabled,
                    colors = colors,
                    onSelect = {
                        if (item.kind == MediaKind.Audio) PlaybackEngine.setMusicSpeed(speed) else PlaybackEngine.setVoiceSpeed(speed)
                    }
                )
            }
        }
    }
}

/** Полная длина в секундах: из плеера, если он её знает, иначе из сообщения. */
private fun totalSeconds(state: PlaybackState): Int =
    if (state.durationMs > 0) (state.durationMs / 1000).toInt() else state.item?.durationSeconds ?: 0

@Composable
private fun SpeedChip(speed: Float, selected: Boolean, enabled: Boolean, colors: NowPlayingColors, onSelect: () -> Unit) {
    Text(
        stringResource(R.string.player_speed_option, speedLabel(speed)),
        color = if (selected) colors.accentInk else colors.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(styledShape(16.dp))
            .background(if (selected) colors.accent else colors.accentSoft)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}


/** A swipe to the left plays the next track, to the right the previous one, as in other music players. */
private fun Modifier.miniPlayerSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total <= -threshold -> {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                        onNext()
                    }
                    total >= threshold -> {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                        onPrevious()
                    }
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
            onHorizontalDrag = { change, amount ->
                change.consume()
                total += amount
            }
        )
    }
}
