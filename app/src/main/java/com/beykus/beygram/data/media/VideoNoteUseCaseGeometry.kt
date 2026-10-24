package com.beykus.beygram.data.media

import kotlin.math.roundToInt

/**
 * CameraX's reported output stream and its ViewPort crop for one use case.
 * These are stream coordinates, NOT sensor coordinates or a measured optical field of view.
 */
internal data class VideoNoteUseCaseGeometry(
    val streamWidth: Int,
    val streamHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val rotationDegrees: Int
)

/**
 * Fraction of each output stream retained by CameraX's use-case crop.
 * Helps distinguish SD/HD ViewPort differences without making a claim about optical zoom.
 * Returns null for missing or inconsistent camera metadata rather than inventing a crop.
 */
internal fun videoNoteUseCaseGeometrySummary(info: VideoNoteUseCaseGeometry?): String? {
    info ?: return null
    val cropWidth = info.cropRight - info.cropLeft
    val cropHeight = info.cropBottom - info.cropTop
    if (info.streamWidth <= 0 || info.streamHeight <= 0 ||
        info.cropLeft < 0 || info.cropTop < 0 || cropWidth <= 0 || cropHeight <= 0 ||
        info.cropRight > info.streamWidth || info.cropBottom > info.streamHeight
    ) return null

    val widthPercent = (cropWidth.toDouble() * 100.0 / info.streamWidth).roundToInt()
    val heightPercent = (cropHeight.toDouble() * 100.0 / info.streamHeight).roundToInt()
    return "stream=${info.streamWidth}x${info.streamHeight} " +
        "crop=(${info.cropLeft},${info.cropTop}) ${cropWidth}x${cropHeight} " +
        "retained=${widthPercent}%W/${heightPercent}%H rotation=${info.rotationDegrees}°"
}
