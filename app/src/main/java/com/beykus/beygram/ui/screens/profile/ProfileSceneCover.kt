package com.beykus.beygram.ui.screens.profile

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.ProfileAccentTheme
import com.beykus.beygram.ui.components.BeyGramPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class AmbientOrb(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val isDiamond: Boolean,
    val depth: Float,
    val alphaScale: Float
)

private val AmbientPatternOrbs = listOf(
    AmbientOrb(0.14f, 0.14f, 11f, true, 0.25f, 0.85f),
    AmbientOrb(0.86f, 0.16f, 10f, true, 0.30f, 0.80f),
    AmbientOrb(0.24f, 0.34f, 6.5f, false, 0.45f, 0.65f),
    AmbientOrb(0.77f, 0.32f, 7f, false, 0.40f, 0.70f),
    AmbientOrb(0.10f, 0.50f, 5f, false, 0.55f, 0.50f),
    AmbientOrb(0.90f, 0.48f, 5.5f, true, 0.50f, 0.55f),
    AmbientOrb(0.32f, 0.11f, 4.5f, false, 0.20f, 0.60f),
    AmbientOrb(0.69f, 0.10f, 4.5f, true, 0.22f, 0.60f)
)

/**
 * Multi-layered adaptive spatial cover for Profile 2.0 C (#1138, #1235).
 * Reads [collapse] and [reveal] strictly in the draw pass so scroll and reveal never trigger recomposition.
 */
internal fun Modifier.profileSceneCover(
    palette: BeyGramPalette,
    accentTheme: ProfileAccentTheme? = null,
    density: ProfileSceneDensity = ProfileSceneDensity.Balanced,
    collapse: () -> Float = { 0f },
    reveal: () -> Float = { 0f },
    reduceMotion: Boolean = false
): Modifier = drawWithCache {
    val isDark = palette.screen.luminance() < 0.5f
    val rawColors = if (isDark) {
        accentTheme?.darkBackground?.takeIf { it.isNotEmpty() }
            ?: accentTheme?.darkPalette.orEmpty()
    } else {
        accentTheme?.lightBackground?.takeIf { it.isNotEmpty() }
            ?: accentTheme?.lightPalette.orEmpty()
    }
    val primaryColor = rawColors.firstOrNull()?.let { Color(0xFF000000L or it.toLong()) } ?: palette.accent
    val secondaryColor = rawColors.getOrNull(1)?.let { Color(0xFF000000L or it.toLong()) }
        ?: lerp(primaryColor, palette.accent, 0.35f)

    val (topMix, midMix, bloomAlpha, patternAlpha) = when (density) {
        ProfileSceneDensity.Calm -> CoverIntensity(0.08f, 0.03f, 0.08f, 0f)
        ProfileSceneDensity.Balanced -> CoverIntensity(0.20f, 0.09f, 0.19f, 0.10f)
        ProfileSceneDensity.Rich -> CoverIntensity(0.32f, 0.16f, 0.30f, 0.18f)
    }

    val upperTone = lerp(palette.surface, primaryColor, topMix)
    val midTone = lerp(palette.surface, secondaryColor, midMix)
    val baseGradient = Brush.verticalGradient(
        0f to upperTone,
        0.52f to midTone,
        1f to midTone.copy(alpha = 0f),
        endY = size.height.coerceAtLeast(1f)
    )
    val radialBloom = Brush.radialGradient(
        colors = listOf(
            primaryColor.copy(alpha = bloomAlpha),
            secondaryColor.copy(alpha = bloomAlpha * 0.4f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.5f, size.height * 0.22f),
        radius = (size.width * 0.62f).coerceAtLeast(1f)
    )
    val diamondPath = Path()

    onDrawBehind {
        val c = collapse().coerceIn(0f, 1f)
        val r = reveal().coerceIn(0f, 1f)
        val coverFade = (1f - c * 0.85f).coerceIn(0.15f, 1f)
        val revealBoost = 1f + r * 0.18f

        drawRect(baseGradient, alpha = coverFade)
        drawRect(radialBloom, alpha = (coverFade * revealBoost).coerceAtMost(1f))

        if (patternAlpha > 0f && !reduceMotion) {
            val patternFade = (1f - c * 1.4f).coerceIn(0f, 1f) * (1f - r * 0.45f)
            if (patternFade > 0.01f) {
                val parallaxShift = c * 28.dp.toPx() + r * 10.dp.toPx()
                val orbColor = lerp(primaryColor, palette.textPrimary, if (isDark) 0.25f else 0.12f)
                AmbientPatternOrbs.forEach { orb ->
                    val cx = size.width * orb.xFraction
                    val cy = size.height * orb.yFraction - parallaxShift * orb.depth
                    val radius = orb.radiusDp.dp.toPx()
                    val alpha = patternAlpha * orb.alphaScale * patternFade
                    if (cy + radius > 0f && cy - radius < size.height * 0.68f) {
                        if (orb.isDiamond) {
                            drawFourPointStar(diamondPath, cx, cy, radius, orbColor.copy(alpha = alpha))
                        } else {
                            drawCircle(orbColor.copy(alpha = alpha), radius = radius, center = Offset(cx, cy))
                        }
                    }
                }
            }
        }
    }
}

private data class CoverIntensity(
    val topMix: Float,
    val midMix: Float,
    val bloomAlpha: Float,
    val patternAlpha: Float
)

private fun DrawScope.drawFourPointStar(
    path: Path,
    cx: Float,
    cy: Float,
    outerRadius: Float,
    color: Color
) {
    val innerRadius = outerRadius * 0.38f
    path.rewind()
    for (i in 0 until 8) {
        val angle = (i * PI / 4.0) - PI / 2.0
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = Fill)
}
