package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageAnimationDirection
import com.beykus.beygram.data.appearance.MessageAnimationEasing
import com.beykus.beygram.data.appearance.MessageAnimationMotionDirection
import com.beykus.beygram.data.appearance.MessageAnimationPreset
import com.beykus.beygram.data.appearance.flowFallback
import com.beykus.beygram.data.appearance.validated
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import kotlin.math.roundToInt

/** The measurable part of one entrance, kept separate from Compose so presets can be tested on the JVM. */
internal data class MessageAnimationPlan(
    val alphaFrom: Float,
    val scaleFrom: Float,
    val offsetXDp: Float,
    val offsetYDp: Float,
    val rotationYDegrees: Float = 0f,
    val rotationZDegrees: Float = 0f,
    val durationMillis: Int,
    val easing: MessageAnimationEasing = MessageAnimationEasing.Smooth,
    val springDampingRatio: Float? = null,
    val springStiffness: Float = 0f
)

private const val BASE_DURATION_MILLIS = 220
private const val POP_DURATION_MILLIS = 200
private const val FLOW_DURATION_MILLIS = 280
private const val DRIFT_DURATION_MILLIS = 320
private const val QUICK_DURATION_MILLIS = 90
private const val TILT_DURATION_MILLIS = 260
private const val SWAY_DURATION_MILLIS = 240

/**
 * Turns a preset into starting values. Sent and received entrances mirror each other unless the tuning overrides the
 * direction. Offsets are applied through graphicsLayer, so no preset changes list heights.
 */
