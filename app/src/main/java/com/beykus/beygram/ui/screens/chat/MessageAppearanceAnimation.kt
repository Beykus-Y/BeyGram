package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.validated
import kotlin.math.roundToInt

/** The measurable part of one entrance, kept separate from Compose so presets can be tested on the JVM. */
internal data class MessageAnimationPlan(
    val alphaFrom: Float,
    val scaleFrom: Float,
    val offsetDp: Float,
    val durationMillis: Int,
    val springDampingRatio: Float? = null,
    val springStiffness: Float = 0f
)

private const val BASE_DURATION_MILLIS = 220
private const val SPRING_DAMPING = 0.82f

/**
 * Turns a preset into starting values. Sent messages move in from the right and received ones from the left; the
 * signed offset is applied only by slide and custom presets.
 */
internal fun messageAnimationPlan(direction: MessageAnimationDirection, outgoing: Boolean): MessageAnimationPlan? {
    if (direction.preset == MessageAnimationPreset.None) return null
    val tuning = direction.tuning.validated()
    val intensity = tuning.intensity
    val signedOffset = tuning.offsetDp.toFloat() * intensity * if (outgoing) 1f else -1f
    val scaleFrom = (1f - (1f - tuning.scale) * intensity).coerceIn(0.5f, 1f)
    val duration = (BASE_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(80, 600)
    return when (direction.preset) {
        MessageAnimationPreset.None -> null
        MessageAnimationPreset.Fade -> MessageAnimationPlan(
            alphaFrom = (1f - 0.9f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetDp = 0f,
            durationMillis = duration
        )
        MessageAnimationPreset.Slide -> MessageAnimationPlan(
            alphaFrom = (1f - 0.35f * intensity).coerceIn(0f, 1f),
            scaleFrom = 1f,
            offsetDp = signedOffset,
            durationMillis = duration
        )
        MessageAnimationPreset.Spring -> MessageAnimationPlan(
            alphaFrom = (1f - 0.45f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetDp = 0f,
            durationMillis = duration,
            springDampingRatio = SPRING_DAMPING,
            springStiffness = (420f * tuning.speed).coerceIn(180f, 900f)
        )
        MessageAnimationPreset.Custom -> MessageAnimationPlan(
            alphaFrom = (1f - 0.9f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetDp = signedOffset,
            durationMillis = duration
        )
    }
}

/** Returns no plan for a disabled animation or for a row that is not a fresh arrival. */
internal fun messageAnimationPlan(config: MessageAnimationConfig, animate: Boolean, outgoing: Boolean): MessageAnimationPlan? =
    if (!config.enabled || !animate) null else messageAnimationPlan(config.direction(outgoing), outgoing)

private fun MessageAnimationPlan.spec(): AnimationSpec<Float> =
    if (springDampingRatio != null) spring(dampingRatio = springDampingRatio, stiffness = springStiffness)
    else tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)

/**
 * Plays the entrance for one already-present list row. [animate] is a one-shot claim from [MessageArrivalTracker]:
 * recomposition, delivery updates and the temporary-to-server key change never turn it back on. Compose's animation
 * clock already carries Android's animator duration scale, so system "remove animations" makes this immediate.
 */
@Composable
internal fun MessageAnimationFrame(
    animate: Boolean,
    outgoing: Boolean,
    config: MessageAnimationConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val plan = remember(config, animate, outgoing) { messageAnimationPlan(config, animate, outgoing) }
    val play = plan != null
    val progress = remember { Animatable(if (play) 0f else 1f) }
    var animating by remember { mutableStateOf(play) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        if (!play) return@LaunchedEffect
        progress.animateTo(1f, plan!!.spec())
        animating = false
    }
    val animated = if (animating) {
        Modifier.graphicsLayer {
            val p = progress.value
            val clamped = p.coerceIn(0f, 1f)
            alpha = plan!!.alphaFrom + (1f - plan.alphaFrom) * clamped
            val scale = plan.scaleFrom + (1f - plan.scaleFrom) * p
            scaleX = scale
            scaleY = scale
            translationX = with(density) { (plan.offsetDp * (1f - clamped)).dp.toPx() }
        }
    } else Modifier
    Box(modifier.fillMaxWidth().then(animated)) { content() }
}
