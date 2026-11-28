package com.beykus.beygram.ui.screens.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val ProfileStretchResistance = 0.35f
private const val RevealVelocityThresholdPx = 400f
private const val RevealSnapThreshold = 0.38f

@Stable
internal class ProfilePhotoStretchState {
    var distancePx by mutableFloatStateOf(0f)
}

/** Decides whether an interrupted or flung reveal settles to expanded (1f) or collapsed (0f). */
internal fun settleImmersiveRevealTarget(reveal: Float, velocityY: Float): Float =
    if ((reveal > RevealSnapThreshold && velocityY >= -RevealVelocityThresholdPx) ||
        velocityY > RevealVelocityThresholdPx
    ) 1f else 0f

/**
 * Single unified nested-scroll connection for Profile C:
 * - At scroll == 0, pulling down morphs the circular avatar continuously into the full-bleed cover,
 *   and any extra pull-down stretches it with elastic resistance.
 * - Swiping up while revealed smoothly folds the cover back to the circular avatar before normal
 *   vertical scrolling collapses the header into the compact toolbar.
 */
@Composable
internal fun rememberProfilePhotoStretch(
    state: ProfilePhotoStretchState,
    reveal: Animatable<Float, AnimationVector1D>,
    scroll: ScrollState,
    canReveal: Boolean,
    reduceMotion: Boolean,
    revealDragPx: Float,
    maxDistancePx: Float,
    scope: CoroutineScope,
    onSettled: (expanded: Boolean) -> Unit
): NestedScrollConnection {
    val canRevealNow by rememberUpdatedState(canReveal)
    val reduceMotionNow by rememberUpdatedState(reduceMotion)
    val settledCallback by rememberUpdatedState(onSettled)
    val safeDragPx = revealDragPx.coerceAtLeast(1f)

    fun settleTo(target: Float) {
        scope.launch {
            if (reduceMotionNow) {
                reveal.snapTo(target)
                state.distancePx = 0f
            } else {
                launch {
                    if (state.distancePx > 0f) {
                        animate(state.distancePx, 0f, animationSpec = tween(180)) { value, _ ->
                            state.distancePx = value
                        }
                    }
                }
                reveal.animateTo(target, tween(240))
            }
            settledCallback(target >= 1f)
        }
    }

    val connection = remember(state, reveal, scroll, safeDragPx, maxDistancePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canRevealNow || source != NestedScrollSource.UserInput || scroll.value != 0) {
                    return Offset.Zero
                }
                val dy = available.y
                if (dy > 0f) {
                    val curReveal = reveal.value
                    if (curReveal < 1f) {
                        val nextReveal = (curReveal + dy / safeDragPx).coerceIn(0f, 1f)
                        val consumedByReveal = (nextReveal - curReveal) * safeDragPx
                        scope.launch { reveal.snapTo(nextReveal) }
                        val overflow = (dy - consumedByReveal).coerceAtLeast(0f)
                        if (overflow > 0f && !reduceMotionNow) {
                            state.distancePx = (state.distancePx + overflow * ProfileStretchResistance)
                                .coerceAtMost(maxDistancePx)
                        }
                        return Offset(0f, dy)
                    }
                    if (!reduceMotionNow) {
                        state.distancePx = (state.distancePx + dy * ProfileStretchResistance)
                            .coerceAtMost(maxDistancePx)
                        return Offset(0f, dy)
                    }
                    return Offset.Zero
                }

                if (dy < 0f) {
                    var remaining = dy
                    var totalConsumed = 0f
                    val prevStretch = state.distancePx
                    if (prevStretch > 0f) {
                        val stretchConsumed = remaining.coerceAtLeast(-prevStretch / ProfileStretchResistance)
                        state.distancePx = (prevStretch + stretchConsumed * ProfileStretchResistance).coerceAtLeast(0f)
                        remaining -= stretchConsumed
                        totalConsumed += stretchConsumed
                    }
                    val curReveal = reveal.value
                    if (remaining < 0f && curReveal > 0f) {
                        val nextReveal = (curReveal + remaining / safeDragPx).coerceIn(0f, 1f)
                        val revealConsumed = (nextReveal - curReveal) * safeDragPx
                        scope.launch { reveal.snapTo(nextReveal) }
                        totalConsumed += revealConsumed
                    }
                    return Offset(0f, totalConsumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val curReveal = reveal.value
                val hasStretch = state.distancePx > 0f
                val isMidReveal = curReveal in 0.001f..0.999f
                if (!hasStretch && !isMidReveal) return Velocity.Zero
                val target = if (isMidReveal) settleImmersiveRevealTarget(curReveal, available.y) else 1f
                settleTo(target)
                return if (available.y > 0f || isMidReveal) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }

    LaunchedEffect(state, canReveal, scroll.isScrollInProgress) {
        if (!canReveal) {
            state.distancePx = 0f
        } else if (!scroll.isScrollInProgress) {
            if (state.distancePx > 0f) {
                animate(state.distancePx, 0f, animationSpec = tween(180)) { value, _ -> state.distancePx = value }
            }
            val curReveal = reveal.value
            if (curReveal in 0.001f..0.999f && !reveal.isRunning) {
                val target = settleImmersiveRevealTarget(curReveal, 0f)
                settleTo(target)
            }
        }
    }
    return connection
}
