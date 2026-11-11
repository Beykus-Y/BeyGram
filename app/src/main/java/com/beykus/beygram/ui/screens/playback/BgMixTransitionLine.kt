package com.beykus.beygram.ui.screens.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.playback.BgMixTransitionUi
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.fadeEnter
import com.beykus.beygram.ui.motion.fadeExit
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlin.math.sin

/** Cycles per second of the ambient phase. Slow on purpose: the decoration is a hint, not a spinner. */
private const val AMBIENT_CYCLES_PER_SECOND = 0.25f

/** Two pi as a Float, for the sine waves of the decorations. */
private const val TAU = 6.2831855f

/** Points sampled along the Signature wave. */
private const val WAVE_STEPS = 40

/** A longer line is cut with an ellipsis instead of pushing the transport controls. */
private val LineMaxWidth = 420.dp

private val PillShape = RoundedCornerShape(50)

/**
 * What the line says and which pair of tracks it belongs to. A new pair is a new label even when the title repeats. The
 * derived state built from it changes only when one of these changes, so progress ticks never recompose the text.
 */
private data class TransitionLabel(
    val fromNativeId: Long?,
    val targetKey: String?,
    val title: String?
)

/** Without a known incoming title the line says "next track" instead of naming a wrong track. */
private fun BgMixTransitionUi.toLabel(): TransitionLabel = TransitionLabel(
    fromNativeId = fromNativeId,
    targetKey = target?.key,
    title = target?.musicTitle()?.ifBlank { null }
)

/**
 * The "Transition to …" line under the timeline while BG Mix mixes two tracks. The text stands still; a thin decoration
 * around it follows the real progress of the mix, in the way of the current design style. The slot keeps its height while
 * the line is hidden, so the transport controls below never move.
 */
@Composable
internal fun BgMixTransitionLine(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    val style = LocalDesignSystem.current.style
    val spec = rememberMotionSpec(MotionToken.MediaExpand)
    val reduced = spec.isInstant || systemReducedMotion()
    val transition = PlaybackEngine.bgMixTransition.collectAsStateWithLifecycle()

    // Progress and pause are read only in draw code and in the effects below, never in the composition.
    val labelState = remember { derivedStateOf { transition.value?.toLabel() } }
    val label = labelState.value
    val running by remember { derivedStateOf { transition.value?.paused == false } }

    // The last line stays on screen while the exit fades out, instead of an empty pill.
    var lastShown by remember { mutableStateOf<TransitionLabel?>(null) }
    LaunchedEffect(label) {
        if (label != null) lastShown = label
    }
    val shown = label ?: lastShown
    val title = shown?.title
    val lineText = if (title != null) {
        stringResource(R.string.player_bgmix_transition_to, title)
    } else {
        stringResource(R.string.player_bgmix_transition_next)
    }

    // The ambient phase turns only while a mix plays. A pause freezes it where it is; reduced motion keeps it at zero.
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced, running) {
        if (!reduced && running) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.25f)
                phase.floatValue = (phase.floatValue + seconds * AMBIENT_CYCLES_PER_SECOND) % 1f
                last = now
            }
        }
    }

    val enter: EnterTransition = if (reduced) {
        EnterTransition.None
    } else {
        spec.fadeEnter() + slideInVertically(spec.animationSpec<IntOffset>()) { it / 4 }
    }
    val exit: ExitTransition = if (reduced) ExitTransition.None else spec.fadeExit()

    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clipToBounds()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        // Reserves the height of the line in every state, and follows the font scale. It carries the vertical padding of
        // the pill, the tallest variant. It is invisible and removed from the accessibility tree.
        Text(
            text = lineText,
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .alpha(0f)
                .clearAndSetSemantics {}
        )
        AnimatedVisibility(visible = label != null, enter = enter, exit = exit) {
            BgMixLineVisual(
                style = style,
                text = lineText,
                palette = palette,
                transition = transition,
                phase = phase,
                ambient = !reduced
            )
        }
    }
}

