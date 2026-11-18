package com.beykus.beygram.ui.screens.playback

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.ContentDrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.corner
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/**
 * The full music player as one layer of this window, above the whole app. It replaces the modal sheet, because a
 * dialog window cannot share elements with the screen behind it. Nothing but the root layout is composed while the
 * player is collapsed.
 */
@Composable
internal fun PlayerExpansionHost() {
    val player = rememberMusicPlayerUi()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spec = rememberMotionSpec(MotionToken.PlayerExpand)
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val openEqualizer = LocalOpenEqualizer.current
    val openMusicSearch = LocalOpenMusicSearch.current
    val openMyMusic = LocalOpenMyMusic.current
    val keyboard = LocalSoftwareKeyboardController.current
    val phase = PlayerExpansion.phase
    DisposableEffect(scope) {
        PlayerExpansion.scope = scope
        onDispose { PlayerExpansion.hostDetached(scope) }
    }
    SideEffect {
        PlayerExpansion.motion = spec
        PlayerExpansion.density = density.density
        PlayerExpansion.statusTopPx = statusTopPx
    }
    LaunchedEffect(Unit) {
        MusicPlayerLauncher.openRequests.collect {
            if (PlaybackEngine.state.value.item?.isMusic == true) PlayerExpansion.open(null)
        }
    }
    LaunchedEffect(player == null) {
        if (player == null) PlayerExpansion.dismissImmediately()
    }
    LaunchedEffect(phase) {
        // A dialog used to take the keyboard away when the player opened; the overlay does it explicitly.
        if (phase == PlayerExpansionPhase.Expanding) keyboard?.hide()
    }
    val cover = rememberCoverTokens(
        artworkPath = player?.item?.artworkPath,
        audioPath = player?.item?.path?.takeIf { it.isNotBlank() },
        enabled = true,
        maxAmount = FULL_COVER_TINT,
        base = sheetSurfaceColor()
    )
    // Always composed, so the overlay's root coordinates exist before the player first opens.
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { PlayerExpansion.rootCoords = it }) {
        val current = player
        if (current == null || phase == PlayerExpansionPhase.Collapsed) return@BoxWithConstraints
        val full = fullPlayerRect(
            constraints.maxWidth.toFloat(),
            constraints.maxHeight.toFloat(),
            statusTopPx,
            density.density
        )
        PlayerOverlay(
            player = current,
            cover = cover,
            full = full,
            onOpenEqualizer = openEqualizer,
            onOpenMusicSearch = openMusicSearch,
            onOpenMyMusic = openMyMusic
        )
    }
}

/**
 * Wraps the app's own screen. While the player is open the screen under it is hidden from TalkBack, as the modal
 * dialog used to hide it.
 */
@Composable
internal fun PlayerModalUnderlay(content: @Composable () -> Unit) {
    val expanded = PlayerExpansion.phase == PlayerExpansionPhase.Expanded
    Box(Modifier.fillMaxSize().then(if (expanded) Modifier.clearAndSetSemantics {} else Modifier)) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerOverlay(
    player: MusicPlayerUi,
    cover: CoverTokens,
    full: Rect,
    onOpenEqualizer: (() -> Unit)?,
    onOpenMusicSearch: (() -> Unit)?,
    onOpenMyMusic: (() -> Unit)?
) {
    // Inside the overlay's subtree on purpose: it resets to the player whenever the overlay closes.
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.Player) }
    val density = LocalDensity.current
    val phase = PlayerExpansion.phase
    val sheetRadiusPx = with(density) { LocalDesignSystem.current.shapes.corner(28.dp).toPx() }
    val fullWidthDp = with(density) { full.width.toDp() }
    val fullHeightDp = with(density) { full.height.toDp() }
    val fullLeftPx = full.left.roundToInt()
    val fullTopPx = full.top.roundToInt()
    val scrim = MaterialTheme.colorScheme.scrim
    val playerTitle = stringResource(R.string.player_pane_title)
    val slopPx = with(density) { 16.dp.toPx() }
    val collapseConnection = remember { collapseNestedScroll() }
    val dragState = rememberDraggableState { PlayerExpansion.dragBy(it) }
    val fullDragEnabled = phase == PlayerExpansionPhase.Expanded ||
        (phase == PlayerExpansionPhase.Dragging && PlayerExpansion.dragOrigin == PlayerDragOrigin.Full)
    // Stays enabled through the whole back gesture (the phase is Dragging then) and during a move, so a back press
    // never falls through to the screen behind an open player.
    val backEnabled = panel == PlayerPanel.Player && phase != PlayerExpansionPhase.Collapsed

    // Composed before the content, so the queue's own back handler (added later) wins while the queue is open.
    PredictiveBackHandler(enabled = backEnabled) { events ->
        if (!PlayerExpansion.beginDrag(PlayerDragOrigin.Back, null)) return@PredictiveBackHandler
        try {
            events.collect { PlayerExpansion.backProgress(it.progress) }
            PlayerExpansion.collapse()
        } catch (e: CancellationException) {
            if (PlayerExpansion.phase == PlayerExpansionPhase.Dragging) {
                PlayerExpansion.open(PlayerExpansion.activeAnchorId)
            }
            throw e
        }
    }

    val onScrimTap: () -> Unit = {
        when (PlayerExpansion.phase) {
            PlayerExpansionPhase.Expanding,
            PlayerExpansionPhase.Expanded,
            PlayerExpansionPhase.Dragging -> PlayerExpansion.collapse()
            PlayerExpansionPhase.Collapsing,
            PlayerExpansionPhase.Collapsed -> Unit
        }
    }
    val onTransitionTap: (Offset) -> Unit = { offset ->
        // A tap inside the bar's own rect during the collapse brings the player back.
        val tapped = Offset(offset.x + full.left, offset.y + full.top)
        if (PlayerExpansion.phase == PlayerExpansionPhase.Collapsing &&
            PlayerExpansion.currentSource()?.container?.contains(tapped) == true
        ) {
            PlayerExpansion.open(PlayerExpansion.activeAnchorId)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val alpha = 0.32f * PlayerExpansion.progress * PlayerExpansion.overlayFade()
                if (alpha > 0f) drawRect(scrim.copy(alpha = alpha))
            }
            .consumeTaps(slopPx) { onScrimTap() }
            .clearAndSetSemantics {}
    ) {}
    Box(
        Modifier
            .offset { IntOffset(fullLeftPx, fullTopPx) }
            .size(fullWidthDp, fullHeightDp)
            .semantics { paneTitle = playerTitle }
            .drawWithContent { drawPlayerContainer(cover, full, sheetRadiusPx) }
    ) {
        CompositionLocalProvider(
            LocalBeyGramTokens provides cover.tokens,
            LocalContentColor provides cover.tokens.text
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = PlayerExpansion.progress
                        alpha = (if (PlayerExpansion.flights) ramp(p, 0.45f, 1f) else 1f) * PlayerExpansion.overlayFade()
                    }
                    .nestedScroll(collapseConnection)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = fullDragEnabled,
                        onDragStarted = { PlayerExpansion.beginDrag(PlayerDragOrigin.Full, null) },
                        onDragStopped = { velocity -> PlayerExpansion.endDrag(velocity) }
                    )
            ) {
                Box(Modifier.fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    BottomSheetDefaults.DragHandle(color = cover.tokens.muted)
                }
                MusicPlayerContent(
                    player = player,
                    panel = panel,
                    onPanel = { panel = it },
                    onCollapse = { PlayerExpansion.collapse() },
                    onLeave = { PlayerExpansion.dismissImmediately() },
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenMusicSearch = onOpenMusicSearch,
                    onOpenMyMusic = onOpenMyMusic,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // While the transition runs, nothing in the content may be tapped; the dragging finger owns its own gesture.
        if (phase == PlayerExpansionPhase.Expanding || phase == PlayerExpansionPhase.Collapsing) {
            Box(Modifier.fillMaxSize().consumeTaps(slopPx, onTransitionTap)) {}
        }
    }
    PlayerFlights(player = player, cover = cover)
}

