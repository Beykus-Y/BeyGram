package com.beykus.beygram.ui.screens.chats

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** Gap between the search pill and the actions group in the collapsed row. */
private val ChatsHeaderActionGap = 8.dp

/** Collapse state of the phone chat list header: one source of truth, 0 = expanded, [rangePx] = collapsed. */
@Stable
internal class ChatsHeaderCollapseState(initialOffsetPx: Float = 0f) {
    var offsetPx by mutableFloatStateOf(initialOffsetPx)

    /** Written by the header layout from measured sizes; plain field, not observed. */
    var rangePx: Float = 0f

    val progress: Float get() = if (rangePx <= 0f) 0f else (offsetPx / rangePx).coerceIn(0f, 1f)

    /** The collapse in px the header shows now (observed: reads [offsetPx]). */
    val currentPx: Float get() = if (rangePx <= 0f) 0f else offsetPx.coerceIn(0f, rangePx)

    /** The collapse in px used by the header's last measure; plain field, written by the layout. */
    var appliedPx: Float = 0f

    companion object {
        val Saver: Saver<ChatsHeaderCollapseState, Float> = androidx.compose.runtime.saveable.Saver(
            save = { it.offsetPx },
            restore = { ChatsHeaderCollapseState(it) }
        )
    }
}

/** Hermite ease from [edge0] (0) to [edge1] (1); an empty range is a step at the edge. */
internal fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Geometry of the header at a given progress. All values in px, relative to the header content box (after its padding). */
internal data class ChatsHeaderGeometry(
    val topBlockHeight: Float, // height used by title row + search (the part that collapses)
    val searchY: Float,
    val searchWidth: Float,
    val actionsY: Float,
    val titleTranslationY: Float,
    val titleScale: Float,
    val titleAlpha: Float,
    val rangePx: Float
)

/**
 * width = content width; titleH = measured title block height (overline+title); actionsW/actionsH = actions group size;
 * searchH = search pill height; gap = design.mainScreen.headerGap in px; actionGap = 8dp in px.
 */
internal fun chatsHeaderGeometry(
    progress: Float,
    width: Float,
    titleH: Float,
    actionsW: Float,
    actionsH: Float,
    searchH: Float,
    gap: Float,
    actionGap: Float
): ChatsHeaderGeometry {
    val p = progress.coerceIn(0f, 1f)
    val row1 = maxOf(titleH, actionsH)
    val expanded = row1 + gap + searchH
    val compact = maxOf(searchH, actionsH)
    val range = (expanded - compact).coerceAtLeast(0f)
    val top = expanded - range * p                       // linear: follows the finger 1:1
    val searchY0 = row1 + gap
    val searchY1 = (compact - searchH) / 2f
    val searchY = lerpF(searchY0, searchY1, p)
    val actionsY = lerpF((row1 - actionsH) / 2f, (compact - actionsH) / 2f, p)
    // The pill must be fully narrowed before its top enters the actions' vertical band: solve searchY(p) == actionsY(p) + actionsH.
    val a0 = (row1 - actionsH) / 2f + actionsH
    val a1 = (compact - actionsH) / 2f + actionsH
    val denom = (searchY0 - searchY1) - (a0 - a1)
    val pc = if (denom > 0f) ((searchY0 - a0) / denom).coerceIn(0.05f, 1f) else 1f
    val narrow = smoothStep(0.2f * pc, pc, p)
    val searchWidth = lerpF(width, (width - actionsW - actionGap).coerceAtLeast(0f), narrow)
    val scale = lerpF(1f, 0.8f, smoothStep(0f, 0.35f, p))
    // Title stays above the pill: its (scaled, bottom-anchored) bottom never goes below searchY - gap.
    val titleBottom = (row1 + titleH) / 2f               // title is vertically centred in row1
    val titleTranslationY = minOf(0f, searchY - gap - titleBottom)
    val alpha = 1f - smoothStep(0.4f, 1f, p)
    return ChatsHeaderGeometry(top, searchY, searchWidth, actionsY, titleTranslationY, scale, alpha, range)
}

/**
 * Moves the phone header with the list's own scroll. While the finger moves the list, the header takes the scroll first;
 * the list moves once the header has reached its resting place in that direction. A gesture that ends between the two
 * resting places settles to the nearer one.
 */
