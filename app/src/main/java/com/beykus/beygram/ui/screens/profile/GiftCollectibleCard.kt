package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.GiftVisual

/**
 * Shared artwork frame for a regular or collectible gift: backdrop radial gradient, faint symbol pattern,
 * main sticker/model visual (or emoji fallback), and optional static corner symbol.
 */
@Composable
internal fun GiftArtworkFrame(
    visual: GiftVisual?,
    emoji: String,
    collectible: GiftCollectibleDetails? = null,
    frameSize: Dp = 52.dp,
    stickerSize: Dp = 36.dp,
    symbolSize: Dp = 18.dp,
    cornerRadius: Dp = 12.dp,
    staticOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasBackdrop = backdropEndpoints(collectible) != null
    GiftCollectibleBackdrop(
        details = collectible,
        modifier = modifier.size(frameSize).clip(RoundedCornerShape(cornerRadius))
    ) {
        GiftPatternLayer(collectible?.symbol, hasBackdrop = hasBackdrop)
        GiftVisualImage(visual, stickerSize, staticOnly = staticOnly) {
            Text(emoji.ifBlank { "\uD83C\uDF81" }, fontSize = stickerSize.value.sp)
        }
        collectible?.symbol?.takeIf { it.thumbnailFileId != null }?.let { symbol ->
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).clearAndSetSemantics {}) {
                GiftVisualImage(symbol, symbolSize, staticOnly = true) {}
            }
        }
    }
}
