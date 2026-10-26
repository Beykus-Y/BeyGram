package com.beykus.beygram.ui.screens.chat

/**
 * The native video-frame decoder accepts a width and height separately. Constrain the longest
 * edge without stretching wide/portrait GIF previews into square images or upscaling tiny ones.
 */
internal fun fitVideoPosterSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    require(maxDimension > 0) { "Maximum poster dimension must be positive" }
    if (width <= 0 || height <= 0) return maxDimension to maxDimension
    val longest = maxOf(width, height)
    if (longest <= maxDimension) return width to height
    return ((width.toLong() * maxDimension / longest).toInt().coerceAtLeast(1)) to
        ((height.toLong() * maxDimension / longest).toInt().coerceAtLeast(1))
}