/** Lets the content scroll, and hands the leftover of a downward scroll at the top to the player's drag. */
private fun collapseNestedScroll(): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (PlayerExpansion.phase == PlayerExpansionPhase.Dragging &&
            PlayerExpansion.dragOrigin == PlayerDragOrigin.Full && available.y < 0f
        ) {
            // Re-expand first, before the list scrolls.
            PlayerExpansion.dragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Only a finger, and only downward leftover: an upward leftover at the end of a list never collapses.
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        if (PlayerExpansion.phase == PlayerExpansionPhase.Expanded) PlayerExpansion.beginDrag(PlayerDragOrigin.Full, null)
        if (PlayerExpansion.phase != PlayerExpansionPhase.Dragging || PlayerExpansion.dragOrigin != PlayerDragOrigin.Full) {
            return Offset.Zero
        }
        PlayerExpansion.dragBy(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (PlayerExpansion.phase == PlayerExpansionPhase.Dragging && PlayerExpansion.dragOrigin == PlayerDragOrigin.Full) {
            PlayerExpansion.endDrag(available.y)
            return available
        }
        return Velocity.Zero
    }
}

/** Consumes every touch on this layer, and reports a tap: a touch that lifts without moving past [slopPx]. */
private fun Modifier.consumeTaps(slopPx: Float, onTap: (Offset) -> Unit): Modifier = pointerInput(slopPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var moved = false
        var released = false
        var last: Offset = down.position
        while (!released) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if ((change.position - down.position).getDistance() > slopPx) moved = true
            last = change.position
            released = !change.pressed
        }
        if (released && !moved) onTap(last)
    }
}

/** Draws the container at its growing rect, and the content through that rect, anchored at its top-left. */
private fun ContentDrawScope.drawPlayerContainer(cover: CoverTokens, full: Rect, sheetRadiusPx: Float) {
    val p = PlayerExpansion.progress
    val fade = PlayerExpansion.overlayFade()
    val src = PlayerExpansion.currentSource()
    val flying = PlayerExpansion.flights && src != null
    // The container is laid out once at the full rect, so its drawing space starts at the full rect's top-left.
    val rect = containerRectAt(p, src?.container, full).translate(-full.left, -full.top)
    val top = if (src != null) lerpFloat(src.containerRadius, sheetRadiusPx, p) else sheetRadiusPx
    val bottom = if (src != null) lerpFloat(src.containerRadius, 0f, p) else 0f
    val fill = if (src != null) lerp(src.containerColor, cover.surface, p) else cover.surface
    val path = roundedRectPath(rect, top, bottom)
    drawPath(path, color = fill, alpha = (if (flying) ramp(p, 0f, 0.25f) else 1f) * fade)
    clipPath(path) {
        withTransform({ translate(rect.left, rect.top) }) {
            this@drawPlayerContainer.drawContent()
        }
    }
}

private fun roundedRectPath(rect: Rect, top: Float, bottom: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect,
            topLeft = CornerRadius(top),
            topRight = CornerRadius(top),
            bottomRight = CornerRadius(bottom),
            bottomLeft = CornerRadius(bottom)
        )
    )
}
