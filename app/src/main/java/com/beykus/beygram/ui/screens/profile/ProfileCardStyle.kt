package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.components.groupSurfaceColor
import com.beykus.beygram.ui.design.LocalDesignSystem

/** Shared card rhythm for the profile, Premium, Stars and gifts screens. */
private val CardScreenInset = 16.dp
private val CardGap = 12.dp

/**
 * A grouped surface card. Each card gets half the gap above and below, so two neighbouring cards keep a 12 dp
 * gap and their rounded corners never touch. The corner, the fill and the depth follow the style in effect through
 * the same group-card tokens as the settings pages: Default keeps its flat 20 dp card on the plain surface, Material 2
 * gets a 4 dp card with its 2 dp elevation, Material 3 a tonal container and Signature its quiet layered depth.
 */
@Composable
internal fun Modifier.profileCardSurface(color: Color = groupSurfaceColor()): Modifier {
    val design = LocalDesignSystem.current
    val shape = RoundedCornerShape(design.shapes.groupCard)
    val elevation = design.surfaces.cardElevation
    return padding(horizontal = CardScreenInset, vertical = CardGap / 2)
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false) else Modifier)
        .clip(shape)
        .background(color)
}
