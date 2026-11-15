package com.beykus.beygram.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** How the screen that is now shown relates to the one before it. */
enum class NavigationMove { None, Push, Pop, Tab }

/**
 * Remembers the order routes were opened in. Going back to a route that is already on the stack is a pop, and opening
 * any other route is a push. A tab root resets the stack, so switching tabs reads as [NavigationMove.Tab], not as a push.
 */
class NavigationHistory<T>(
    initial: T,
    private val isRoot: (T) -> Boolean,
    private val rootIndex: (T) -> Int = { 0 }
) {
    private val stack = mutableListOf(initial)
    private var move = NavigationMove.None
    private var pendingTabOrigin: T? = null
    private var hasPendingTabOrigin = false

    /** +1 when moving to a later tab in bar order, -1 when moving to an earlier tab. */
    var tabDirection: Int = 1
        private set

    /**
     * Marks the next transition to a root route as an explicit tab switch from [activeTabRoot], even when the
     * current route is a nested screen inside that tab (such as an open chat or search pane in a two-pane layout).
     */
    fun prepareTabSwitch(activeTabRoot: T) {
        pendingTabOrigin = activeTabRoot
        hasPendingTabOrigin = true
    }

    /** Records [target] and returns how it was reached. Asking again for the same target returns the same move. */
    fun visit(target: T): NavigationMove {
        val current = stack.last()
        if (current == target) {
            hasPendingTabOrigin = false
            pendingTabOrigin = null
            return move
        }
        val tabOrigin = pendingTabOrigin.takeIf { hasPendingTabOrigin }
        hasPendingTabOrigin = false
        pendingTabOrigin = null
        val previousIndex = stack.indexOfLast { it == target }
        move = when {
            isRoot(target) && (isRoot(current) || tabOrigin != null) -> NavigationMove.Tab
            isRoot(target) -> NavigationMove.Pop
            previousIndex >= 0 -> NavigationMove.Pop
            else -> NavigationMove.Push
        }
        if (move == NavigationMove.Tab) {
            val origin = if (isRoot(current)) current else tabOrigin ?: current
            tabDirection = if (rootIndex(target) >= rootIndex(origin)) 1 else -1
        }
        when {
            isRoot(target) -> {
                stack.clear()
                stack.add(target)
            }
            move == NavigationMove.Pop -> {
                // A direct jump back over multiple nested screens is still a pop.
                // Remove all screens above the existing target to avoid duplicate routes in the stack.
                stack.subList(previousIndex + 1, stack.size).clear()
            }
            else -> stack.add(target)
        }
        return move
    }
}

/** Base share of the screen width the underlying route shifts for parallax during a push or pop. */
private const val RouteParallaxFraction = 0.22f
private const val MaxRouteParallaxFraction = 0.33f

internal fun routeParallaxFraction(intensity: Float): Float =
    (RouteParallaxFraction * intensity.coerceAtLeast(0f)).coerceAtMost(MaxRouteParallaxFraction)

internal data class RouteSlideOffsets(
    val enterOffsetX: Int,
    val exitOffsetX: Int
)

internal fun routeSlideOffsets(
    move: NavigationMove,
    fullWidth: Int,
    intensity: Float,
    tabDirection: Int = 1,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
): RouteSlideOffsets {
    if (move == NavigationMove.None || intensity <= 0f) return RouteSlideOffsets(0, 0)
    val rtlSign = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    return when (move) {
        NavigationMove.Push -> {
            val parallax = (fullWidth * routeParallaxFraction(intensity)).roundToInt()
            RouteSlideOffsets(
                enterOffsetX = rtlSign * fullWidth,
                exitOffsetX = -rtlSign * parallax
            )
        }
        NavigationMove.Pop -> {
            val parallax = (fullWidth * routeParallaxFraction(intensity)).roundToInt()
            RouteSlideOffsets(
                enterOffsetX = -rtlSign * parallax,
                exitOffsetX = rtlSign * fullWidth
            )
        }
        NavigationMove.Tab -> {
            val dir = (if (tabDirection >= 0) 1 else -1) * rtlSign
            RouteSlideOffsets(
                enterOffsetX = dir * fullWidth,
                exitOffsetX = -dir * fullWidth
            )
        }
        NavigationMove.None -> RouteSlideOffsets(0, 0)
    }
}

