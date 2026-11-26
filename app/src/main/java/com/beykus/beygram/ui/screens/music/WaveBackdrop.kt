package com.beykus.beygram.ui.screens.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.beykus.beygram.ui.motion.LocalMotionSettings
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.screens.playback.BgRhythmSignal
import kotlin.math.PI
import kotlin.math.cos
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
    val path = remember { Path() }
    Canvas(modifier) {
        val w: Float = size.width
        val h = size.height
        for (band in 0 until 3) {
            val baseY = h * (0.42f + 0.14f * band)
            val amplitude = h * (0.08f + 0.03f * band)
            val cycles = 1.1f + 0.35f * band
            val shift = phase * (1f + 0.4f * band) + 1.3f * band
            path.reset()
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

internal data class TrackWaveSeed(
    val driftSpeed: Float,
    val baseTilt: Float,
    val primaryPhase: Float,
    val secondaryPhase: Float,
    val harmonicMix: Float,
    val anchorSpread: Float,
)

internal fun trackWaveSeedFor(trackKey: String): TrackWaveSeed {
    var hash = 0x811C9DC5.toInt()
    for (ch in trackKey) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    fun unit(shift: Int): Float = (((hash ushr shift) and 0xFF) / 255f)
    return TrackWaveSeed(
        driftSpeed = 0.22f + unit(0) * 0.18f,
        baseTilt = (unit(8) - 0.5f) * 0.08f,
        primaryPhase = unit(16) * TAU,
        secondaryPhase = unit(24) * TAU,
        harmonicMix = 0.30f + unit(4) * 0.35f,
        anchorSpread = 0.07f + unit(12) * 0.05f,
    )
}

/**
 * Wave Visuals 2.0: organic multi-layered fluid ribbons driven in draw-phase with zero per-frame
 * recomposition of the surrounding player sheet. Reacts gently to [BgRhythmSignal] when active,
 * and falls back to calm autonomous drift when BG Rhythm is off.
 */
@Composable
internal fun PlayerWaveBackdrop(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    darkTheme: Boolean,
    trackKey: String,
    animate: Boolean,
    alphaProvider: () -> Float,
    rhythmSignalProvider: () -> BgRhythmSignal,
    modifier: Modifier = Modifier,
    deterministicTimeSec: Float? = null,
) {
    val mediaMotion = rememberMotionSpec(MotionToken.MediaExpand)
    val motionSpeed = LocalMotionSettings.current.speed
    val seed = remember(trackKey) { trackWaveSeedFor(trackKey) }
    val timeSecState = remember(trackKey) { mutableFloatStateOf(deterministicTimeSec ?: 0f) }
    val smoothedEnergyState = remember(trackKey) { mutableFloatStateOf(0f) }

    LaunchedEffect(animate, mediaMotion.isInstant, deterministicTimeSec, trackKey) {
        if (deterministicTimeSec != null) {
            timeSecState.floatValue = deterministicTimeSec
            val sig = rhythmSignalProvider()
            smoothedEnergyState.floatValue = if (sig.isActive) {
                (sig.phraseEnergy * 0.65f + sig.beatPulse * 0.35f + sig.downbeatPulse * 0.25f).coerceIn(0f, 1.2f)
            } else {
                0f
            }
            return@LaunchedEffect
        }
        if (!animate || mediaMotion.isInstant) return@LaunchedEffect

        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    timeSecState.floatValue += dt * seed.driftSpeed * motionSpeed
                    val sig = rhythmSignalProvider()
                    val targetEnergy = if (sig.isActive) {
                        (sig.phraseEnergy * 0.65f + sig.beatPulse * 0.35f + sig.downbeatPulse * 0.25f)
                            .coerceIn(0f, 1.2f)
                    } else {
                        0f
                    }
                    // Smooth envelope prevents abrupt jumps on seek, pause, or beat transients.
                    val lerpRate = (dt * 6.5f).coerceIn(0f, 1f)
                    smoothedEnergyState.floatValue += (targetEnergy - smoothedEnergyState.floatValue) * lerpRate
                }
                lastNanos = frameNanos
            }
        }
    }

    val paths = remember { Array(4) { Path() } }
    val layerRatios = remember { floatArrayOf(1.0f, 1.37f, 1.79f, 2.23f) }

    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val layerBrushes = arrayOf(
                Brush.verticalGradient(
                    0.0f to primary.copy(alpha = if (darkTheme) 0.34f else 0.24f),
                    0.65f to primary.copy(alpha = if (darkTheme) 0.14f else 0.09f),
                    1.0f to Color.Transparent,
                    startY = h * 0.25f,
                    endY = h,
                ),
                Brush.verticalGradient(
                    0.0f to secondary.copy(alpha = if (darkTheme) 0.30f else 0.20f),
                    0.70f to secondary.copy(alpha = if (darkTheme) 0.12f else 0.08f),
                    1.0f to Color.Transparent,
                    startY = h * 0.35f,
                    endY = h,
                ),
                Brush.verticalGradient(
                    0.0f to tertiary.copy(alpha = if (darkTheme) 0.28f else 0.18f),
                    0.72f to primary.copy(alpha = if (darkTheme) 0.10f else 0.07f),
                    1.0f to Color.Transparent,
                    startY = h * 0.45f,
                    endY = h,
                ),
                Brush.verticalGradient(
                    0.0f to secondary.copy(alpha = if (darkTheme) 0.22f else 0.16f),
                    0.80f to tertiary.copy(alpha = if (darkTheme) 0.08f else 0.05f),
                    1.0f to Color.Transparent,
                    startY = h * 0.55f,
                    endY = h,
                ),
            )
            val glowBrush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = if (darkTheme) 0.24f else 0.16f),
                    secondary.copy(alpha = if (darkTheme) 0.10f else 0.06f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.34f),
                radius = maxOf(w, h) * 0.65f,
            )

            onDrawBehind {
                val layerAlpha = alphaProvider().coerceIn(0f, 1f)
                if (layerAlpha <= 0.005f || w <= 0f || h <= 0f) return@onDrawBehind

                val t = timeSecState.floatValue
                val energy = smoothedEnergyState.floatValue
                val motionGain = (0.35f + 0.65f * mediaMotion.intensity).coerceIn(0.2f, 1.2f)

                drawRect(
                    brush = glowBrush,
                    alpha = (layerAlpha * (0.65f + energy * 0.35f)).coerceIn(0f, 1f),
                )

                val steps = 36
                for (layer in 0 until 4) {
                    val path = paths[layer]
                    path.reset()
                    val ratio = layerRatios[layer]
                    val anchorY = h * (0.36f + layer * seed.anchorSpread)
                    val baseAmp = h * (0.048f + 0.014f * layer) * motionGain * (1f + energy * 0.55f)
                    val p1 = seed.primaryPhase + t * ratio * 1.15f + layer * 1.47f
                    val p2 = seed.secondaryPhase - t * ratio * 0.73f + layer * 2.13f
                    val p3 = t * (0.41f + 0.19f * layer) + layer * 0.89f

                    var prevX = 0f
                    var prevY = anchorY + baseAmp * (
                        sin(p1) * 0.58f +
                            cos(p2) * seed.harmonicMix +
                            sin(p3) * 0.18f
                        )
                    path.moveTo(0f, h)
                    path.lineTo(prevX, prevY)

                    for (step in 1..steps) {
                        val frac = step / steps.toFloat()
                        val x = w * frac
                        val tilt = (frac - 0.5f) * h * seed.baseTilt * (if (layer % 2 == 0) 1f else -1f)
                        val wave =
                            sin(frac * TAU * (0.85f + 0.18f * layer) + p1) * 0.58f +
                                cos(frac * TAU * (1.42f + 0.27f * layer) + p2) * seed.harmonicMix +
                                sin(frac * TAU * 2.31f + p3) * 0.18f
                        val y = anchorY + tilt + baseAmp * wave
                        val midX = (prevX + x) * 0.5f
                        val midY = (prevY + y) * 0.5f
                        path.quadraticTo(prevX, prevY, midX, midY)
                        prevX = x
                        prevY = y
                    }
                    path.lineTo(w, prevY)
                    path.lineTo(w, h)
                    path.close()

                    drawPath(
                        path = path,
                        brush = layerBrushes[layer],
                        alpha = layerAlpha,
                    )
                }
            }
        }
    )
}
