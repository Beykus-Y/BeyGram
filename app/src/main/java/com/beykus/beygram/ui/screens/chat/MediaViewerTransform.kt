package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

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

/** Fraction of the viewport height a slow vertical drag must exceed to dismiss the viewer. */
private const val MEDIA_VIEWER_DISMISS_DISTANCE_FRACTION = 0.18f

/** Fraction of the viewport height at which interactive dismiss scale/alpha reach their full effect. */
private const val MEDIA_VIEWER_DISMISS_EFFECT_FRACTION = 0.45f

/**
 * Places the media page over the tapped thumbnail at [travel] 1 and leaves it full-screen at 0.
 * Without valid on-screen bounds, falls back to a subtle 96% grow in place with sharp corners.
 */
internal fun mediaViewerTransform(
    origin: Rect?,
    pageWidth: Float,
    pageHeight: Float,
    travel: Float,
    target: Rect? = null,
    viewport: Rect? = null
): MediaViewerTransform {
    // Intensity above 100% may push travel past 1; clamping keeps the scale positive and the page on the thumbnail.
    val t = travel.coerceIn(0f, 1f)
    if (pageWidth <= 0f || pageHeight <= 0f) {
        return MediaViewerTransform(scale = 1f - 0.04f * t, offsetX = 0f, offsetY = 0f)
    }
    val dest = target?.takeIf { it.width > 0f && it.height > 0f } ?: Rect(0f, 0f, pageWidth, pageHeight)
    val visibleViewport = viewport ?: Rect(0f, 0f, pageWidth, pageHeight)
    if (origin == null || origin.width <= 0f || origin.height <= 0f || !origin.overlaps(visibleViewport)) {
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

/** Normalized 0..1 progress of an interactive vertical swipe-to-dismiss gesture (up or down). */
internal fun mediaViewerDismissFraction(offsetY: Float, viewportHeightPx: Float): Float {
    if (viewportHeightPx <= 0f) return 0f
    return (abs(offsetY) / (viewportHeightPx * MEDIA_VIEWER_DISMISS_EFFECT_FRACTION)).coerceIn(0f, 1f)
}

/** Subtle scale-down applied to the media page as the user drags vertically to dismiss. */
internal fun mediaViewerDismissScale(fraction: Float): Float =
    1f - 0.12f * fraction.coerceIn(0f, 1f)

/** Backdrop alpha fades smoothly as the user drags vertically to dismiss. */
internal fun mediaViewerDismissBackdropAlpha(fraction: Float): Float =
    (1f - 0.85f * fraction.coerceIn(0f, 1f)).coerceIn(0.15f, 1f)

/**
 * Decides on release whether a vertical swipe (either up or down) should dismiss the viewer,
 * considering both drag distance and fling velocity in the drag direction.
 * A fast fling requires at least [minFlingTravelPx] of net displacement in the fling direction so
 * pulling one way and snapping back near the origin does not accidentally dismiss.
 */
internal fun shouldDismissMediaViewer(
    offsetY: Float,
    velocityY: Float,
    viewportHeightPx: Float,
    velocityThresholdPxPerSec: Float,
    minFlingTravelPx: Float = 0f
): Boolean {
    if (viewportHeightPx > 0f && abs(offsetY) > viewportHeightPx * MEDIA_VIEWER_DISMISS_DISTANCE_FRACTION) {
        return true
    }
    val sameDirection = offsetY * velocityY > 0f
    return sameDirection && abs(offsetY) >= minFlingTravelPx && abs(velocityY) >= velocityThresholdPxPerSec
}
