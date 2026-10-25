package com.beykus.beygram.data.media

/** Clockwise rotation applied to the decoded (EXIF-upright) photo. */
internal enum class PhotoRotation(val quarterTurns: Int) {
    None(0), Clockwise90(1), Half(2), CounterClockwise90(3);

    val degrees: Int get() = quarterTurns * 90
    fun rotatedRight(): PhotoRotation = entries[(ordinal + 1) % entries.size]
    fun rotatedLeft(): PhotoRotation = entries[(ordinal + entries.size - 1) % entries.size]
}

/**
 * Crop rectangle in normalized coordinates of the already rotated photo. Keeping it in rotated
 * space matches what the editor shows; rotating the photo rotates the crop with it.
 */
internal data class NormalizedCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) : java.io.Serializable {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f
}

internal data class PixelCrop(val left: Int, val top: Int, val width: Int, val height: Int)
internal data class PhotoSize(val width: Int, val height: Int)

internal enum class CropEdge { TopLeft, TopRight, BottomLeft, BottomRight }

/** Smallest crop side as a fraction of the photo, so a handle can never collapse the export to one pixel. */
internal const val MIN_CROP_FRACTION = 0.08f

internal enum class PhotoExportQuality(val maxSide: Int, val jpegQuality: Int) {
    High(2560, 92), Balanced(1600, 86), Compact(1280, 78)
}

internal fun clampCrop(crop: NormalizedCrop): NormalizedCrop {
    var left = crop.left.coerceIn(0f, 1f - MIN_CROP_FRACTION)
    var top = crop.top.coerceIn(0f, 1f - MIN_CROP_FRACTION)
    var right = crop.right.coerceIn(MIN_CROP_FRACTION, 1f)
    var bottom = crop.bottom.coerceIn(MIN_CROP_FRACTION, 1f)
    if (right - left < MIN_CROP_FRACTION) {
        right = (left + MIN_CROP_FRACTION).coerceAtMost(1f)
        left = (right - MIN_CROP_FRACTION).coerceAtLeast(0f)
    }
    if (bottom - top < MIN_CROP_FRACTION) {
        bottom = (top + MIN_CROP_FRACTION).coerceAtMost(1f)
        top = (bottom - MIN_CROP_FRACTION).coerceAtLeast(0f)
    }
    return NormalizedCrop(left, top, right, bottom)
}

/**
 * Moves the crop window by a normalized delta while preserving its size. The window stops at the
 * photo border instead of shrinking, which is what users expect when dragging a selection.
 */
internal fun moveCrop(crop: NormalizedCrop, dx: Float, dy: Float): NormalizedCrop {
    val safe = clampCrop(crop)
    val width = safe.width
    val height = safe.height
    val left = (safe.left + dx).coerceIn(0f, 1f - width)
    val top = (safe.top + dy).coerceIn(0f, 1f - height)
    return NormalizedCrop(left, top, left + width, top + height)
}

/** Resizes one corner by a normalized delta, keeping the opposite corner anchored. */
internal fun resizeCrop(crop: NormalizedCrop, edge: CropEdge, dx: Float, dy: Float): NormalizedCrop {
    val safe = clampCrop(crop)
    var left = safe.left
    var top = safe.top
    var right = safe.right
    var bottom = safe.bottom
    when (edge) {
        CropEdge.TopLeft -> { left += dx; top += dy }
        CropEdge.TopRight -> { right += dx; top += dy }
        CropEdge.BottomLeft -> { left += dx; bottom += dy }
        CropEdge.BottomRight -> { right += dx; bottom += dy }
    }
    left = left.coerceIn(0f, right - MIN_CROP_FRACTION)
    top = top.coerceIn(0f, bottom - MIN_CROP_FRACTION)
    right = right.coerceIn(left + MIN_CROP_FRACTION, 1f)
    bottom = bottom.coerceIn(top + MIN_CROP_FRACTION, 1f)
    return NormalizedCrop(left, top, right, bottom)
}

