package com.beykus.beygram.ui.screens.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlin.math.PI
import kotlin.math.sin

/** Shared with the wave card and the full player: the wave's TAU in Float, so the math stays in one precision. */
private val TAU: Float = (2 * PI).toFloat()

/**
 * Soft ribbons drifting across the wave card. Three sine bands in the palette's accent tones; the phase keeps
 * moving on a slow loop, so the card reads as alive without any state of its own.
 */
@Composable
internal fun WaveBackdrop(color: Color, secondary: Color, modifier: Modifier) {
    val mediaMotion = rememberMotionSpec(MotionToken.MediaExpand)
    val phase = if (mediaMotion.isInstant) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "wave-backdrop")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = TAU,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wave-backdrop-phase",
        )
        animated
    }
    Canvas(modifier) {
        val w: Float = size.width
        val h = size.height
        for (band in 0 until 3) {
            val baseY = h * (0.42f + 0.14f * band)
            val amplitude = h * (0.08f + 0.03f * band)
            val cycles = 1.1f + 0.35f * band
            val shift = phase * (1f + 0.4f * band) + 1.3f * band
            val path = Path()
            path.moveTo(0f, h)
            for (step in 0..48) {
                val x = w * step / 48f
                val y = baseY + amplitude * sin(TAU * cycles * x / w + shift)
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color = if (band == 1) secondary else color, alpha = 0.16f + 0.06f * band)
        }
    }
}
