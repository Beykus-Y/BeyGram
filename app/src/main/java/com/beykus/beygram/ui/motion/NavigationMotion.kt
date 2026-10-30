package com.beykus.beygram.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

/**
 * The transition for one [move]. A push slides in from the right and a pop from the left; tab switches only fade.
 * An instant spec gives a cut with no alpha or offset, so reduced motion and the Off preset show the new screen on its
 * first frame. Intensity scales only the slide distance, and the fade always runs.
 */
fun routeContentTransform(
    move: NavigationMove,
    push: MotionSpec,
    pop: MotionSpec,
    tab: MotionSpec
): ContentTransform = when (move) {
    NavigationMove.Push -> slideTransform(push, direction = 1)
    NavigationMove.Pop -> slideTransform(pop, direction = -1)
    NavigationMove.Tab, NavigationMove.None -> tab.fadeEnter() togetherWith tab.fadeExit()
}

private fun slideTransform(spec: MotionSpec, direction: Int): ContentTransform {
    if (spec.isInstant) return EnterTransition.None togetherWith ExitTransition.None
    val travel = { fullWidth: Int -> direction * (fullWidth * RouteSlideFraction * spec.intensity).roundToInt() }
    return (slideInHorizontally(spec.animationSpec(), initialOffsetX = travel) + spec.fadeEnter()) togetherWith
        (slideOutHorizontally(spec.animationSpec(), targetOffsetX = { -travel(it) }) + spec.fadeExit())
}
