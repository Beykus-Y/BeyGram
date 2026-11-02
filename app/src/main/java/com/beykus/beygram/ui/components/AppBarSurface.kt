package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.zIndex
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.TopBarVariant

/**
 * The surface behind a screen's top bar (back button and title) for styles with an elevated app bar. Material 2 draws
 * the classic app bar: an opaque surface with the style's bar elevation, drawn above the content below it so the
 * shadow is not covered by the list. Every other style returns the modifier unchanged, so their bars stay as they were.
 */
@Composable
fun Modifier.topAppBarSurface(): Modifier {
    val design = LocalDesignSystem.current
    val palette = beyGramPalette()
    return when (design.components.topBar) {
        TopBarVariant.Elevated -> this
            .zIndex(1f)
            .shadow(design.surfaces.barElevation, RectangleShape, clip = false)
            .background(palette.surface)
        TopBarVariant.Glass, TopBarVariant.Signature, TopBarVariant.Flat -> this
    }
}
