package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect

/** Scale and translation of the full-screen media page. Both reach identity when the page is at rest. */
internal data class MediaViewerTransform(val scale: Float, val offsetX: Float, val offsetY: Float)

/** The smallest scale the page starts from, so a thin thumbnail never shrinks the page to a sliver. */
private const val MEDIA_VIEWER_MIN_ORIGIN_SCALE = 0.1f

/**
 * Places the page over the tapped thumbnail at [travel] 1 and leaves it full-screen at 0. Without known bounds the page
 * keeps its 96% grow. The scale follows the thumbnail width, so a tall thumbnail is letterboxed while it moves.
 */
internal fun mediaViewerTransform(origin: Rect?, pageWidth: Float, pageHeight: Float, travel: Float): MediaViewerTransform {
    if (origin == null || origin.width <= 0f || pageWidth <= 0f || pageHeight <= 0f) {
        return MediaViewerTransform(scale = 1f - 0.04f * travel, offsetX = 0f, offsetY = 0f)
    }
    // Intensity above 100% may push travel past 1; clamping keeps the scale positive and the page on the thumbnail.
    val t = travel.coerceIn(0f, 1f)
    val originScale = (origin.width / pageWidth).coerceIn(MEDIA_VIEWER_MIN_ORIGIN_SCALE, 1f)
    return MediaViewerTransform(
        scale = 1f - (1f - originScale) * t,
        offsetX = (origin.center.x - pageWidth / 2f) * t,
        offsetY = (origin.center.y - pageHeight / 2f) * t
    )
}
