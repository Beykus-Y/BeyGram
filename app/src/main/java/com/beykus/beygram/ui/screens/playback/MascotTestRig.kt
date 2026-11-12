package com.beykus.beygram.ui.screens.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.media.bgmotion.MascotFrameBuffer
import com.beykus.beygram.media.bgmotion.MascotSectionKind
import com.beykus.beygram.media.bgmotion.accent
import com.beykus.beygram.media.bgmotion.accented
import com.beykus.beygram.media.bgmotion.armLeft
import com.beykus.beygram.media.bgmotion.armRight
import com.beykus.beygram.media.bgmotion.bass
import com.beykus.beygram.media.bgmotion.beat
import com.beykus.beygram.media.bgmotion.blend
import com.beykus.beygram.media.bgmotion.bob
import com.beykus.beygram.media.bgmotion.chestTwist
import com.beykus.beygram.media.bgmotion.elbowLeft
import com.beykus.beygram.media.bgmotion.elbowRight
import com.beykus.beygram.media.bgmotion.energy
import com.beykus.beygram.media.bgmotion.footLeft
import com.beykus.beygram.media.bgmotion.footRight
import com.beykus.beygram.media.bgmotion.headNod
import com.beykus.beygram.media.bgmotion.headTilt
import com.beykus.beygram.media.bgmotion.headTurn
import com.beykus.beygram.media.bgmotion.hipShift
import com.beykus.beygram.media.bgmotion.impact
import com.beykus.beygram.media.bgmotion.intensity
import com.beykus.beygram.media.bgmotion.isAnalyzed
import com.beykus.beygram.media.bgmotion.kneeLeft
import com.beykus.beygram.media.bgmotion.kneeRight
import com.beykus.beygram.media.bgmotion.lean
import com.beykus.beygram.media.bgmotion.legLeft
import com.beykus.beygram.media.bgmotion.legRight
import com.beykus.beygram.media.bgmotion.phase
import com.beykus.beygram.media.bgmotion.phraseLoudness
import com.beykus.beygram.media.bgmotion.rotation
import com.beykus.beygram.media.bgmotion.sectionEnergy
import com.beykus.beygram.media.bgmotion.sectionKind
import com.beykus.beygram.media.bgmotion.seekDirection
import com.beykus.beygram.media.bgmotion.seekPulse
import com.beykus.beygram.media.bgmotion.silenceWeight
import com.beykus.beygram.media.bgmotion.transitionPulse
import com.beykus.beygram.ui.components.BeyGramPalette
import com.beykus.beygram.ui.components.beyGramPalette
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RIG_SIZE = 1000f
private const val GROUND_Y = 900f
private const val PELVIS_Y = 650f
private const val HEAD_PIVOT_Y = 382f

private const val HIP_LEFT_X = 446f
private const val HIP_RIGHT_X = 554f
private const val SHOULDER_LEFT_X = 368f
private const val SHOULDER_RIGHT_X = 632f
private const val SHOULDER_Y = 448f

private const val UPPER_LEG = 120f
private const val LOWER_LEG = 120f
private const val UPPER_ARM = 122f
private const val LOWER_ARM = 112f
private const val LEG_WIDTH = 25f
private const val ARM_WIDTH = 22f

// Ring strokes are shared so the ground pulse does not allocate a Stroke every frame.
private val RingStrokes = arrayOf(Stroke(1f), Stroke(2f), Stroke(3f), Stroke(4f))

private fun ringStroke(width: Float): Stroke {
    val index = (width.roundToInt() - 1).coerceIn(0, RingStrokes.lastIndex)
    return RingStrokes[index]
}

/**
 * Temporary 2D mannequin used to validate BG Motion synchronisation before a VRM model exists.
 *
 * This is deliberately a debug robot, not character art: rounded head, two eyes, joint circles and
 * two-segment limbs. Pose comes from [buffer], while phrase loudness, accents, bass, section energy,
 * silence and the beat grid are drawn as stage lighting. A short idle loop keeps the rig alive while
 * the track is analysed or paused, and seek/track-change pulses are rendered as motion streaks and a
 * stage ring.
 *
 * [buffer.version] is read inside the Canvas draw block, so publishing a native frame invalidates
 * only this drawing, not the surrounding player UI. With no native session (all zeros) the rig
 * draws a neutral, standing mannequin.
 */
