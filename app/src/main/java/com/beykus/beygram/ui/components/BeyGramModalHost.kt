package com.beykus.beygram.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.beykus.beygram.ui.design.GlassBackdrop
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.LocalGlassBackdrop
import com.beykus.beygram.ui.design.glassBackdropSource
import com.beykus.beygram.ui.design.recordsGlassBackdrop
import com.beykus.beygram.ui.design.rememberGlassBackdrop
import com.beykus.beygram.ui.design.styledTopShape
import com.beykus.beygram.ui.design.usesGlassPanels
import kotlinx.coroutines.launch

/**
 * Where a [BeyGramSheet] or [BeyGramDialog] can be hosted so that it samples the real screen behind it.
 *
 * Material's `ModalBottomSheet` and Compose's `Dialog` draw in a window of their own. A backdrop recorded in the
 * activity window is not part of that window's content: `positionInWindow()` belongs to different coordinate spaces,
 * and drawing one window's render node inside another is not a supported path. Wrapping such a surface in
 * [GlassPanel] therefore produces an imitation, never the material.
 *
 * The surfaces that must show the material (sheets, the sheet-based sticker set and previews, dialogs and notices) are
 * instead drawn **inside the activity window**, above the routes, by [BeyGramModalHostOverlay]. The host records the
 * screen it covers into the window-level backdrop while it is open, so a panel samples exactly the content behind it.
 * Nothing is recorded while no modal is shown, and the overlay itself is drawn outside the recorded subtree, so a
 * panel never samples the layer it is being drawn into.
 *
 * The pattern is the one the chat's long-press sheet already uses
 * (`ChatActionsSheet`) and [SameWindowPopoverHost] uses for popovers; this class makes it shared instead of per-screen.
 */
@Stable
class BeyGramModalHost {
    internal val sheets = mutableStateListOf<SheetRoute>()
    internal var dialog by mutableStateOf<DialogRoute?>(null)

    /** True while any modal surface is on screen: the only time the window backdrop is worth recording. */
    val isOpen: Boolean get() = sheets.isNotEmpty() || dialog != null

    internal fun pushSheet(route: SheetRoute) {
        if (sheets.none { it === route }) sheets.add(route)
    }

    internal fun removeSheet(route: SheetRoute) {
        sheets.removeAll { it === route }
    }
}

/** The host of the current window. Null in previews and wherever no host is provided. */
val LocalBeyGramModalHost = staticCompositionLocalOf<BeyGramModalHost?> { null }

/** Creates the host of one window. */
@Composable
fun rememberBeyGramModalHost(): BeyGramModalHost = remember { BeyGramModalHost() }

/**
 * Provides [host] to the content and draws its modals above it.
 *
 * The content is recorded into the host's own [GlassBackdrop] only while a modal is open, so nothing is copied on
 * idle frames and the window-level backdrop that screens already use is not disturbed. While a modal is open the
 * content is also hidden from accessibility and cannot take focus, which is what a separate dialog window gives for
 * free; pointer input is stopped by the scrim, so a drag inside the modal still reaches it.
 *
 * The recorded subtree and the overlay are siblings: the panel is drawn after the content has been recorded for the
 * same frame, and it is outside the subtree it samples, so it can never feed itself.
 */
@Composable
fun BeyGramModalHostProvider(
    host: BeyGramModalHost,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val design = LocalDesignSystem.current
    val modalBackdrop = rememberGlassBackdrop()
    val open = host.isOpen
    // Only a style that really samples a backdrop pays for the recording, and only while something is open.
    val record = open && design.recordsGlassBackdrop
    CompositionLocalProvider(LocalBeyGramModalHost provides host) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (record) Modifier.glassBackdropSource(modalBackdrop) else Modifier)
                    .then(if (open) Modifier.clearAndSetSemantics {} else Modifier)
                    .then(if (open) Modifier.focusProperties { canFocus = false } else Modifier)
            ) {
                content()
            }
            BeyGramModalHostOverlay(host, modalBackdrop)
        }
    }
}

/**
 * Draws the modals of [host]: the top sheet first, then the dialog. Both are ordinary in-window composables, so every
 * material rule of the design system applies to them unchanged.
 */
@Composable
fun BeyGramModalHostOverlay(host: BeyGramModalHost, backdrop: GlassBackdrop?) {
    val topSheet = host.sheets.lastOrNull()
    if (topSheet != null) CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { SheetModal(topSheet) }
    val dialog = host.dialog
    if (dialog != null) CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { DialogModal(dialog) }
}