/** The visible line in the look of the design style. */
@Composable
private fun BgMixLineVisual(
    style: DesignStyle,
    text: String,
    palette: BeyGramPalette,
    transition: State<BgMixTransitionUi?>,
    phase: FloatState,
    ambient: Boolean
) {
    when (style) {
        DesignStyle.Default -> DefaultMixLine(text, palette, transition, phase, ambient)
        DesignStyle.Material2 -> Material2MixLine(text, palette, transition, phase, ambient)
        DesignStyle.Material3 -> Material3MixLine(text, palette, transition, phase, ambient)
        DesignStyle.LiquidGlass -> LiquidGlassMixLine(text, palette, transition, phase, ambient)
        DesignStyle.Signature -> SignatureMixLine(text, palette, transition, phase, ambient)
    }
}

@Composable
private fun DefaultMixLine(
    text: String,
    palette: BeyGramPalette,
    transition: State<BgMixTransitionUi?>,
    phase: FloatState,
    ambient: Boolean
) {
    Text(
        text = text,
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = LineMaxWidth)
            .padding(bottom = 6.dp)
            .drawBehind { drawDefaultProgress(palette, progressNow(transition), phaseNow(phase, ambient)) }
    )
}

@Composable
private fun Material2MixLine(
    text: String,
    palette: BeyGramPalette,
    transition: State<BgMixTransitionUi?>,
    phase: FloatState,
    ambient: Boolean
) {
    Row(
        modifier = Modifier.widthIn(max = LineMaxWidth),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(14.dp, 12.dp)) {
            drawWaveBars(palette, progressNow(transition), phaseNow(phase, ambient))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun Material3MixLine(
    text: String,
    palette: BeyGramPalette,
    transition: State<BgMixTransitionUi?>,
    phase: FloatState,
    ambient: Boolean
) {
    Text(
        text = text,
        color = palette.accentInk,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = LineMaxWidth)
            .clip(PillShape)
            .background(palette.accentSoft)
            .drawBehind { drawTonalSpot(palette, progressNow(transition), phaseNow(phase, ambient)) }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun LiquidGlassMixLine(
    text: String,
    palette: BeyGramPalette,
    transition: State<BgMixTransitionUi?>,
    phase: FloatState,
    ambient: Boolean
) {
    Text(
        text = text,
        color = palette.textPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = LineMaxWidth)
            .clip(PillShape)
            .background(palette.surface.copy(alpha = 0.55f), PillShape)
            .border(1.dp, palette.textPrimary.copy(alpha = 0.12f), PillShape)
            .drawWithContent {
                drawContent()
                drawRimGlint(progressNow(transition), phaseNow(phase, ambient))
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun SignatureMixLine(
    text: String,
    palette: BeyGramPalette,
    transition: State<BgMixTransitionUi?>,
    phase: FloatState,
    ambient: Boolean
) {
    Text(
        text = text,
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = LineMaxWidth)
            .padding(bottom = 6.dp)
            .drawBehind { drawSignatureWave(palette, progressNow(transition), phaseNow(phase, ambient)) }
    )
}

/** Progress of the running mix, 0..1. Call it only from a draw lambda, so a tick redraws without recomposing. */
private fun progressNow(transition: State<BgMixTransitionUi?>): Float = transition.value?.progress ?: 0f

/** The ambient phase, or zero when the decoration must stand still. Call it only from a draw lambda. */
private fun phaseNow(phase: FloatState, ambient: Boolean): Float = if (ambient) phase.floatValue else 0f

/** Smooth 0..1 ramp between two edges. */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Amplitude of the ambient motion: full while the mix runs, easing to zero over its last 15 percent. */
private fun calmOf(progress: Float): Float = 1f - smoothstep(0.85f, 1f, progress)

private fun lerpUnit(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** Default: a thin track under the text, filled to the progress, with a soft highlight that travels along it. */
private fun DrawScope.drawDefaultProgress(palette: BeyGramPalette, progress: Float, phase: Float) {
    val calm = calmOf(progress)
    val stroke = 1.5.dp.toPx()
    val y = size.height - 3.dp.toPx()
    val left = size.width * 0.15f
    val length = size.width * 0.7f
    drawLine(palette.divider, Offset(left, y), Offset(left + length, y), strokeWidth = stroke, cap = StrokeCap.Round)
    if (progress > 0f) {
        drawLine(
            palette.accent,
            Offset(left, y),
            Offset(left + length * progress, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
    // The highlight enters at the left and leaves at the right. The half-sine envelope keeps it invisible at both ends,
    // so the phase wrapping back to zero never shows as a jump.
    val segment = length * 0.18f
    val x = left + (length - segment) * phase
    val glow = palette.accent.copy(alpha = 0.35f * calm * sin(TAU * 0.5f * phase))
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(glow.copy(alpha = 0f), glow, glow.copy(alpha = 0f)),
            startX = x,
            endX = x + segment
        ),
        start = Offset(x, y),
        end = Offset(x + segment, y),
        strokeWidth = stroke
    )
}

/** Material 2: three short bars that move with the phase; they fill in with the progress. */
private fun DrawScope.drawWaveBars(palette: BeyGramPalette, progress: Float, phase: Float) {
    val calm = calmOf(progress)
    val barWidth = 2.dp.toPx()
    val gap = (size.width - barWidth * 3f) / 2f
    val color = palette.accent.copy(alpha = 0.55f + 0.45f * progress)
    for (index in 0 until 3) {
        val level = (0.65f + 0.3f * calm * sin(TAU * (phase + index / 3f))).coerceIn(0.35f, 1f)
        val height = size.height * level
        drawRoundRect(
            color = color,
            topLeft = Offset(index * (barWidth + gap), (size.height - height) / 2f),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 4f)
        )
    }
}

/** Material 3: a soft spot of accent light that travels across the capsule with the progress, with a slight wobble. */
private fun DrawScope.drawTonalSpot(palette: BeyGramPalette, progress: Float, phase: Float) {
    val calm = calmOf(progress)
    val spotWidth = size.width * 0.35f
    val centre = size.width * lerpUnit(0.15f, 0.85f, progress) + size.width * 0.03f * calm * sin(TAU * phase)
    val tint = palette.accent.copy(alpha = 0.18f * calm + 0.06f)
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(tint.copy(alpha = 0f), tint, tint.copy(alpha = 0f)),
            startX = centre - spotWidth / 2f,
            endX = centre + spotWidth / 2f
        ),
        size = size
    )
}

/** Liquid glass: a short light glint that travels along the top rim and wraps at the ends. The fill never changes. */
private fun DrawScope.drawRimGlint(progress: Float, phase: Float) {
    val calm = calmOf(progress)
    if (calm <= 0f) return
    val light = Color.White.copy(alpha = 0.35f * calm)
    val glintLength = size.width * 0.22f
    val rimY = 0.5.dp.toPx()
    val centre = size.width * phase
    // The copies one width to either side keep the glint continuous across the wrap; the clip keeps only what is visible.
    for (x in listOf(centre - size.width, centre, centre + size.width)) {
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(light.copy(alpha = 0f), light, light.copy(alpha = 0f)),
                startX = x - glintLength / 2f,
                endX = x + glintLength / 2f
            ),
            start = Offset(x - glintLength / 2f, rimY),
            end = Offset(x + glintLength / 2f, rimY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** Signature: a gentle wave is drawn in under the text up to the progress, and a small dot marks where the mix has got to. */
private fun DrawScope.drawSignatureWave(palette: BeyGramPalette, progress: Float, phase: Float) {
    val calm = calmOf(progress)
    val amplitude = 2.dp.toPx()
    val baseY = size.height - 3.dp.toPx()
    val left = size.width * 0.15f
    val length = size.width * 0.7f
    fun pointAt(fraction: Float) = Offset(left + length * fraction, baseY + amplitude * sin(TAU * 1.5f * fraction))
    val drawn = progress.coerceIn(0f, 1f)
    if (drawn > 0f) {
        val path = Path()
        path.moveTo(left, baseY)
        for (step in 1..WAVE_STEPS) {
            val point = pointAt(drawn * step / WAVE_STEPS)
            path.lineTo(point.x, point.y)
        }
        drawPath(path, color = palette.textPrimary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
    val dotAlpha = 1f - 0.3f * calm * (0.5f + 0.5f * sin(TAU * phase))
    drawCircle(color = palette.accent, radius = 2.5.dp.toPx(), center = pointAt(drawn), alpha = dotAlpha)
}