@Composable
internal fun MascotTestRig(
    buffer: MascotFrameBuffer,
    modifier: Modifier = Modifier,
    debugLines: () -> List<String> = { emptyList() },
    showDebug: Boolean = false
) {
    val palette = beyGramPalette()
    val brushes = remember { MascotRigBrushCache() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reading snapshot state here is what makes a published frame redraw this canvas only.
            val frameVersion = buffer.version
            val side = min(size.width, size.height)
            if (side <= 0f) return@Canvas

            drawStage(
                values = buffer.values,
                canvasSize = size,
                side = side,
                frameVersion = frameVersion,
                palette = palette,
                brushes = brushes
            )
        }

        if (showDebug) {
            MascotDebugOverlay(
                palette = palette,
                lines = debugLines().take(7),
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

private fun DrawScope.drawStage(
    values: FloatArray,
    canvasSize: Size,
    side: Float,
    @Suppress("UNUSED_PARAMETER") frameVersion: Int,
    palette: BeyGramPalette,
    brushes: MascotRigBrushCache
) {
    val analyzed = values.isAnalyzed()
    val energy = if (analyzed) unit(values.energy()) else 0f
    val silence = if (analyzed) unit(values.silenceWeight()) else 0f
    val accentLevel = if (analyzed) unit(values.accent()) else 0f
    val bassLevel = if (analyzed) unit(values.bass()) else 0f
    val sectionEnergy = if (analyzed) unit(values.sectionEnergy()) else 0f
    val performance = if (analyzed) {
        val raw = values.intensity()
        if (raw.isFinite()) raw.coerceIn(0f, 1.5f) else 1f
    } else {
        1f
    }
    val section = if (analyzed) values.sectionKind() else MascotSectionKind.Unknown
    val phrase = if (analyzed) {
        val raw = values.phraseLoudness()
        if (raw.isFinite()) raw.coerceIn(-1f, 1f) else -1f
    } else {
        -1f
    }
    val accented = analyzed && values.accented() >= 0.5f
    val phase = if (values.phase().isFinite()) {
        (values.phase() - floor(values.phase())).coerceIn(0f, 1f)
    } else {
        0f
    }
    val idlePhase = if (analyzed) 0f else phase
    val seek = values.seekPulse()
    val seekDir = values.seekDirection()
    val transition = values.transitionPulse()
    val blend = if (analyzed) unit(values.blend()) else 0f
    val beatPhase = if (analyzed) {
        val raw = values.beat()
        if (raw.isFinite()) (raw - floor(raw)).coerceIn(0f, 1f) else 0f
    } else {
        0f
    }

    // Backdrop: energy and section tint are quantised into a few buckets so the radial brush is
    // rebuilt only when the look changes, not on every frame.
    val tint = sectionTint(section, palette)
    val phraseGlow = max(phrase, 0f)
    val strength = if (analyzed) {
        (0.08f + sectionEnergy * 0.30f + phraseGlow * 0.10f) * (1f - silence * 0.55f)
    } else {
        0f
    }
    val tintBucket = (strength * 10f).roundToInt().coerceIn(0, 5)
    // While the track is still being analysed, keep the cover visible behind a
    // light scrim instead of replacing it with an opaque stage.
    val scrimAlpha = if (analyzed) 0.94f else 0.58f
    val centerColor = lerp(
        palette.screen.copy(alpha = scrimAlpha),
        tint.copy(alpha = scrimAlpha),
        tintBucket * 0.040f
    )
    val edgeColor = palette.screen.copy(alpha = scrimAlpha)
    drawRect(brush = brushes.backdrop(canvasSize, centerColor, edgeColor))

    val scale = side / RIG_SIZE
    val offsetX = (canvasSize.width - side) * 0.5f
    val offsetY = (canvasSize.height - side) * 0.5f
    withTransform({
        translate(offsetX, offsetY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawAura(
            analyzed = analyzed,
            phrase = phrase,
            sectionEnergy = sectionEnergy,
            silence = silence,
            performance = performance,
            accentColor = palette.accent,
            brushes = brushes
        )
        drawGroundPulse(
            analyzed = analyzed,
            accentLevel = accentLevel,
            bassLevel = bassLevel,
            accented = accented,
            beatPhase = beatPhase,
            silence = silence,
            bobLevel = poseValue(analyzed, values.bob()),
            accentColor = palette.accent,
            secondaryColor = palette.textSecondary
        )
        drawSeekStreaks(seek, seekDir, palette.accent)
        drawTransitionRing(transition, palette.accent)
        drawMascotBody(
            values = values,
            analyzed = analyzed,
            energy = energy,
            performance = performance,
            silence = silence,
            phrase = phrase,
            bassLevel = bassLevel,
            accentLevel = accentLevel,
            accented = accented,
            phase = phase,
            idlePhase = idlePhase,
            seek = seek,
            seekDir = seekDir,
            transition = transition,
            blend = blend,
            palette = palette
        )
    }
}

private fun DrawScope.drawAura(
    analyzed: Boolean,
    phrase: Float,
    sectionEnergy: Float,
    silence: Float,
    performance: Float,
    accentColor: Color,
    brushes: MascotRigBrushCache
) {
    if (!analyzed || phrase < 0f) return
    val level = (phrase * 3f + sectionEnergy * 2f + performance * 0.5f)
        .times(1f - silence * 0.7f)
        .roundToInt()
        .coerceIn(0, 4)
    if (level == 0) return
    drawCircle(
        brush = brushes.aura(accentColor, level),
        radius = 430f,
        center = Offset(500f, 470f)
    )
}

private fun DrawScope.drawGroundPulse(
    analyzed: Boolean,
    accentLevel: Float,
    bassLevel: Float,
    accented: Boolean,
    beatPhase: Float,
    silence: Float,
    bobLevel: Float,
    accentColor: Color,
    secondaryColor: Color
) {
    val dim = 1f - silence * 0.55f
    val lift = bobLevel.coerceIn(0f, 1.5f)
    val shadowScale = 1f - lift * 0.18f
    drawOval(
        color = secondaryColor.copy(alpha = 0.07f * dim * (1f - lift * 0.25f)),
        topLeft = Offset(500f - 295f * shadowScale, GROUND_Y - 38f * shadowScale),
        size = Size(590f * shadowScale, 76f * shadowScale)
    )

    if (!analyzed) return

    val travel = 1f - beatPhase
    val pulseRadius = 220f + bassLevel * 70f
    val outerRadius = pulseRadius * (0.70f + 0.30f * travel)
    val ringAlpha = (0.06f + accentLevel * 0.16f + bassLevel * 0.07f +
        if (accented) 0.08f else 0f) * dim
    drawOval(
        color = accentColor.copy(alpha = ringAlpha.coerceIn(0f, 0.34f)),
        topLeft = Offset(500f - outerRadius, GROUND_Y - outerRadius * 0.21f),
        size = Size(outerRadius * 2f, outerRadius * 0.42f),
        style = ringStroke(1f + accentLevel * 2.5f)
    )

    val innerRadius = pulseRadius * 0.52f
    drawOval(
        color = accentColor.copy(alpha = (0.05f + bassLevel * 0.10f) * dim),
        topLeft = Offset(500f - innerRadius, GROUND_Y - innerRadius * 0.20f),
        size = Size(innerRadius * 2f, innerRadius * 0.40f),
        style = ringStroke(1f + bassLevel)
    )

    if (accented) {
        val burstRadius = pulseRadius * (0.86f + 0.14f * travel)
        drawOval(
            color = accentColor.copy(alpha = 0.16f * dim),
            topLeft = Offset(500f - burstRadius, GROUND_Y - burstRadius * 0.22f),
            size = Size(burstRadius * 2f, burstRadius * 0.44f),
            style = ringStroke(2f)
        )
    }
}

private fun DrawScope.drawSeekStreaks(
    seek: Float,
    direction: Float,
    accentColor: Color
) {
    if (seek <= 0.01f) return
    val dir = if (direction < 0f) -1f else 1f
    val alpha = seek * 0.34f
    for (i in 0..2) {
        val y = 470f + i * 82f
        val tailX = 500f - dir * (175f + i * 28f)
        val length = 150f + i * 42f
        drawLine(
            color = accentColor.copy(alpha = alpha * (1f - i * 0.22f)),
            start = Offset(tailX, y),
            end = Offset(tailX - dir * length, y),
            strokeWidth = 6f - i,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTransitionRing(
    transition: Float,
    accentColor: Color
) {
    if (transition <= 0.01f) return
    val expansion = 1f - transition
    val radiusX = 250f + expansion * 150f
    val radiusY = 55f + expansion * 20f
    drawOval(
        color = accentColor.copy(alpha = transition * 0.18f),
        topLeft = Offset(500f - radiusX, 680f - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
        style = ringStroke(3f)
    )
}

private fun DrawScope.drawMascotBody(
    values: FloatArray,
    analyzed: Boolean,
    energy: Float,
    performance: Float,
    silence: Float,
    phrase: Float,
    bassLevel: Float,
    accentLevel: Float,
    accented: Boolean,
    phase: Float,
    idlePhase: Float,
    seek: Float,
    seekDir: Float,
    transition: Float,
    blend: Float,
    palette: BeyGramPalette
) {
    val poseAmp = 0.88f + energy * 0.32f
    val idle = if (analyzed) 0f else sin(idlePhase * (2f * PI.toFloat()))
    // Native bob is positive up, negative down; canvas y grows downwards.
    val bob = if (analyzed) {
        -poseValue(analyzed, values.bob()) * 34f * poseAmp
    } else {
        idle * 7f
    }
    val hipShift = poseValue(analyzed, values.hipShift()) * 34f * poseAmp
    val idleSway = if (analyzed) 0f else idle * 2.2f
    val seekKick = seek * seekDir * 13f
    val seekTilt = seek * seekDir * 5.5f
    val wholeRotation = poseValue(analyzed, values.rotation()) * 7f + idleSway + seekTilt
    val impact = poseValue(analyzed, values.impact()) * 0.085f

    val base = lerp(palette.textSecondary, palette.textPrimary, 0.48f)
    val desaturated = desaturate(base, silence * 0.85f)
    val phraseLight = max(phrase, 0f) * 0.10f
    val accentedLight = if (accented) 0.06f else 0f
    val performanceLight = (performance - 1f) * 0.10f
    val idleBreath = if (analyzed) 0f else (idle + 1f) * 0.025f
    val body = brighten(
        desaturated,
        0.80f + energy * 0.30f + phraseLight + accentedLight + performanceLight +
            idleBreath + transition * 0.06f
    ).copy(alpha = 1f - silence * 0.34f)
    val edge = lerp(body, palette.textSecondary, 0.82f).copy(alpha = body.alpha)
    val joint = lerp(body, palette.accentSoft, 0.30f).copy(alpha = body.alpha)
    val accentColor = lerp(palette.accent, palette.textPrimary, 0.10f + energy * 0.15f)
    val screenColor = palette.screen

    val leanDeg = poseValue(analyzed, values.lean()) * 10f * poseAmp +
        poseValue(analyzed, values.chestTwist()) * 5f * poseAmp
    val headNod = if (analyzed) {
        -poseValue(analyzed, values.headNod()) * 14f * poseAmp
    } else {
        idle * 2.8f
    }
    val headTilt = poseValue(analyzed, values.headTilt()) * 16f * poseAmp +
        if (analyzed) 0f else idle * 1.8f
    val headTurn = poseValue(analyzed, values.headTurn()) * 26f * poseAmp

    val leftHip = 3f + poseValue(analyzed, values.legLeft()) * 24f * poseAmp
    val rightHip = -3f + poseValue(analyzed, values.legRight()) * 24f * poseAmp
    // Native knees are 0..1 bend. Bend each lower leg away from the centre line.
    val leftKnee = poseValue(analyzed, values.kneeLeft()) * 26f * poseAmp
    val rightKnee = -poseValue(analyzed, values.kneeRight()) * 26f * poseAmp
    val leftFoot = poseValue(analyzed, values.footLeft()) * 22f
    val rightFoot = poseValue(analyzed, values.footRight()) * 22f

    // Native arms are negative-outward on the left and positive-outward on the right.
    val idleArm = if (analyzed) 0f else idle * 2.5f
    val leftShoulder = 7f - poseValue(analyzed, values.armLeft()) * 65f * poseAmp - idleArm
    val rightShoulder = -7f - poseValue(analyzed, values.armRight()) * 65f * poseAmp + idleArm
    val leftElbow = 5f + poseValue(analyzed, values.elbowLeft()) * 58f * poseAmp
    val rightElbow = -5f - poseValue(analyzed, values.elbowRight()) * 58f * poseAmp

    withTransform({
        translate(hipShift + seekKick, bob)
        scale(
            1f + impact + transition * 0.045f,
            1f - impact + transition * 0.045f,
            pivot = Offset(500f, GROUND_Y)
        )
        rotate(wholeRotation, pivot = Offset(500f, GROUND_Y))
    }) {
        drawLeg(
            hipX = HIP_LEFT_X,
            side = -1f,
            hipDeg = leftHip,
            kneeDeg = leftKnee,
            footLift = leftFoot,
            body = body,
            edge = edge,
            joint = joint,
            accentColor = accentColor
        )
        drawLeg(
            hipX = HIP_RIGHT_X,
            side = 1f,
            hipDeg = rightHip,
            kneeDeg = rightKnee,
            footLift = rightFoot,
            body = body,
            edge = edge,
            joint = joint,
            accentColor = accentColor
        )

        withTransform({ rotate(leanDeg, pivot = Offset(500f, PELVIS_Y)) }) {
            drawArm(
                shoulderX = SHOULDER_LEFT_X,
                shoulderDeg = leftShoulder,
                elbowDeg = leftElbow,
                body = body,
                edge = edge,
                joint = joint,
                accentColor = accentColor
            )
            drawArm(
                shoulderX = SHOULDER_RIGHT_X,
                shoulderDeg = rightShoulder,
                elbowDeg = rightElbow,
                body = body,
                edge = edge,
                joint = joint,
                accentColor = accentColor
            )

            drawTorso(
                body = body,
                edge = edge,
                joint = joint,
                accentColor = accentColor,
                bassLevel = bassLevel,
                accentLevel = accentLevel,
                accented = accented,
                blend = blend,
                energy = energy,
                silence = silence,
                palette = palette
            )
            drawNeck(body = body, edge = edge, joint = joint)

            withTransform({
                translate(headTurn, headNod)
                rotate(headTilt, pivot = Offset(500f, HEAD_PIVOT_Y))
            }) {
                drawHead(
                    body = body,
                    edge = edge,
                    joint = joint,
                    accentColor = accentColor,
                    screenColor = screenColor,
                    energy = energy,
                    silence = silence,
                    phase = phase,
                    analyzed = analyzed
                )
            }
        }
    }
}

private fun DrawScope.drawTorso(
    body: Color,
    edge: Color,
    joint: Color,
    accentColor: Color,
    bassLevel: Float,
    accentLevel: Float,
    accented: Boolean,
    blend: Float,
    energy: Float,
    silence: Float,
    palette: BeyGramPalette
) {
    drawRoundRect(
        color = edge,
        topLeft = Offset(390f, 408f),
        size = Size(220f, 248f),
        cornerRadius = CornerRadius(52f)
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(398f, 416f),
        size = Size(204f, 232f),
        cornerRadius = CornerRadius(46f)
    )

    // Pelvis plate, drawn inside the lean so the torso reads as one jointed unit.
    drawRoundRect(
        color = edge,
        topLeft = Offset(426f, 626f),
        size = Size(148f, 78f),
        cornerRadius = CornerRadius(30f)
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(434f, 634f),
        size = Size(132f, 62f),
        cornerRadius = CornerRadius(24f)
    )
    drawCircle(joint, radius = 19f, center = Offset(500f, 666f))
    drawCircle(accentColor.copy(alpha = 0.55f), radius = 7f, center = Offset(500f, 666f))

    drawRoundRect(
        color = accentColor.copy(alpha = 0.12f + blend * 0.08f),
        topLeft = Offset(454f, 462f),
        size = Size(92f, 110f),
        cornerRadius = CornerRadius(25f)
    )

    val coreRadius = 26f + bassLevel * 10f + if (accented) 4f else 0f
    val coreAlpha = (0.72f + accentLevel * 0.20f + blend * 0.08f) *
        (1f - silence * 0.45f)
    drawCircle(
        color = accentColor.copy(alpha = (0.12f + bassLevel * 0.10f) * (1f - silence * 0.5f)),
        radius = coreRadius + 17f,
        center = Offset(500f, 514f)
    )
    drawCircle(accentColor.copy(alpha = coreAlpha.coerceIn(0f, 1f)), radius = coreRadius + 5f, center = Offset(500f, 514f))
    drawCircle(body, radius = coreRadius, center = Offset(500f, 514f))
    drawCircle(
        color = lerp(accentColor, palette.textPrimary, 0.45f)
            .copy(alpha = coreAlpha.coerceIn(0f, 1f)),
        radius = coreRadius * 0.46f,
        center = Offset(500f, 514f)
    )

    val ventColor = palette.textSecondary.copy(
        alpha = (0.18f + energy * 0.22f) * (1f - silence * 0.5f)
    )
    for (i in 0 until 3) {
        val y = 586f + i * 14f
        drawLine(
            color = ventColor,
            start = Offset(468f, y),
            end = Offset(532f, y),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }

    // Shoulder socket rings sit on top of the torso so the arm attachment is legible.
    drawCircle(joint, radius = 17f, center = Offset(SHOULDER_LEFT_X + 24f, SHOULDER_Y))
    drawCircle(edge, radius = 9f, center = Offset(SHOULDER_LEFT_X + 24f, SHOULDER_Y))
    drawCircle(joint, radius = 17f, center = Offset(SHOULDER_RIGHT_X - 24f, SHOULDER_Y))
    drawCircle(edge, radius = 9f, center = Offset(SHOULDER_RIGHT_X - 24f, SHOULDER_Y))
}

private fun DrawScope.drawNeck(body: Color, edge: Color, joint: Color) {
    drawRoundRect(
        color = edge,
        topLeft = Offset(470f, 352f),
        size = Size(60f, 74f),
        cornerRadius = CornerRadius(18f)
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(476f, 358f),
        size = Size(48f, 62f),
        cornerRadius = CornerRadius(14f)
    )
    drawCircle(joint, radius = 10f, center = Offset(500f, 372f))
}

private fun DrawScope.drawHead(
    body: Color,
    edge: Color,
    joint: Color,
    accentColor: Color,
    screenColor: Color,
    energy: Float,
    silence: Float,
    phase: Float,
    analyzed: Boolean
) {
    val dim = 1f - silence * 0.45f

    // Antenna and its soft glow.
    drawLine(
        color = edge,
        start = Offset(500f, 190f),
        end = Offset(500f, 154f),
        strokeWidth = 7f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = accentColor.copy(alpha = 0.16f * dim),
        radius = 20f + energy * 5f,
        center = Offset(500f, 148f)
    )
    drawCircle(
        color = accentColor.copy(alpha = 0.90f * dim),
        radius = 9f + energy * 2f,
        center = Offset(500f, 148f)
    )

    drawRoundRect(
        color = edge,
        topLeft = Offset(404f, 184f),
        size = Size(192f, 184f),
        cornerRadius = CornerRadius(56f)
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(412f, 192f),
        size = Size(176f, 168f),
        cornerRadius = CornerRadius(50f)
    )

    // Ear bolts.
    drawCircle(edge, radius = 14f, center = Offset(407f, 278f))
    drawCircle(joint, radius = 8f, center = Offset(407f, 278f))
    drawCircle(edge, radius = 14f, center = Offset(593f, 278f))
    drawCircle(joint, radius = 8f, center = Offset(593f, 278f))

    drawRoundRect(
        color = screenColor.copy(alpha = 0.92f),
        topLeft = Offset(428f, 242f),
        size = Size(144f, 66f),
        cornerRadius = CornerRadius(31f)
    )

    if (analyzed) {
        val scanX = 440f + 120f * phase
        drawLine(
            color = accentColor.copy(alpha = (0.18f + energy * 0.22f) * dim),
            start = Offset(scanX, 252f),
            end = Offset(scanX, 298f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    val eyeY = 275f
    val blink = if (!analyzed && phase > 0.965f) 0.22f else 1f
    val eyeRadius = (11f + energy * 1.5f) * blink
    val eyeColor = lerp(accentColor, Color.White, 0.30f)
    val eyeGlow = accentColor.copy(alpha = 0.22f * dim)

    drawCircle(eyeGlow, radius = 22f, center = Offset(465f, eyeY))
    drawCircle(eyeColor.copy(alpha = 0.95f * dim), radius = eyeRadius, center = Offset(465f, eyeY))
    drawCircle(Color.White.copy(alpha = 0.72f * dim), radius = 3f, center = Offset(462f, eyeY - 3f))

    drawCircle(eyeGlow, radius = 22f, center = Offset(535f, eyeY))
    drawCircle(eyeColor.copy(alpha = 0.95f * dim), radius = eyeRadius, center = Offset(535f, eyeY))
    drawCircle(Color.White.copy(alpha = 0.72f * dim), radius = 3f, center = Offset(532f, eyeY - 3f))

    // Small speaker grille under the visor: enough detail to read as a robot, not character art.
    val grille = accentColor.copy(alpha = 0.30f * dim)
    drawRoundRect(grille, topLeft = Offset(480f, 322f), size = Size(9f, 6f), cornerRadius = CornerRadius(3f))
    drawRoundRect(grille, topLeft = Offset(495f, 322f), size = Size(9f, 6f), cornerRadius = CornerRadius(3f))
    drawRoundRect(grille, topLeft = Offset(510f, 322f), size = Size(9f, 6f), cornerRadius = CornerRadius(3f))
}

private fun DrawScope.drawArm(
    shoulderX: Float,
    shoulderDeg: Float,
    elbowDeg: Float,
    body: Color,
    edge: Color,
    joint: Color,
    accentColor: Color
) {
    withTransform({
        translate(shoulderX, SHOULDER_Y)
        rotate(shoulderDeg, pivot = Offset.Zero)
    }) {
        drawLimbSegment(UPPER_ARM, ARM_WIDTH, body, edge, joint)
        withTransform({
            translate(0f, UPPER_ARM)
            rotate(elbowDeg, pivot = Offset.Zero)
        }) {
            drawLimbSegment(LOWER_ARM, ARM_WIDTH, body, edge, joint)
            drawCircle(edge, radius = 20f, center = Offset(0f, LOWER_ARM))
            drawCircle(body, radius = 14f, center = Offset(0f, LOWER_ARM))
            drawCircle(accentColor.copy(alpha = 0.75f), radius = 5f, center = Offset(0f, LOWER_ARM))
        }
    }
}

private fun DrawScope.drawLeg(
    hipX: Float,
    side: Float,
    hipDeg: Float,
    kneeDeg: Float,
    footLift: Float,
    body: Color,
    edge: Color,
    joint: Color,
    accentColor: Color
) {
    withTransform({
        translate(hipX, PELVIS_Y)
        rotate(hipDeg, pivot = Offset.Zero)
    }) {
        drawLimbSegment(UPPER_LEG, LEG_WIDTH, body, edge, joint)
        withTransform({
            translate(0f, UPPER_LEG)
            rotate(kneeDeg, pivot = Offset.Zero)
        }) {
            drawLimbSegment(LOWER_LEG, LEG_WIDTH, body, edge, joint)
            withTransform({ translate(0f, LOWER_LEG - footLift) }) {
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(-35f, -12f),
                    size = Size(70f, 23f),
                    cornerRadius = CornerRadius(11f)
                )
                drawRoundRect(
                    color = body,
                    topLeft = Offset(-31f, -9f),
                    size = Size(62f, 17f),
                    cornerRadius = CornerRadius(8f)
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.80f),
                    radius = 5f,
                    center = Offset(side * 21f, 0f)
                )
            }
        }
    }
}

private fun DrawScope.drawLimbSegment(
    length: Float,
    width: Float,
    body: Color,
    edge: Color,
    joint: Color
) {
    drawLine(
        color = edge,
        start = Offset.Zero,
        end = Offset(0f, length),
        strokeWidth = width + 9f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = body,
        start = Offset.Zero,
        end = Offset(0f, length),
        strokeWidth = width,
        cap = StrokeCap.Round
    )
    drawCircle(edge, radius = width * 0.62f + 4f)
    drawCircle(joint, radius = width * 0.62f)
}

private fun sectionTint(kind: MascotSectionKind, palette: BeyGramPalette): Color = when (kind) {
    MascotSectionKind.Intro -> palette.accentSoft
    MascotSectionKind.Build -> lerp(palette.accentSoft, palette.accent, 0.60f)
    MascotSectionKind.Main -> palette.accent
    MascotSectionKind.Breakdown -> palette.textSecondary
    MascotSectionKind.Outro -> palette.accentSoft
    MascotSectionKind.Unknown -> palette.textSecondary
}

// Native values are expected to be normalised. The clamps keep a bad frame from throwing or
// producing invisible geometry (NaN/Infinity) without allocating anything in the draw pass.
private fun poseValue(analyzed: Boolean, value: Float): Float {
    if (!analyzed || !value.isFinite()) return 0f
    return value.coerceIn(-1.5f, 1.5f)
}

private fun unit(value: Float): Float {
    if (!value.isFinite()) return 0f
    return value.coerceIn(0f, 1f)
}

private fun desaturate(color: Color, amount: Float): Color {
    val clamped = amount.coerceIn(0f, 1f)
    if (clamped <= 0f) return color
    val grey = (color.red + color.green + color.blue) / 3f
    return lerp(color, Color(grey, grey, grey, color.alpha), clamped)
}

private fun brighten(color: Color, factor: Float): Color {
    if (factor >= 1f) return lerp(color, Color.White, (factor - 1f).coerceIn(0f, 1f))
    return lerp(color, Color.Black, (1f - factor).coerceIn(0f, 1f))
}

/**
 * Cached shaders for the rig. The backdrop depends on canvas pixels, the aura on the 1000x1000
 * stage; both are keyed by their inputs so a steady frame reuses the same Brush objects.
 */
private class MascotRigBrushCache {
    private var backdropWidth = Float.NaN
    private var backdropHeight = Float.NaN
    private var backdropCenter = Color.Transparent
    private var backdropEdge = Color.Transparent
    private var backdropBrush: Brush? = null

    private var auraColor = Color.Transparent
    private var auraLevel = Int.MIN_VALUE
    private var auraBrush: Brush? = null

    fun backdrop(size: Size, center: Color, edge: Color): Brush {
        val cached = backdropBrush
        if (cached != null &&
            backdropWidth == size.width &&
            backdropHeight == size.height &&
            backdropCenter == center &&
            backdropEdge == edge
        ) {
            return cached
        }
        val created = Brush.radialGradient(
            0f to center,
            0.58f to lerp(center, edge, 0.58f),
            1f to edge,
            center = Offset(size.width * 0.5f, size.height * 0.44f),
            radius = max(size.width, size.height) * 0.78f
        )
        backdropWidth = size.width
        backdropHeight = size.height
        backdropCenter = center
        backdropEdge = edge
        backdropBrush = created
        return created
    }

    fun aura(color: Color, level: Int): Brush {
        val cached = auraBrush
        if (cached != null && auraColor == color && auraLevel == level) return cached

        val strength = 0.025f + level.coerceIn(0, 4) * 0.030f
        val created = Brush.radialGradient(
            0f to color.copy(alpha = strength),
            0.55f to color.copy(alpha = strength * 0.42f),
            1f to color.copy(alpha = 0f),
            center = Offset(500f, 470f),
            radius = 430f
        )
        auraColor = color
        auraLevel = level
        auraBrush = created
        return created
    }
}

@Composable
private fun MascotDebugOverlay(
    palette: BeyGramPalette,
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .padding(10.dp)
            .widthIn(max = 280.dp)
            .background(palette.screen.copy(alpha = 0.84f), shape)
            .border(1.dp, palette.accentSoft.copy(alpha = 0.45f), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = "2D TEST RIG",
            color = palette.accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            maxLines = 1
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