/** Values of a [BeyGramSheetState]. Mirrors the Material states so call sites read the same way. */
enum class BeyGramSheetValue { Hidden, Expanded }

/**
 * State of a hosted sheet: [isVisible], [currentValue], [show], [expand] and [hide] keep the meaning they have on
 * Material's `SheetState`, so a caller that waits for [isVisible] or reopens the sheet keeps working.
 * Partial expansion is not offered: every BeyGram sheet opens fully, as it always has.
 */
@Stable
class BeyGramSheetState internal constructor(
    /**
     * Material's sheet opens partially when the content is tall unless this is set. The hosted sheet always opens
     * fully; the flag is kept so the other styles keep exactly the behaviour their call sites asked for.
     */
    internal val skipPartiallyExpanded: Boolean
) {
    internal var expanded by mutableStateOf(true)

    /**
     * A veto for every dismissal, the same idea as Material's `confirmValueChange`: a sheet that owns an in-flight
     * operation must not disappear under a swipe, a tap outside or Back. It is read at the moment of the request, so a
     * caller can keep it up to date with `rememberUpdatedState`.
     */
    internal var canDismiss: () -> Boolean = { true }

    val isVisible: Boolean get() = expanded

    val currentValue: BeyGramSheetValue
        get() = if (expanded) BeyGramSheetValue.Expanded else BeyGramSheetValue.Hidden

    /** True when the sheet may be closed now. A dismissal that returns false does nothing. */
    fun dismissAllowed(): Boolean = canDismiss()

    suspend fun show() {
        expanded = true
    }

    suspend fun expand() {
        expanded = true
    }

    suspend fun hide() {
        if (canDismiss()) expanded = false
    }
}

/**
 * Remembers a [BeyGramSheetState].
 *
 * [canDismiss] is the sheet's veto for a swipe, a tap outside and Back. Pass it where the sheet must stay open while a
 * server or a store operation is in flight.
 */
@Composable
fun rememberBeyGramSheetState(
    skipPartiallyExpanded: Boolean = false,
    canDismiss: () -> Boolean = { true }
): BeyGramSheetState {
    val state = remember(skipPartiallyExpanded) { BeyGramSheetState(skipPartiallyExpanded) }
    state.canDismiss = canDismiss
    return state
}

/** One sheet registered with the host. Its lambdas are state, so a recomposing caller updates the live sheet. */
internal class SheetRoute(
    val state: BeyGramSheetState,
    content: @Composable ColumnScope.() -> Unit,
    onDismissRequest: () -> Unit,
    title: String?,
    modifier: Modifier,
    dragHandle: @Composable () -> Unit,
    scrollableContent: Boolean,
    bottomPadding: Dp,
    contentColor: Color
) {
    val content = mutableStateOf(content)
    val dismiss = mutableStateOf(onDismissRequest)
    val title = mutableStateOf(title)
    val modifier = mutableStateOf(modifier)
    val handle = mutableStateOf(dragHandle)
    val scrollable = mutableStateOf(scrollableContent)
    val bottom = mutableStateOf(bottomPadding)
    val tint = mutableStateOf(contentColor)
}

/** Creates the route of a hosted sheet, updating it in place so the host never sees a new identity. */
@Composable
internal fun rememberSheetRoute(
    state: BeyGramSheetState,
    content: @Composable ColumnScope.() -> Unit,
    onDismissRequest: () -> Unit,
    title: String?,
    modifier: Modifier,
    dragHandle: @Composable () -> Unit,
    scrollableContent: Boolean,
    bottomPadding: Dp,
    contentColor: Color
): SheetRoute {
    val latestContent = rememberUpdatedState(content)
    val latestDismiss = rememberUpdatedState(onDismissRequest)
    val latestHandle = rememberUpdatedState(dragHandle)
    val route = remember(state) {
        SheetRoute(
            state, latestContent.value, latestDismiss.value, title, modifier,
            latestHandle.value, scrollableContent, bottomPadding, contentColor
        )
    }
    route.content.value = latestContent.value
    route.dismiss.value = latestDismiss.value
    route.handle.value = latestHandle.value
    route.title.value = title
    route.modifier.value = modifier
    route.scrollable.value = scrollableContent
    route.bottom.value = bottomPadding
    route.tint.value = contentColor
    return route
}