/** Rotates a crop rectangle together with the photo so the selected content is preserved. */
internal fun rotateCrop(crop: NormalizedCrop, clockwise: Boolean): NormalizedCrop {
    val safe = clampCrop(crop)
    return if (clockwise) {
        NormalizedCrop(1f - safe.bottom, safe.left, 1f - safe.top, safe.right)
    } else {
        NormalizedCrop(safe.top, 1f - safe.right, safe.bottom, 1f - safe.left)
    }
}

/**
 * Largest centered crop with [aspect] (width / height) inside the rotated photo. `null` keeps the
 * current selection so the "free" preset does not reset manual work.
 */
internal fun cropForAspect(aspect: Float?, imageWidth: Int, imageHeight: Int, current: NormalizedCrop): NormalizedCrop {
    if (aspect == null) return clampCrop(current)
    if (aspect <= 0f || imageWidth <= 0 || imageHeight <= 0) return clampCrop(current)
    val imageAspect = imageWidth.toFloat() / imageHeight
    return if (aspect >= imageAspect) {
        // Limited by width.
        val height = (imageAspect / aspect).coerceIn(MIN_CROP_FRACTION, 1f)
        val top = (1f - height) / 2f
        NormalizedCrop(0f, top, 1f, top + height)
    } else {
        val width = (aspect / imageAspect).coerceIn(MIN_CROP_FRACTION, 1f)
        val left = (1f - width) / 2f
        NormalizedCrop(left, 0f, left + width, 1f)
    }
}

internal fun cropPixels(crop: NormalizedCrop, imageWidth: Int, imageHeight: Int): PixelCrop {
    if (imageWidth <= 0 || imageHeight <= 0) return PixelCrop(0, 0, 0, 0)
    val safe = clampCrop(crop)
    val left = (safe.left * imageWidth).toInt().coerceIn(0, imageWidth - 1)
    val top = (safe.top * imageHeight).toInt().coerceIn(0, imageHeight - 1)
    val right = kotlin.math.ceil(safe.right * imageWidth).toInt().coerceIn(left + 1, imageWidth)
    val bottom = kotlin.math.ceil(safe.bottom * imageHeight).toInt().coerceIn(top + 1, imageHeight)
    return PixelCrop(left, top, right - left, bottom - top)
}

internal fun rotatedSize(width: Int, height: Int, rotation: PhotoRotation): PhotoSize =
    if (rotation.quarterTurns % 2 == 0) PhotoSize(width, height) else PhotoSize(height, width)

/** Never upscales: a small crop stays small instead of inventing pixels. */
internal fun scaledSize(width: Int, height: Int, maxSide: Int): PhotoSize {
    if (width <= 0 || height <= 0 || maxSide <= 0) return PhotoSize(0, 0)
    val longest = maxOf(width, height)
    if (longest <= maxSide) return PhotoSize(width, height)
    val scale = maxSide.toFloat() / longest
    return PhotoSize(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1)
    )
}

/** Telegram rejects aspect ratios above [maxAspect]; extreme crops are padded instead of cropped. */
internal fun telegramPaddedSize(width: Int, height: Int, maxAspect: Double = MediaLimits.MAX_PHOTO_ASPECT): PhotoSize {
    if (width <= 0 || height <= 0) return PhotoSize(width, height)
    val paddedWidth = maxOf(width, kotlin.math.ceil(height / maxAspect).toInt())
    val paddedHeight = maxOf(height, kotlin.math.ceil(width / maxAspect).toInt())
    return PhotoSize(paddedWidth, paddedHeight)
}

internal fun photoExportMimeType(hasAlpha: Boolean): String = if (hasAlpha) "image/png" else "image/jpeg"

internal fun photoExportExtension(hasAlpha: Boolean): String = if (hasAlpha) "png" else "jpg"
