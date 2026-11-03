package com.beykus.beygram.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlin.math.roundToInt

/** How the screen that is now shown relates to the one before it. */
enum class NavigationMove { None, Push, Pop, Tab }

/**
 * Remembers the order routes were opened in. Going back to a route that is already on the stack is a pop, and opening
 * any other route is a push. A tab root resets the stack, so switching tabs reads as [NavigationMove.Tab], not as a push.
 */
class NavigationHistory<T>(initial: T, private val isRoot: (T) -> Boolean) {
    private val stack = mutableListOf(initial)
    private var move = NavigationMove.None

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

/** Share of the screen width a push or pop slides across at full intensity. */
private const val RouteSlideFraction = 0.12f

/** Share of a route transition spent on the outgoing screen; the incoming one starts only when that share is over. */
private const val RouteExitShare = 0.4f

/**
 * The transition for one [move]. A push slides in from the right and a pop from the left; tab switches fade through.
 * Both run in sequence, never as a crossfade: the outgoing screen leaves first and the incoming one starts after it, so
 * two screens are never readable at the same time (a crossfade left Contacts over Calls and Appearance over Settings
 * half-transparent). An instant spec gives a cut with no alpha or offset, so reduced motion and the Off preset show the
 * new screen on its first frame. Intensity scales only the slide distance.
 */
fun routeContentTransform(
    move: NavigationMove,
    push: MotionSpec,
    pop: MotionSpec,
    tab: MotionSpec
): ContentTransform = when (move) {
    NavigationMove.Push -> slideTransform(push, direction = 1)
    NavigationMove.Pop -> slideTransform(pop, direction = -1)
    NavigationMove.Tab, NavigationMove.None -> fadeThrough(tab)
}

/** Splits one token's duration into the exit part and the enter part, which follows it without overlap. */
private class RouteTimeline(spec: MotionSpec) {
    val exitMillis: Int = (spec.durationMillis * RouteExitShare).roundToInt().coerceAtLeast(1)
    val enterMillis: Int = (spec.durationMillis - exitMillis).coerceAtLeast(1)
}

private fun slideTransform(spec: MotionSpec, direction: Int): ContentTransform {
    if (spec.isInstant) return EnterTransition.None togetherWith ExitTransition.None
    val timeline = RouteTimeline(spec)
    val travel = { fullWidth: Int -> direction * (fullWidth * RouteSlideFraction * spec.intensity).roundToInt() }
    val enter = slideInHorizontally(
        animationSpec = tween(timeline.enterMillis, delayMillis = timeline.exitMillis, easing = spec.easing),
        initialOffsetX = travel
    ) + fadeIn(animationSpec = tween(timeline.enterMillis, delayMillis = timeline.exitMillis, easing = spec.easing))
    val exit = slideOutHorizontally(
        animationSpec = tween(timeline.exitMillis, easing = FastOutLinearInEasing),
        targetOffsetX = { -travel(it) }
    ) + fadeOut(animationSpec = tween(timeline.exitMillis, easing = FastOutLinearInEasing))
    return enter togetherWith exit
}

/**
 * Tab switches have no direction, so they fade through: the outgoing screen fades out first and the incoming one fades in
 * only after it has gone, instead of two half-transparent screens drawn over each other.
 */
private fun fadeThrough(spec: MotionSpec): ContentTransform {
    if (spec.isInstant) return EnterTransition.None togetherWith ExitTransition.None
    val timeline = RouteTimeline(spec)
    val enter = fadeIn(animationSpec = tween(timeline.enterMillis, delayMillis = timeline.exitMillis, easing = spec.easing))
    val exit = fadeOut(animationSpec = tween(timeline.exitMillis, easing = FastOutLinearInEasing))
    return enter togetherWith exit
}