/** Registers [route] with the host of the current window. */
@Composable
internal fun HostedSheet(route: SheetRoute) {
    val host = LocalBeyGramModalHost.current ?: return
    SideEffect { host.pushSheet(route) }
    DisposableEffect(host, route) { onDispose { host.removeSheet(route) } }
}

/** Fractions of the window a sheet may take; the rest keeps the screen visible around it. */
private const val SheetMaxHeightFraction = 0.92f
private const val SheetScrimAlpha = 0.32f
private const val DialogMaxHeightFraction = 0.8f
private const val DismissVelocityPxPerSecond = 1400f
private const val DismissDistanceFraction = 0.28f
private val DragHandleBand = 28.dp
private val SheetCorner = 28.dp
private val PanelMaxWidth = 560.dp

@Composable
private fun SheetModal(route: SheetRoute) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val dismiss = route.dismiss.value
    val state = route.state
    val visible = state.expanded
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // The sheet is bounded by the space that is actually left above the keyboard, not by the window: a tall sheet with
    // a text field would otherwise push its own top edge off the screen when the IME opens.
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
    val imePx = WindowInsets.ime.getBottom(density).toFloat()
    val availablePx = (screenHeightPx - imePx).coerceAtLeast(screenHeightPx * 0.3f)
    val maxHeight = with(density) { (availablePx * SheetMaxHeightFraction).toDp() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var panelHeight by remember { mutableStateOf(0) }

    // Every dismissal goes through the state's veto, so a sheet that owns an in-flight operation stays put under a
    // swipe, a tap outside and Back alike.
    val requestDismiss: () -> Unit = { if (state.dismissAllowed()) dismiss() }
    BackHandler(enabled = visible) { requestDismiss() }
    val scrim by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "beyGramSheetScrim"
    )
    if (!visible && scrim == 0f) return

    fun settle(velocity: Float) {
        val dismissByDistance = panelHeight > 0 && offset.value > panelHeight * DismissDistanceFraction
        val dismissByVelocity = velocity > DismissVelocityPxPerSecond
        if ((dismissByDistance || dismissByVelocity) && state.dismissAllowed()) {
            scope.launch {
                offset.animateTo(panelHeight.coerceAtLeast(1).toFloat(), tween(160))
                dismiss()
                offset.snapTo(0f)
            }
        } else {
            scope.launch { offset.animateTo(0f, tween(180)) }
        }
    }

    val dragState: (Float) -> Unit = { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
    }

    Box(Modifier.fillMaxSize().zIndex(30f)) {
        // A modal sheet owns the whole window: the scrim is the topmost hit target, so nothing under it scrolls or
        // takes a tap, and a tap on it closes the sheet the same way Material's scrim does.
        Box(
            Modifier
                .fillMaxSize()
                .testTag("beyGramSheetScrim")
                .background(Color.Black.copy(alpha = SheetScrimAlpha * scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = requestDismiss
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // The union, not a sum: with the keyboard open the IME inset already covers the navigation bar, so the
                // sheet must not pad both.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .graphicsLayer { translationY = offset.value }
        ) {
            val panelModifier = route.modifier.value
                .widthIn(max = PanelMaxWidth)
                .heightIn(max = maxHeight)
                .onSizeChanged { panelHeight = it.height }
                .then(
                    route.title.value?.let { heading -> Modifier.semantics { paneTitle = heading } }
                        ?: Modifier
                )
            if (design.usesGlassPanels) {
                GlassPanel(
                    design = design,
                    tint = palette.surface,
                    rim = palette.textSecondary,
                    // The recorded screen may leave gaps; the pane starts from the screen colour, not from sharp content.
                    backdropBase = palette.screen,
                    shape = styledTopShape(SheetCorner),
                    edge = GlassEdge.TopHairline,
                    lensOnFreeEdge = true,
                    modifier = panelModifier.testTag("beyGramGlassSheet")
                ) {
                    SheetBody(route, dragState, ::settle)
                }
            } else {
                Column(
                    panelModifier
                        .testTag("beyGramPlainSheet")
                        .clip(styledTopShape(SheetCorner))
                        .background(palette.surface)
                ) {
                    SheetBody(route, dragState, ::settle)
                }
            }
        }
    }
}

/**
 * The inner layout of a sheet: the drag handle, the optional heading and the content. Only the handle band starts a
 * drag, so a list or a scroll area inside the sheet keeps its own gestures.
 */
@Composable
private fun SheetBody(
    route: SheetRoute,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit
) {
    val palette = beyGramPalette()
    val title = route.title.value
    val scrollable = route.scrollable.value
    val bottom = route.bottom.value
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(bottom = bottom)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DragHandleBand)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> onDrag(delta) },
                    onDragStopped = { velocity -> onDragStopped(velocity) }
                ),
            contentAlignment = Alignment.Center
        ) {
            route.handle.value()
        }
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
            )
        }
        val columnScope = this
        CompositionLocalProvider(LocalContentColor provides route.tint.value) {
            val body = route.content.value
            body(columnScope)
        }
    }
}

