package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect

/**
 * Scale, translation, and corner radius of the media page during open/close.
 * All reach identity (scale = 1, offset = 0, cornerRadiusDp = 0) when the page is at rest.
 */
internal data class MediaViewerTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val cornerRadiusDp: Float = 0f
)

/** The smallest scale the page starts from, so a thin thumbnail never shrinks the page to a sliver. */
private const val MEDIA_VIEWER_MIN_ORIGIN_SCALE = 0.1f

/** Visual corner radius (in dp) at the thumbnail end of the transition. */
private const val MEDIA_VIEWER_ORIGIN_CORNER_DP = 14f

/** Upper bound on unscaled corner radius (in dp) so a very small thumbnail does not over-clip the page. */
private const val MEDIA_VIEWER_MAX_CORNER_DP = 64f

/**
 * Places the media page over the tapped thumbnail at [travel] 1 and leaves it full-screen at 0.
 * Without valid on-screen bounds, falls back to a subtle 96% grow in place with sharp corners.
 */
internal fun mediaViewerTransform(
    origin: Rect?,
    pageWidth: Float,
    pageHeight: Float,
    travel: Float,
    target: Rect? = null
): MediaViewerTransform {
    // Intensity above 100% may push travel past 1; clamping keeps the scale positive and the page on the thumbnail.
    val t = travel.coerceIn(0f, 1f)
    if (pageWidth <= 0f || pageHeight <= 0f) {
        return MediaViewerTransform(scale = 1f - 0.04f * t, offsetX = 0f, offsetY = 0f)
    }
    val dest = target?.takeIf { it.width > 0f && it.height > 0f } ?: Rect(0f, 0f, pageWidth, pageHeight)
    if (origin == null || !isOriginVisibleInWindow(origin, pageWidth, pageHeight)) {
        return MediaViewerTransform(scale = 1f - 0.04f * t, offsetX = 0f, offsetY = 0f)
    }
    val originScale = (origin.width / dest.width).coerceIn(MEDIA_VIEWER_MIN_ORIGIN_SCALE, 1f)
    val originCornerDp = (MEDIA_VIEWER_ORIGIN_CORNER_DP / originScale).coerceAtMost(MEDIA_VIEWER_MAX_CORNER_DP)
    return MediaViewerTransform(
        scale = 1f - (1f - originScale) * t,
        offsetX = (origin.center.x - dest.center.x) * t,
        offsetY = (origin.center.y - dest.center.y) * t,
        cornerRadiusDp = originCornerDp * t
    )
}

internal fun isOriginVisibleInWindow(origin: Rect, windowWidth: Float, windowHeight: Float): Boolean {
    if (origin.width <= 0f || origin.height <= 0f) return false
    if (windowWidth <= 0f || windowHeight <= 0f) return false
    return origin.right > 0f && origin.left < windowWidth && origin.bottom > 0f && origin.top < windowHeight
}
