package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.GiftVisual
import androidx.compose.runtime.Composable
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

/** How faint the repeated symbol pattern is: visible on the backdrop, quiet behind the model. */
private const val GIFT_PATTERN_ALPHA = 0.28f

/**
 * The backdrop's pattern: the symbol's thumbnail repeated in three corners, faint and static. One thumbnail file is
 * observed for the whole pattern, and each corner draws the same image, so a card holds one file subscription rather
 * than one per corner. Nothing is drawn without a backdrop, or while the thumbnail is not on the device.
 *
 * The three fixed corners are a deliberate first slice; a pattern that follows the backdrop shape is a later change.
 */
@Composable
internal fun BoxScope.GiftPatternLayer(symbol: GiftVisual?, hasBackdrop: Boolean) {
    if (!hasBackdrop) return
    val thumbnailId = symbol?.thumbnailFileId ?: return
    val access = LocalGiftFileAccess.current ?: return
    val thumbnail by remember(thumbnailId, access) {
        access.observeFile(thumbnailId)
    }.collectAsStateWithLifecycle()
    LaunchedEffect(thumbnailId, thumbnail.size, thumbnail.localPath) {
        if (!thumbnail.isDownloading && thumbnail.localPath == null) access.downloadFile(thumbnailId)
    }
    val path = thumbnail.localPath ?: return
    val corners = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart)
    corners.forEach { corner ->
        Box(
            Modifier.align(corner).padding(4.dp).size(12.dp).alpha(GIFT_PATTERN_ALPHA).clearAndSetSemantics {}
        ) {
            LocalMediaImage(path, Modifier.fillMaxSize(), fit = true, animate = false)
        }
    }
}