/** One centred dialog registered with the host. */
internal class DialogRoute(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    paneTitle: String?,
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean
) {
    val dismiss = mutableStateOf(onDismissRequest)
    val body = mutableStateOf(content)
    val panelModifier = mutableStateOf(modifier)
    val panelShape = mutableStateOf(shape)
    val title = mutableStateOf(paneTitle)
    val backDismiss = mutableStateOf(dismissOnBackPress)
    val outsideDismiss = mutableStateOf(dismissOnClickOutside)
}

@Composable
internal fun rememberDialogRoute(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier,
    shape: Shape,
    paneTitle: String?,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
): DialogRoute {
    val latestDismiss = rememberUpdatedState(onDismissRequest)
    val latestContent = rememberUpdatedState(content)
    val route = remember {
        DialogRoute(
            latestDismiss.value, latestContent.value, modifier, shape, paneTitle,
            dismissOnBackPress, dismissOnClickOutside
        )
    }
    route.dismiss.value = latestDismiss.value
    route.body.value = latestContent.value
    route.panelModifier.value = modifier
    route.panelShape.value = shape
    route.title.value = paneTitle
    route.backDismiss.value = dismissOnBackPress
    route.outsideDismiss.value = dismissOnClickOutside
    return route
}

/** Registers [route] as the dialog of the current window. The dialog is drawn above the sheet. */
@Composable
internal fun HostedDialog(route: DialogRoute) {
    val host = LocalBeyGramModalHost.current ?: return
    SideEffect { host.dialog = route }
    DisposableEffect(host, route) {
        onDispose { if (host.dialog === route) host.dialog = null }
    }
}

private const val DialogScrimAlpha = 0.4f

@Composable
private fun DialogModal(route: DialogRoute) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val dismiss = route.dismiss.value
    val title = route.title.value
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
    val imePx = WindowInsets.ime.getBottom(density).toFloat()
    val availablePx = (screenHeightPx - imePx).coerceAtLeast(screenHeightPx * 0.3f)
    val maxHeight = with(density) { (availablePx * DialogMaxHeightFraction).toDp() }
    BackHandler(enabled = route.backDismiss.value) { dismiss() }
    Box(Modifier.fillMaxSize().zIndex(40f)) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("beyGramDialogScrim")
                .background(Color.Black.copy(alpha = DialogScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (route.outsideDismiss.value) dismiss() }
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime),
            contentAlignment = Alignment.Center
        ) {
            val panelModifier = route.panelModifier.value
                .padding(horizontal = 24.dp)
                .widthIn(max = PanelMaxWidth)
                .heightIn(max = maxHeight)
                .then(if (title != null) Modifier.semantics { paneTitle = title } else Modifier)
                // A tap inside the panel must not reach the scrim behind it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            if (design.usesGlassPanels) {
                GlassPanel(
                    design = design,
                    tint = palette.surface,
                    rim = palette.textSecondary,
                    backdropBase = palette.screen,
                    shape = route.panelShape.value,
                    modifier = panelModifier.testTag("beyGramGlassDialog")
                ) {
                    route.body.value()
                }
            } else {
                Column(
                    panelModifier
                        .testTag("beyGramPlainDialog")
                        .clip(route.panelShape.value)
                        .background(palette.surface)
                ) {
                    route.body.value()
                }
            }
        }
    }
}

/** The short rounded bar at the top of a sheet. It is decorative, so it has no semantics of its own. */
@Composable
fun BeyGramSheetHandle(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Box(
        modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(palette.textSecondary.copy(alpha = 0.4f))
    )
}