internal fun messageAnimationPlan(direction: MessageAnimationDirection, outgoing: Boolean): MessageAnimationPlan? {
    if (direction.preset == MessageAnimationPreset.None) return null
    val tuning = direction.tuning.validated()
    val intensity = tuning.intensity
    val horizontal = horizontalSign(outgoing, tuning.motionDirection)
    val vertical = verticalSign(tuning.motionDirection)
    val signedOffset = tuning.offsetDp.toFloat() * intensity
    val scaleFrom = (1f - (1f - tuning.scale) * intensity).coerceIn(0.5f, 1f)
    val duration = (BASE_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(80, 600)
    return when (direction.preset) {
        MessageAnimationPreset.None -> null
        MessageAnimationPreset.Fade -> MessageAnimationPlan(
            alphaFrom = (1f - 0.9f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetXDp = 0f,
            offsetYDp = 0f,
            durationMillis = duration,
            easing = tuning.easing
        )
        MessageAnimationPreset.Slide -> MessageAnimationPlan(
            alphaFrom = (1f - 0.35f * intensity).coerceIn(0f, 1f),
            scaleFrom = 1f,
            offsetXDp = if (vertical != 0f) 0f else horizontal * signedOffset,
            offsetYDp = if (vertical != 0f) vertical * signedOffset else 0f,
            durationMillis = duration,
            easing = tuning.easing
        )
        MessageAnimationPreset.Spring -> MessageAnimationPlan(
            alphaFrom = (1f - 0.45f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetXDp = 0f,
            offsetYDp = 0f,
            durationMillis = duration,
            springDampingRatio = springDamping(tuning.springiness),
            springStiffness = (420f * tuning.speed).coerceIn(180f, 900f)
        )
        MessageAnimationPreset.Pop -> MessageAnimationPlan(
            alphaFrom = (1f - 0.35f * intensity).coerceIn(0f, 1f),
            // Pop adds its own extra shrink on top of the configured starting scale, but intensity must be able to
            // turn the whole visible change off (0% -> scale 1).
            scaleFrom = (1f - ((1f - tuning.scale) + 0.08f) * intensity).coerceIn(0.72f, 1f),
            offsetXDp = 0f,
            offsetYDp = 0f,
            durationMillis = (POP_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(80, 420),
            springDampingRatio = (0.9f - 0.3f * tuning.springiness).coerceIn(0.58f, 0.9f),
            springStiffness = (520f * tuning.speed).coerceIn(220f, 980f)
        )
        MessageAnimationPreset.Flow -> MessageAnimationPlan(
            alphaFrom = (1f - 0.5f * intensity).coerceIn(0f, 1f),
            scaleFrom = (1f - ((1f - tuning.scale) + 0.015f) * intensity).coerceIn(0.5f, 1f),
            offsetXDp = if (vertical != 0f) 0f else horizontal * signedOffset,
            offsetYDp = if (vertical != 0f) vertical * signedOffset * 0.75f else -minOf(6f, signedOffset * 0.35f),
            durationMillis = (FLOW_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(100, 620),
            easing = tuning.easing
        )
        MessageAnimationPreset.Drift -> MessageAnimationPlan(
            alphaFrom = (1f - 0.45f * intensity).coerceIn(0f, 1f),
            scaleFrom = (1f - ((1f - tuning.scale) + 0.03f) * intensity).coerceIn(0.5f, 1f),
            offsetXDp = if (vertical != 0f) 0f else horizontal * signedOffset * 1.3f,
            offsetYDp = if (vertical != 0f) vertical * signedOffset * 1.3f else -signedOffset * 0.85f,
            durationMillis = (DRIFT_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(120, 700),
            easing = MessageAnimationEasing.EaseOut
        )
        MessageAnimationPreset.Quick -> MessageAnimationPlan(
            alphaFrom = (1f - 0.18f * intensity).coerceIn(0f, 1f),
            scaleFrom = (1f - 0.015f * intensity).coerceIn(0.96f, 1f),
            // Quick has no Direction control, so an override saved for another preset must not erase its offset.
            offsetXDp = automaticHorizontalSign(outgoing) * 2f * intensity,
            offsetYDp = 0f,
            durationMillis = (QUICK_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(60, 160),
            easing = MessageAnimationEasing.Smooth
        )
        MessageAnimationPreset.Tilt -> MessageAnimationPlan(
            alphaFrom = (1f - 0.55f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetXDp = 0f,
            offsetYDp = -2f * intensity,
            rotationYDegrees = automaticHorizontalSign(outgoing) * 8f * intensity,
            durationMillis = (TILT_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(100, 620),
            easing = tuning.easing
        )
        MessageAnimationPreset.Sway -> MessageAnimationPlan(
            alphaFrom = (1f - 0.45f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetXDp = automaticHorizontalSign(outgoing) * 3f * intensity,
            offsetYDp = 0f,
            rotationZDegrees = automaticHorizontalSign(outgoing) * 2.4f * intensity,
            durationMillis = (SWAY_DURATION_MILLIS / tuning.speed).roundToInt().coerceIn(100, 620),
            easing = tuning.easing
        )
        // The row itself is hidden while the spatial composer transition runs. If the transition is unavailable,
        // the same row falls back to the standard BeyGram Flow entrance. Only Speed is exposed by Composer Launch,
        // so the remaining Flow defaults are restored instead of inheriting hidden tuning.
        MessageAnimationPreset.ComposerLaunch -> messageAnimationPlan(direction.flowFallback(), outgoing)
        MessageAnimationPreset.Custom -> MessageAnimationPlan(
            alphaFrom = (1f - 0.9f * intensity).coerceIn(0f, 1f),
            scaleFrom = scaleFrom,
            offsetXDp = if (vertical != 0f) 0f else horizontal * signedOffset,
            offsetYDp = if (vertical != 0f) vertical * signedOffset else 0f,
            durationMillis = duration,
            easing = tuning.easing
        )
    }
}

/** Returns no plan for a disabled animation or for a row that is not a fresh arrival. */
internal fun messageAnimationPlan(config: MessageAnimationConfig, animate: Boolean, outgoing: Boolean): MessageAnimationPlan? =
    if (!config.enabled || !animate) null else messageAnimationPlan(config.direction(outgoing), outgoing)

/**
 * System "Remove animations" sets the animator duration scale to zero. Compose would finish the animation
 * on the next frame anyway, but skipping the plan keeps the row visible from its first frame instead of
 * showing it transparent or shifted for one frame.
 */
internal fun messageAnimationPlan(
    config: MessageAnimationConfig,
    animate: Boolean,
    outgoing: Boolean,
    motionEnabled: Boolean
): MessageAnimationPlan? = if (!motionEnabled) null else messageAnimationPlan(config, animate, outgoing)

/** Reads Android's animator duration scale and screen-reader state at composition time. */
@Composable
internal fun systemMotionEnabled(): Boolean = !systemReducedMotion()

private fun horizontalSign(outgoing: Boolean, direction: MessageAnimationMotionDirection): Float = when (direction) {
    MessageAnimationMotionDirection.Auto -> automaticHorizontalSign(outgoing)
    MessageAnimationMotionDirection.FromEnd -> 1f
    MessageAnimationMotionDirection.FromStart -> -1f
    MessageAnimationMotionDirection.FromTop, MessageAnimationMotionDirection.FromBottom -> 0f
}

/** Presets without a Direction control always use the mirrored automatic side. */
private fun automaticHorizontalSign(outgoing: Boolean): Float = if (outgoing) 1f else -1f

private fun verticalSign(direction: MessageAnimationMotionDirection): Float = when (direction) {
    MessageAnimationMotionDirection.FromTop -> -1f
    MessageAnimationMotionDirection.FromBottom -> 1f
    else -> 0f
}

/** Matches the damping of the original PR #348 spring at the default springiness (0.4 -> 0.82). */
internal fun springDamping(springiness: Float): Float =
    (1f - 0.45f * springiness).coerceIn(0.55f, 1f)

private fun MessageAnimationPlan.spec(): AnimationSpec<Float> =
    if (springDampingRatio != null) spring(dampingRatio = springDampingRatio, stiffness = springStiffness)
    else tween(durationMillis = durationMillis, easing = easing.toComposeEasing())

private fun MessageAnimationEasing.toComposeEasing() = when (this) {
    MessageAnimationEasing.Smooth -> FastOutSlowInEasing
    MessageAnimationEasing.Linear -> LinearEasing
    MessageAnimationEasing.EaseIn -> CubicBezierEasing(0.42f, 0f, 1f, 1f)
    MessageAnimationEasing.EaseOut -> CubicBezierEasing(0f, 0f, 0.2f, 1f)
    MessageAnimationEasing.Expressive -> CubicBezierEasing(0.18f, 0.9f, 0.2f, 1f)
}

/**
 * Plays the entrance for one already-present list row. [animate] is a one-shot claim from [MessageArrivalTracker]:
 * recomposition, delivery updates and the temporary-to-server key change never turn it back on. Compose's animation
 * clock carries Android's animator duration scale, and [systemMotionEnabled] skips the first-frame offset when the
 * user removed animations.
 */
@Composable
internal fun MessageAnimationFrame(
    animate: Boolean,
    outgoing: Boolean,
    config: MessageAnimationConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motionEnabled = systemMotionEnabled()
    val plan = remember(config, animate, outgoing, motionEnabled) {
        messageAnimationPlan(config, animate, outgoing, motionEnabled)
    }
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
            translationX = with(density) { (plan.offsetXDp * (1f - clamped)).dp.toPx() }
            translationY = with(density) { (plan.offsetYDp * (1f - clamped)).dp.toPx() }
            rotationY = plan.rotationYDegrees * (1f - clamped)
            rotationZ = plan.rotationZDegrees * (1f - clamped)
            cameraDistance = 12f * density.density
            transformOrigin = TransformOrigin.Center
        }
    } else Modifier
    Box(modifier.fillMaxWidth().then(animated)) { content() }
}
