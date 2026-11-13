package com.beykus.beygram.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion
import com.beykus.beygram.ui.design.DesignMotionProfile
import com.beykus.beygram.ui.design.LocalDesignSystem
import kotlin.math.roundToInt

/** The user's motion settings, provided once at the app root next to [LocalSystemReducedMotion]. */
val LocalMotionSettings = staticCompositionLocalOf { MotionSettings() }

/**
 * Resolves a token against the current settings, the system reduce-motion flag and the motion profile of the design
 * style. Components call this instead of writing `tween(...)`, so a preset change and a style change reach every adapter
 * without editing the call sites. User and system restrictions always win: an instant spec stays instant.
 */
@Composable
fun rememberMotionSpec(token: MotionToken): MotionSpec {
    val settings = LocalMotionSettings.current
    val reduced = LocalSystemReducedMotion.current
    val profile = LocalDesignSystem.current.motion
    return remember(token, settings, reduced, profile) {
        resolveMotion(token, settings, reduced).withDesignProfile(profile)
    }
}

/**
 * Applies the style's motion profile on top of the resolved user settings. An instant spec stays instant: motion is
 * off, the category is disabled or the system asked for reduced motion, and no style may animate through it.
 */
internal fun MotionSpec.withDesignProfile(profile: DesignMotionProfile): MotionSpec =
    if (isInstant) this else copy(
        durationMillis = (durationMillis * profile.speedScale).roundToInt().coerceAtLeast(1),
        intensity = intensity * profile.intensityScale
    )

/** Fade-in for [AnimatedVisibility] and similar. Instant specs return [EnterTransition.None], so nothing starts at alpha 0. */
fun MotionSpec.fadeEnter(): EnterTransition =
    if (isInstant) EnterTransition.None else fadeIn(animationSpec())

fun MotionSpec.fadeExit(): ExitTransition =
    if (isInstant) ExitTransition.None else fadeOut(animationSpec())

/**
 * Live policy for long-running scroll jobs. A captured Boolean only checks the setting
 * at the start; this State also changes when the user disables motion mid-scroll.
 */
@Composable
fun rememberMotionInstant(token: MotionToken): State<Boolean> =
    rememberUpdatedState(rememberMotionSpec(token).isInstant)

/** Cancel an in-flight programmatic movement and snap when motion becomes instant. */
internal suspend fun scrollWithMotion(
    motionInstant: State<Boolean>,
    snap: suspend () -> Unit,
    animate: suspend () -> Unit
) {
    if (motionInstant.value) {
        snap()
        return
    }
    coroutineScope {
        val animation = async { animate() }
        val restriction = async { snapshotFlow { motionInstant.value }.first { it } }
        try {
            val becameInstant = select<Boolean> {
                restriction.onAwait { true }
                animation.onAwait { false }
            }
            if (becameInstant || motionInstant.value) {
                animation.cancelAndJoin()
                snap()
            }
        } finally {
            restriction.cancel()
        }
    }
}

/** Interactive drag remains unchanged; only programmatic list movement is adapted. */
suspend fun LazyListState.scrollToItemRespectingMotion(
    index: Int,
    scrollOffset: Int = 0,
    motionInstant: State<Boolean>
) = scrollWithMotion(motionInstant,
    snap = { scrollToItem(index, scrollOffset) },
    animate = { animateScrollToItem(index, scrollOffset) })

suspend fun LazyGridState.scrollToItemRespectingMotion(
    index: Int,
    scrollOffset: Int = 0,
    motionInstant: State<Boolean>
) = scrollWithMotion(motionInstant,
    snap = { scrollToItem(index, scrollOffset) },
    animate = { animateScrollToItem(index, scrollOffset) })

suspend fun PagerState.scrollToPageRespectingMotion(
    page: Int,
    motionInstant: State<Boolean>
) = scrollWithMotion(motionInstant,
    snap = { scrollToPage(page) },
    animate = { animateScrollToPage(page) })
