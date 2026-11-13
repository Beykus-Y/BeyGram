package com.beykus.beygram.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
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

    /** +1 when moving to a later tab in bar order, -1 when moving to an earlier tab. */
    var tabDirection: Int = 1
        private set

    /** Records [target] and returns how it was reached. Asking again for the same target returns the same move. */
    fun visit(target: T): NavigationMove {
        val current = stack.last()
        if (current == target) return move
        val previousIndex = stack.indexOfLast { it == target }
        move = when {
            isRoot(target) && isRoot(current) -> NavigationMove.Tab
            isRoot(target) -> NavigationMove.Pop
            previousIndex >= 0 -> NavigationMove.Pop
            else -> NavigationMove.Push
        }
        if (move == NavigationMove.Tab) {
            tabDirection = if (rootIndex(target) >= rootIndex(current)) 1 else -1
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

/**
 * The transition for one [move].
 *
 * All transitions keep both screens opaque (no fade-to-empty gap and no semi-transparent double-text overlap):
 * - [NavigationMove.Push]: the incoming screen slides over the outgoing one from the right (`targetContentZIndex = 1f`)
 *   while the outgoing screen shifts left with subtle parallax.
 * - [NavigationMove.Pop]: the outgoing screen stays on top (`targetContentZIndex = -1f`) and slides off to the right,
 *   revealing the previous screen returning from its parallax offset.
 * - [NavigationMove.Tab]: both tab roots slide edge-to-edge in tab-bar order ([tabDirection]).
 * - [NavigationMove.None], instant specs (Off / reduced motion), or zero-intensity specs produce an immediate cut with
 *   no alpha or offset.
 */
fun routeContentTransform(
    move: NavigationMove,
    push: MotionSpec,
    pop: MotionSpec,
    tab: MotionSpec,
    tabDirection: Int = 1
): ContentTransform = when (move) {
    NavigationMove.Push -> pushTransform(push)
    NavigationMove.Pop -> popTransform(pop)
    NavigationMove.Tab -> tabSlideTransform(tab, tabDirection)
    NavigationMove.None -> instantTransform()
}

private fun instantTransform(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None

private fun pushTransform(spec: MotionSpec): ContentTransform {
    if (spec.isInstant || spec.intensity <= 0f) return instantTransform()
    val animSpec = tween<IntOffset>(spec.durationMillis, easing = spec.easing)
    val parallax = routeParallaxFraction(spec.intensity)
    val enter = slideInHorizontally(animationSpec = animSpec, initialOffsetX = { it })
    val exit = slideOutHorizontally(
        animationSpec = animSpec,
        targetOffsetX = { fullWidth -> -(fullWidth * parallax).roundToInt() }
    )
    return ContentTransform(enter, exit, targetContentZIndex = 1f)
}

private fun popTransform(spec: MotionSpec): ContentTransform {
    if (spec.isInstant || spec.intensity <= 0f) return instantTransform()
    val animSpec = tween<IntOffset>(spec.durationMillis, easing = spec.easing)
    val parallax = routeParallaxFraction(spec.intensity)
    val enter = slideInHorizontally(
        animationSpec = animSpec,
        initialOffsetX = { fullWidth -> -(fullWidth * parallax).roundToInt() }
    )
    val exit = slideOutHorizontally(animationSpec = animSpec, targetOffsetX = { it })
    return ContentTransform(enter, exit, targetContentZIndex = -1f)
}

private fun tabSlideTransform(spec: MotionSpec, direction: Int): ContentTransform {
    if (spec.isInstant || spec.intensity <= 0f) return instantTransform()
    val dir = if (direction >= 0) 1 else -1
    val animSpec = tween<IntOffset>(spec.durationMillis, easing = spec.easing)
    val enter = slideInHorizontally(animationSpec = animSpec, initialOffsetX = { dir * it })
    val exit = slideOutHorizontally(animationSpec = animSpec, targetOffsetX = { -dir * it })
    return ContentTransform(enter, exit, targetContentZIndex = 0f)
}
