package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.MediaKind

internal fun MediaKind.isSingleVisualMedia(): Boolean =
    this == MediaKind.Photo || this == MediaKind.Video || this == MediaKind.Animation

internal fun singleMediaSize(paneWidthDp: Float, availableWidthDp: Float, width: Int, height: Int): DpSize {
    val maxWidth = minOf(paneWidthDp * 0.68f, availableWidthDp, 416f).coerceAtLeast(0f)
    val maxHeight = minOf(maxWidth * 1.15f, 360f)
    val sourceRatio = if (width > 0 && height > 0) width.toFloat() / height else 1.5f
    // Give extreme panoramas and tall captures enough room for the overlay controls. The image fits inside the slot.
    val slotRatio = sourceRatio.coerceIn(0.40f, 3.2f)
    val slotWidth = minOf(maxWidth, maxHeight * slotRatio)
    return DpSize(slotWidth.dp, (slotWidth / slotRatio).dp)
}

internal fun sentStickerSize(paneWidthDp: Float, availableWidthDp: Float, width: Int, height: Int): DpSize {
    val side = minOf(paneWidthDp * 0.45f, availableWidthDp, 208f).coerceAtLeast(0f)
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 1f
    val slotRatio = ratio.coerceIn(0.25f, 4f)
    val slotWidth = minOf(side, side * slotRatio)
    return DpSize(slotWidth.dp, (slotWidth / slotRatio).dp)
}
