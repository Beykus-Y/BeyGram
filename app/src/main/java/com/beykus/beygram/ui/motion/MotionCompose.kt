package com.beykus.beygram.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.beykus.beygram.ui.accessibility.LocalSystemReducedMotion

/** The user's motion settings, provided once at the app root next to [LocalSystemReducedMotion]. */
val LocalMotionSettings = staticCompositionLocalOf { MotionSettings() }

/**
 * Resolves a token against the current settings and the system reduce-motion flag. Components call this instead of
 * writing `tween(...)`, so a preset change reaches every adapter without editing the call sites.
 */
@Composable
fun rememberMotionSpec(token: MotionToken): MotionSpec {
    val settings = LocalMotionSettings.current
    val reduced = LocalSystemReducedMotion.current
    return remember(token, settings, reduced) { resolveMotion(token, settings, reduced) }
}

/** Fade-in for [AnimatedVisibility] and similar. Instant specs return [EnterTransition.None], so nothing starts at alpha 0. */
fun MotionSpec.fadeEnter(): EnterTransition =
    if (isInstant) EnterTransition.None else fadeIn(animationSpec())

fun MotionSpec.fadeExit(): ExitTransition =
    if (isInstant) ExitTransition.None else fadeOut(animationSpec())
