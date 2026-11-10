package com.beykus.beygram.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.usesGlassPanels

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

/**
 * Fill of a modal bottom sheet. Material 3 lays its sheets on the low surface container, one tonal step above the
 * screen; the other styles keep the plain surface they have always drawn.
 */
@Composable
fun sheetSurfaceColor(): Color {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    return when {
        design.style == DesignStyle.Material3 -> MaterialTheme.colorScheme.surfaceContainerLow
        // The same dense glass fill as the attachment menu, so bottom sheets read as part of the glass style.
        design.usesGlassPanels -> palette.surface.copy(alpha = 0.94f)
        else -> palette.surface
    }
}

/**
 * Tint of the decorative icon at the start of a settings row. BeyGram Signature keeps the accent for actions and
 * unread counts, so the row icons take the ink; the other styles keep their accent icons.
 */
@Composable
fun settingsIconTint(): Color {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    return if (design.style == DesignStyle.Signature) palette.textPrimary else palette.accent
}
