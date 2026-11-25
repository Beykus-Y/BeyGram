package com.beykus.beygram.ui.screens.profile

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.beykus.beygram.ui.components.BeyGramPalette

/** The cover scrolls with the measured identity and actions and shares the active theme colors. */
internal fun Modifier.profileSceneCover(
    palette: BeyGramPalette,
    expressive: Boolean,
): Modifier = drawWithCache {
    val upperTone = lerp(palette.surface, palette.accent, if (expressive) 0.21f else 0.08f)
    val lowerTone = lerp(palette.surface, palette.accent, if (expressive) 0.12f else 0.04f)
    val base = Brush.verticalGradient(
        0f to upperTone,
        0.55f to lowerTone,
        1f to lowerTone.copy(alpha = 0f),
        endY = size.height.coerceAtLeast(1f)
    )
    val light = Brush.radialGradient(
        colors = listOf(palette.accent.copy(alpha = if (expressive) 0.22f else 0.09f), Color.Transparent),
        center = Offset(size.width * 0.5f, size.height * 0.12f),
        radius = (size.width * 0.57f).coerceAtLeast(1f)
    )
    onDrawBehind {
        drawRect(base)
        drawRect(light)
    }
}
