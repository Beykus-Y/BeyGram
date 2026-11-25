package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.beykus.beygram.domain.model.GiftCollectibleDetails

/**
 * The backdrop of an upgraded gift: a radial gradient from the backdrop's centre colour to its edge colour, drawn once
 * behind the content. A gift without collectible details, or with a backdrop missing either colour, shows no backdrop.
 */
@Composable
internal fun GiftCollectibleBackdrop(
    details: GiftCollectibleDetails?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val endpoints = backdropEndpoints(details)
    val backdrop = remember(endpoints) {
        endpoints?.let { (center, edge) ->
            Brush.radialGradient(colors = listOf(Color(center), Color(edge)))
        }
    }
    Box(
        if (backdrop != null) modifier.background(backdrop) else modifier,
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The two opaque ARGB colours of the backdrop, centre first, or null when either colour is missing. TDLib colours are RGB
 * without alpha, so the alpha channel is set to opaque.
 */
internal fun backdropEndpoints(details: GiftCollectibleDetails?): Pair<Long, Long>? {
    val center = details?.backdropCenterColor ?: return null
    val edge = details.backdropEdgeColor ?: return null
    return Pair(opaqueArgb(center), opaqueArgb(edge))
}

private fun opaqueArgb(rgb: Int): Long = 0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
