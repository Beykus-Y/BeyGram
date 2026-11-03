package com.beykus.beygram.ui.motion

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import kotlinx.coroutines.delay

/**
 * The autoplay rule for decorative animation: animated stickers and custom emoji, GIFs and auto-looping previews start only
 * after the element has been on screen for [REQUIRED_VISIBLE_MILLIS] in a row, with at least [MIN_VISIBLE_FRACTION] of its
 * area inside the window. Until then the element shows a still first frame or poster. Explicit playback of video, audio and
 * music never goes through this rule.
 */
object AutoplayVisibility {
    const val MIN_VISIBLE_FRACTION = 0.5f
    const val REQUIRED_VISIBLE_MILLIS = 500L

    /** The share of [item] that lies inside [viewport], from 0 to 1. An empty item, or one outside the viewport, is 0. */
    fun visibleFraction(item: Rect, viewport: Rect): Float {
        val area = item.width * item.height
        if (!(area > 0f)) return 0f
        val visibleWidth = minOf(item.right, viewport.right) - maxOf(item.left, viewport.left)
        val visibleHeight = minOf(item.bottom, viewport.bottom) - maxOf(item.top, viewport.top)
        if (visibleWidth <= 0f || visibleHeight <= 0f) return 0f
        return (visibleWidth * visibleHeight / area).coerceIn(0f, 1f)
    }

    fun meetsVisibleThreshold(fraction: Float): Boolean = fraction >= MIN_VISIBLE_FRACTION

    /** Same precedence as [resolveMotion]: the master switch, then the system reduce-motion flag, then the category. */
    fun motionAllows(settings: MotionSettings, reducedMotion: Boolean, category: MotionCategory = MotionCategory.Media): Boolean =
        settings.enabled && !reducedMotion && category !in settings.disabledCategories

    /** Every condition except the dwell time, which [rememberAutoplayEligible] waits out after these hold. */
    fun canAutoplay(motionAllowed: Boolean, lifecycleStarted: Boolean, visibleEnough: Boolean): Boolean =
        motionAllowed && lifecycleStarted && visibleEnough
}

/**
 * What [rememberAutoplayEligible] reports: [eligible] says whether automatic animation may run now, and [modifier] must be
 * applied to the element so that its position on screen is tracked.
 */
class AutoplayEligibility internal constructor(
    val eligible: State<Boolean>,
    val modifier: Modifier
)

/**
 * Tracks whether a decorative animation may run for the element that carries [AutoplayEligibility.modifier]. The element must
 * be at least half inside the window, measured when it is placed, so composition and lazy prefetch do not count. Its lifecycle
 * must be at least STARTED and the motion settings must allow it. A popup, menu or dialog drawn above does not stop it. Then it waits
 * [AutoplayVisibility.REQUIRED_VISIBLE_MILLIS]. Any gap cancels that wait, so the countdown starts over.
 *
 * With [enabled] false the element never animates on its own and [AutoplayEligibility.eligible] stays true, so content that
 * does not animate can pass the value through unchanged.
 */
@Composable
fun rememberAutoplayEligible(enabled: Boolean = true, category: MotionCategory = MotionCategory.Media): AutoplayEligibility {
    val motion = LocalMotionSettings.current
    val reducedMotion = LocalSystemReducedMotion.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var lifecycleStarted by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visibleEnough = remember { mutableStateOf(false) }
    val windowLocation = remember { IntArray(2) }
    val running = AutoplayVisibility.canAutoplay(
        motionAllowed = enabled && AutoplayVisibility.motionAllows(motion, reducedMotion, category),
        lifecycleStarted = lifecycleStarted,
        visibleEnough = visibleEnough.value
    )
    val eligible = remember { mutableStateOf(!enabled) }
    LaunchedEffect(running, enabled) {
        eligible.value = !enabled
        if (running) {
            awaitAutoplayDwell()
            eligible.value = true
        }
    }
    val modifier = if (!enabled) Modifier else Modifier.onGloballyPositioned { coordinates ->
        // The window rectangle is read here, at placement time. A value captured during composition can be stale, because
        // the window size is known only after the first layout.
        val fraction = AutoplayVisibility.visibleFraction(coordinates.boundsInWindow(), view.windowRect(windowLocation))
        val visible = AutoplayVisibility.meetsVisibleThreshold(fraction)
        if (visibleEnough.value != visible) visibleEnough.value = visible
    }
    return AutoplayEligibility(eligible, modifier)
}

/** Suspends for the dwell time. The gate cancels this wait as soon as visibility drops, so every gap restarts the count. */
internal suspend fun awaitAutoplayDwell(requiredMillis: Long = AutoplayVisibility.REQUIRED_VISIBLE_MILLIS) {
    delay(requiredMillis)
}

/** The view's rectangle in window coordinates. */
private fun View.windowRect(location: IntArray): Rect {
    getLocationInWindow(location)
    return Rect(
        location[0].toFloat(),
        location[1].toFloat(),
        (location[0] + width).toFloat(),
        (location[1] + height).toFloat()
    )
}
