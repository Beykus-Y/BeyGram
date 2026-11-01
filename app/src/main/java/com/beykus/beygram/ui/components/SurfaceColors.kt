package com.beykus.beygram.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.LocalDesignSystem

/**
 * Fill of a grouped surface (a settings card, the archive row). Material 3 separates groups with its tonal container
 * ramp instead of the plain surface; the other styles keep the surface colour they have always used, so Default and
 * Material 2 are unchanged.
 */
@Composable
fun groupSurfaceColor(): Color {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    return when (design.style) {
        DesignStyle.Material3 -> MaterialTheme.colorScheme.surfaceContainer
        else -> palette.surface
    }
}
