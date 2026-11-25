package com.beykus.beygram.ui.screens.profile

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

private const val ProfileStretchResistance = 0.35f

@Stable
internal class ProfilePhotoStretchState {
    var distancePx by mutableFloatStateOf(0f)
}

/** The expanded photo absorbs a small downward drag at the top before normal scrolling resumes. */
@Composable
internal fun rememberProfilePhotoStretch(
    state: ProfilePhotoStretchState,
    scroll: ScrollState,
    enabled: Boolean,
    maxDistancePx: Float
): NestedScrollConnection {
    val canStretch by rememberUpdatedState(enabled)
    val connection = remember(state, scroll, maxDistancePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canStretch || source != NestedScrollSource.UserInput || scroll.value != 0) return Offset.Zero
                val previous = state.distancePx
                if (available.y > 0f) {
                    state.distancePx = (previous + available.y * ProfileStretchResistance).coerceAtMost(maxDistancePx)
                    return Offset(0f, available.y)
                }
                if (previous <= 0f) return Offset.Zero
                val consumed = available.y.coerceAtLeast(-previous / ProfileStretchResistance)
                state.distancePx = (previous + consumed * ProfileStretchResistance).coerceAtLeast(0f)
                return Offset(0f, consumed)
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (state.distancePx > 0f && available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
        }
    }
    // The scroll state also drops on cancellation, when onPreFling is not guaranteed.
    LaunchedEffect(state, enabled, scroll.isScrollInProgress) {
        if (!enabled) state.distancePx = 0f
        else if (!scroll.isScrollInProgress && state.distancePx > 0f) {
            animate(state.distancePx, 0f, animationSpec = tween(180)) { value, _ -> state.distancePx = value }
        }
    }
    return connection
}
