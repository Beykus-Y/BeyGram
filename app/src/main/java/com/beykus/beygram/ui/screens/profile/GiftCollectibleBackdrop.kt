package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.beykus.beygram.domain.model.GiftCollectibleDetails

/**
 * The backdrop of an upgraded gift: a radial gradient from the backdrop's centre colour to its edge colour, drawn once
 * behind the content. A gift without collectible details, or with a backdrop missing a colour, shows no backdrop.
 */
@Composable
internal fun GiftCollectibleBackdrop(
    details: GiftCollectibleDetails?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val center = details?.backdropCenterColor
    val edge = details?.backdropEdgeColor
    val backdrop = if (center != null && edge != null) {
        Brush.radialGradient(colors = listOf(opaqueColor(center), opaqueColor(edge)))
    } else {
        null
    }
    Box(
        if (backdrop != null) modifier.background(backdrop) else modifier,
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** TDLib colours are RGB without alpha, so the alpha channel is set to opaque. */
internal fun opaqueColor(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)