/**
 * The transition for one [move].
 *
 * All transitions keep both screens opaque (no fade-to-empty gap and no semi-transparent double-text overlap):
 * - [NavigationMove.Push]: the incoming screen slides over the outgoing one from the trailing edge (`targetContentZIndex = 1f`)
 *   while the outgoing screen shifts toward the leading edge with subtle parallax.
 * - [NavigationMove.Pop]: the outgoing screen stays on top (`targetContentZIndex = -1f`) and slides off toward the trailing edge,
 *   revealing the previous screen returning from its parallax offset.
 * - [NavigationMove.Tab]: both tab roots slide edge-to-edge in tab-bar order ([tabDirection]), mirrored for [LayoutDirection.Rtl].
 * - [NavigationMove.None], instant specs (Off / reduced motion), or zero-intensity specs produce an immediate cut with
 *   no alpha or offset.
 */
fun routeContentTransform(
    move: NavigationMove,
    push: MotionSpec,
    pop: MotionSpec,
    tab: MotionSpec,
    tabDirection: Int = 1,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
): ContentTransform = when (move) {
    NavigationMove.Push -> slidePairTransform(move, push, zIndex = 1f, layoutDirection = layoutDirection)
    NavigationMove.Pop -> slidePairTransform(move, pop, zIndex = -1f, layoutDirection = layoutDirection)
    NavigationMove.Tab -> slidePairTransform(move, tab, zIndex = 0f, tabDirection = tabDirection, layoutDirection = layoutDirection)
    NavigationMove.None -> instantTransform()
}

private fun instantTransform(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None

private fun slidePairTransform(
    move: NavigationMove,
    spec: MotionSpec,
    zIndex: Float,
    tabDirection: Int = 1,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
): ContentTransform {
    if (spec.isInstant || spec.intensity <= 0f) return instantTransform()
    val animSpec = tween<IntOffset>(spec.durationMillis, easing = spec.easing)
    val enter = slideInHorizontally(animationSpec = animSpec) { fullWidth ->
        routeSlideOffsets(move, fullWidth, spec.intensity, tabDirection, layoutDirection).enterOffsetX
    }
    val exit = slideOutHorizontally(animationSpec = animSpec) { fullWidth ->
        routeSlideOffsets(move, fullWidth, spec.intensity, tabDirection, layoutDirection).exitOffsetX
    }
    return ContentTransform(enter, exit, targetContentZIndex = zIndex)
}

/**
 * Returns an [androidx.compose.animation.AnimatedContent] `transitionSpec` that always reads the latest [move],
 * motion specs, [tabDirection], and [layoutDirection], even when Compose evaluates `initialContentExit` from a
 * previously cached content entry during a mid-animation interruption (such as interrupting a push with a pop).
 */
@Composable
fun <S> rememberRouteTransitionSpec(
    move: NavigationMove,
    push: MotionSpec,
    pop: MotionSpec,
    tab: MotionSpec,
    tabDirection: Int = 1,
    layoutDirection: LayoutDirection = LocalLayoutDirection.current,
    resolveMove: (initial: S, target: S, latestMove: NavigationMove) -> NavigationMove = { _, _, latest -> latest }
): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val latestMove = rememberUpdatedState(move)
    val latestPush = rememberUpdatedState(push)
    val latestPop = rememberUpdatedState(pop)
    val latestTab = rememberUpdatedState(tab)
    val latestTabDirection = rememberUpdatedState(tabDirection)
    val latestLayoutDirection = rememberUpdatedState(layoutDirection)
    val latestResolveMove = rememberUpdatedState(resolveMove)
    return remember {
        {
            routeContentTransform(
                move = latestResolveMove.value(initialState, targetState, latestMove.value),
                push = latestPush.value,
                pop = latestPop.value,
                tab = latestTab.value,
                tabDirection = latestTabDirection.value,
                layoutDirection = latestLayoutDirection.value
            )
        }
    }
}
