package com.beykus.beygram.ui.design

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Pure geometry for the Liquid Glass V2 lens, kept free of Android types so it can be unit-tested on the JVM.
 */

/** Corner radii of a panel in pixels: top-left, top-right, bottom-right, bottom-left. */
internal data class LensCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float
) {
    companion object {
        val None = LensCornerRadii(0f, 0f, 0f, 0f)
    }
}

/**
 * The corner radii of [shape] at [size], read from the outline the panel is really clipped to. A rounded rectangle
 * keeps its own per-corner radii, a rectangle has none, and any other outline (a circle, a pill, a generic path) uses
 * half of the shorter side at every corner, which is exact for circles and pills.
 */
internal fun lensCornerRadii(
    shape: Shape,
    size: Size,
    density: Density,
    layoutDirection: LayoutDirection
): LensCornerRadii {
    val maxRadius = minOf(size.width, size.height) / 2f
    // A RoundedCornerShape with different radii builds a generic path, so its radii are read from the shape itself.
    val radii = if (shape is RoundedCornerShape) {
        val ltr = layoutDirection == LayoutDirection.Ltr
        fun px(corner: CornerSize) = corner.toPx(size, density)
        val start = px(shape.topStart)
        val end = px(shape.topEnd)
        val bottomEnd = px(shape.bottomEnd)
        val bottomStart = px(shape.bottomStart)
        if (ltr) LensCornerRadii(start, end, bottomEnd, bottomStart)
        else LensCornerRadii(end, start, bottomStart, bottomEnd)
    } else when (val outline = shape.createOutline(size, layoutDirection, density)) {
        is Outline.Rectangle -> LensCornerRadii.None
        is Outline.Rounded -> outline.roundRect.let {
            LensCornerRadii(
                topLeft = it.topLeftCornerRadius.x,
                topRight = it.topRightCornerRadius.x,
                bottomRight = it.bottomRightCornerRadius.x,
                bottomLeft = it.bottomLeftCornerRadius.x
            )
        }
        is Outline.Generic -> LensCornerRadii(maxRadius, maxRadius, maxRadius, maxRadius)
    }
    return LensCornerRadii(
        topLeft = radii.topLeft.coerceIn(0f, maxRadius),
        topRight = radii.topRight.coerceIn(0f, maxRadius),
        bottomRight = radii.bottomRight.coerceIn(0f, maxRadius),
        bottomLeft = radii.bottomLeft.coerceIn(0f, maxRadius)
    )
}

/**
 * The width of the refracting band. It is capped at [maxFraction] of the shorter side, so a small control gets a thin
 * lens instead of a band that covers its whole face, and it is never below one pixel.
 */
internal fun lensThicknessPx(requestedPx: Float, size: Size, maxFraction: Float): Float =
    minOf(requestedPx, minOf(size.width, size.height) * maxFraction).coerceAtLeast(1f)
