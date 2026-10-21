package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.WallpaperMotif
import com.beykus.beygram.data.appearance.WallpaperPresetSpec
import com.beykus.beygram.ui.theme.LocalBeyGramTokens
import kotlin.math.ceil
import kotlin.math.max

/**
 * A deterministic vector wallpaper, also used by the picker tiles and chat preview.
 * No photo assets, allocations per frame or random-dependent positions.
 */
@Composable
internal fun PatternWallpaper(
    preset: WallpaperPresetSpec,
    intensityPercent: Int,
    modifier: Modifier = Modifier
) {
    val themeBg = LocalBeyGramTokens.current.bg
    val amoled = themeBg == Color.Black
    val dark = amoled || themeBg.luminance() < 0.25f
    val colors = preset.colors(dark, amoled).map(::Color)
    val ink = Color(preset.patternInk(dark))
    val strength = (intensityPercent.coerceIn(0, 45) / 100f * 0.72f).coerceAtMost(0.33f)
    Canvas(modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset.Zero,
                end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            )
        )
        if (!amoled) {
            // Two translucent pools soften the base gradient without obscuring messages.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(ink.copy(alpha = if (dark) 0.085f else 0.075f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.12f),
                    radius = max(size.width, size.height) * 0.72f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (dark) 0.018f else 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.10f, size.height * 0.87f),
                    radius = max(size.width, size.height) * 0.55f
                )
            )
        }
        if (strength > 0f) drawWallpaperMotif(preset.motif, ink.copy(alpha = strength))
    }
}

/** Simple, intentionally sparse motifs that remain subtle behind message bubbles. */
private fun DrawScope.drawWallpaperMotif(motif: WallpaperMotif, ink: Color) {
    val cell = 112.dp.toPx()
    val columns = ceil(size.width / cell).toInt() + 1
    val rows = ceil(size.height / cell).toInt() + 1
    val thin = 1.35.dp.toPx()
    when (motif) {
        WallpaperMotif.Waves -> {
            for (row in 0..rows * 2) {
                val y = row * cell * 0.57f
                val wave = Path().apply {
                    moveTo(-cell, y)
                    var x = -cell
                    while (x < size.width + cell) {
                        cubicTo(
                            x + cell * 0.27f, y - cell * 0.30f,
                            x + cell * 0.73f, y + cell * 0.30f,
                            x + cell, y
                        )
                        x += cell
                    }
                }
                drawPath(wave, ink, style = Stroke(width = thin))
            }
        }
        WallpaperMotif.Grid -> {
            for (col in -1..columns) {
                val x = col * cell
                drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = thin)
            }
            for (row in -1..rows) {
                val y = row * cell
                drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = thin)
            }
            for (row in 0..rows) for (col in 0..columns) {
                drawCircle(ink, radius = 2.dp.toPx(), center = Offset(col * cell, row * cell))
            }
        }
        else -> {
            for (row in 0..rows) for (col in 0..columns) {
                val x = col * cell + if (row % 2 == 0) 0f else cell * 0.5f
                val y = row * cell
                when (motif) {
                    WallpaperMotif.Dots -> {
                        drawCircle(ink, radius = cell * 0.065f, center = Offset(x + cell * 0.32f, y + cell * 0.30f))
                        drawCircle(ink, radius = cell * 0.017f, center = Offset(x + cell * 0.78f, y + cell * 0.70f))
                        drawCircle(ink, radius = cell * 0.025f, center = Offset(x + cell * 0.17f, y + cell * 0.80f))
                    }
                    WallpaperMotif.Orbits -> {
                        drawCircle(ink, radius = cell * 0.33f, center = Offset(x + cell * 0.5f, y + cell * 0.5f), style = Stroke(width = thin))
                        drawCircle(ink, radius = cell * 0.18f, center = Offset(x + cell * 0.5f, y + cell * 0.5f), style = Stroke(width = thin))
                        drawCircle(ink, radius = 3.dp.toPx(), center = Offset(x + cell * 0.73f, y + cell * 0.26f))
                    }
                    WallpaperMotif.Doodles -> {
                        drawLine(ink, Offset(x + cell * 0.25f, y + cell * 0.20f), Offset(x + cell * 0.25f, y + cell * 0.42f), thin)
                        drawLine(ink, Offset(x + cell * 0.14f, y + cell * 0.31f), Offset(x + cell * 0.36f, y + cell * 0.31f), thin)
                        drawArc(ink, 35f, 275f, false, Offset(x + cell * 0.49f, y + cell * 0.48f),
                            Size(cell * 0.29f, cell * 0.29f), style = Stroke(width = thin))
                        drawCircle(ink, radius = 2.dp.toPx(), center = Offset(x + cell * 0.73f, y + cell * 0.20f))
                    }
                    WallpaperMotif.Leaves -> {
                        val stemStart = Offset(x + cell * 0.28f, y + cell * 0.80f)
                        val stemEnd = Offset(x + cell * 0.69f, y + cell * 0.22f)
                        drawLine(ink, stemStart, stemEnd, thin)
                        drawOval(ink, Offset(x + cell * 0.23f, y + cell * 0.38f),
                            Size(cell * 0.25f, cell * 0.16f), style = Stroke(width = thin))
                        drawOval(ink, Offset(x + cell * 0.54f, y + cell * 0.48f),
                            Size(cell * 0.26f, cell * 0.16f), style = Stroke(width = thin))
                    }
                    WallpaperMotif.Arches -> {
                        drawArc(ink, 180f, 180f, false, Offset(x + cell * 0.13f, y + cell * 0.18f),
                            Size(cell * 0.70f, cell * 0.72f), style = Stroke(width = thin))
                        drawArc(ink, 180f, 180f, false, Offset(x + cell * 0.30f, y + cell * 0.36f),
                            Size(cell * 0.37f, cell * 0.35f), style = Stroke(width = thin))
                    }
                    WallpaperMotif.Confetti -> {
                        drawLine(ink, Offset(x + cell * 0.21f, y + cell * 0.25f),
                            Offset(x + cell * 0.33f, y + cell * 0.40f), 2.3.dp.toPx())
                        drawCircle(ink, radius = cell * 0.034f, center = Offset(x + cell * 0.72f, y + cell * 0.31f))
                        drawArc(ink, 20f, 150f, false, Offset(x + cell * 0.44f, y + cell * 0.64f),
                            Size(cell * 0.30f, cell * 0.20f), style = Stroke(width = thin))
                    }
                    else -> Unit
                }
            }
        }
    }
}