internal class ChatsHeaderScrollConnection(
    private val state: ChatsHeaderCollapseState,
    private val listState: () -> LazyListState,
    private val hysteresisPx: Float,
    /** True while the archive pull is out: a pull that retracts takes the scroll before the header does. */
    private val pullActive: () -> Boolean = { false }
) : NestedScrollConnection {
    private var lastDir = 0          // -1 collapsing (finger up), +1 expanding (finger down), 0 = fresh gesture
    private var reversePx = 0f
    /** Bumped by every user scroll and every animation: only the newest one writes the offset. */
    private var owner = 0
    /** True while the newest owner is a move to the top, which settling must not reverse. */
    private var toTop = false
    /** Owner id of the settle animation in progress, or -1. */
    private var settlingOwner = -1

    /** Spec of the settle and of the move to the top; null = instant (reduced motion). */
    var settleSpec: AnimationSpec<Float>? = null

    fun resetGesture() {
        lastDir = 0
        reversePx = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        val range = state.rangePx
        if (dy == 0f || range <= 0f) return Offset.Zero
        // A user scroll takes the header back from any animation that is still running.
        owner++
        toTop = false
        if (dy < 0f && pullActive()) return Offset.Zero
        val ls = listState()
        val dir = if (dy < 0f) -1 else 1
        val atTop = ls.firstVisibleItemIndex == 0 && ls.firstVisibleItemScrollOffset == 0
        if (lastDir != 0 && dir != lastDir && !(dir > 0 && atTop)) {
            // Small reversals inside one gesture do not move the header; the list scrolls instead.
            reversePx += abs(dy)
            if (reversePx < hysteresisPx) return Offset.Zero
        }
        lastDir = dir
        reversePx = 0f
        val old = state.offsetPx.coerceIn(0f, range)
        val next = if (dy < 0f) {
            if (!ls.canScrollForward && old <= 0f) return Offset.Zero   // a list that fits on screen never collapses
            (old - dy).coerceAtMost(range)
        } else {
            (old - dy).coerceAtLeast(0f)
        }
        state.offsetPx = next
        return Offset(0f, old - next)   // consumed: negative while collapsing, positive while expanding
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        resetGesture()
        settle()
        return Velocity.Zero
    }

    /**
     * Ends a gesture at the nearer resting place. At rest, the beginning of the list always shows the expanded header
     * (#950): when the list itself has not moved, the collapse is undone. A move to the top already running is left to finish.
     */
    suspend fun settle() {
        val range = state.rangePx
        val start = state.offsetPx
        val ls = listState()
        val atTop = ls.firstVisibleItemIndex == 0 && ls.firstVisibleItemScrollOffset == 0
        // A settle the newest owner already runs is not restarted (the fling end and the scroll end both ask for one).
        if (toTop || settlingOwner == owner || range <= 0f || start <= 0f || (start >= range && !atTop)) return
        val id = ++owner
        settlingOwner = id
        try {
            animateTo(if (atTop || start / range < 0.5f) 0f else range, id)
        } finally {
            if (settlingOwner == id) settlingOwner = -1
        }
    }

    /** Moves the header to the top: a tap on the title, a folder switch, or a list that fits on screen. */
    suspend fun animateToTop() {
        val id = ++owner
        toTop = true
        try {
            animateTo(0f, id)
        } finally {
            if (owner == id) toTop = false
        }
    }

    private suspend fun animateTo(target: Float, id: Int) {
        val spec = settleSpec
        val start = state.offsetPx
        if (spec == null || start == target) {
            if (id == owner) state.offsetPx = target
            return
        }
        animate(start, target, animationSpec = spec) { value, _ -> if (id == owner) state.offsetPx = value }
    }
}

/**
 * The title block, the actions and the search pill of the phone chat list header, laid out from one collapse state.
 * Each slot sits in its own Box, so every slot is exactly one measurable (the pattern of BubbleLayout).
 */
@Composable
internal fun CollapsingChatsHeaderLayout(
    collapse: ChatsHeaderCollapseState,
    gap: Dp,
    title: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    search: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(content = { Box { title() }; Box { actions() }; Box { search() } }, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actionsPlaceable = measurables[1].measure(loose)
        val titlePlaceable = measurables[0].measure(loose.copy(maxWidth = (width - actionsPlaceable.width).coerceAtLeast(0)))
        // The pill is one line of text, so its height does not depend on its width: the intrinsic height is enough here.
        val searchIntrinsicHeight = measurables[2].minIntrinsicHeight(width)
        val gapPx = gap.toPx()
        val actionGapPx = ChatsHeaderActionGap.toPx()
        val w = width.toFloat()
        val aw = actionsPlaceable.width.toFloat()
        val ah = actionsPlaceable.height.toFloat()
        val th = titlePlaceable.height.toFloat()
        val sh = searchIntrinsicHeight.toFloat()
        // The range comes from the sizes alone. It is set before the progress is read, so a new range never uses a stale one.
        collapse.rangePx = chatsHeaderGeometry(0f, w, th, aw, ah, sh, gapPx, actionGapPx).rangePx
        val g = chatsHeaderGeometry(collapse.progress, w, th, aw, ah, sh, gapPx, actionGapPx)
        collapse.appliedPx = g.rangePx * collapse.progress
        val pivotX = if (layoutDirection == LayoutDirection.Rtl) 1f else 0f
        val searchWidth = g.searchWidth.roundToInt()
        val searchPlaceable = measurables[2].measure(
            Constraints(minWidth = searchWidth, maxWidth = searchWidth, minHeight = 0, maxHeight = constraints.maxHeight)
        )
        val height = g.topBlockHeight.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val titleTop = ((maxOf(th, ah) - th) / 2f).roundToInt()
        layout(width, height) {
            actionsPlaceable.placeRelative(width - actionsPlaceable.width, g.actionsY.roundToInt())
            searchPlaceable.placeRelative(0, g.searchY.roundToInt())
            // Once the title has faded out it is not placed at all: it is neither drawn nor hit.
            if (g.titleAlpha > 0.01f) {
                titlePlaceable.placeRelativeWithLayer(0, titleTop) {
                    translationY = g.titleTranslationY
                    scaleX = g.titleScale
                    scaleY = g.titleScale
                    alpha = g.titleAlpha
                    // The pivot is the bottom edge, so the scaled title keeps the bottom that the translation placed above the pill.
                    transformOrigin = TransformOrigin(pivotX, 1f)
                }
            }
        }
    }
}
