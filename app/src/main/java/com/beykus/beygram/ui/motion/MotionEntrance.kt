package com.beykus.beygram.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Fades a panel in and lifts it by [rise] when it first appears. The offset and the fade both follow [token], so the
 * preset and the speed apply without the caller knowing them. An instant spec starts fully visible, so reduced motion
 * and the Off preset never show a frame at alpha 0.
 */
@Composable
fun Modifier.motionEntrance(token: MotionToken, rise: Dp): Modifier {
    val spec = rememberMotionSpec(token)
    val progress = remember { Animatable(if (spec.isInstant) 1f else 0f) }
    LaunchedEffect(spec) {
        if (!spec.isInstant) progress.animateTo(1f, spec.animationSpec())
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise.toPx() * spec.intensity
    }
}
