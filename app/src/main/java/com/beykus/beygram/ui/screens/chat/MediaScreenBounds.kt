package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates

/** Activity and Dialog have different window origins. Keep thumbnail, viewport and media in screen coordinates. */
internal fun LayoutCoordinates.mediaScreenBounds(): Rect? {
    if (!isAttached) return null
    val corners = listOf(Offset.Zero, Offset(size.width.toFloat(), 0f),
        Offset(0f, size.height.toFloat()), Offset(size.width.toFloat(), size.height.toFloat()))
        .map { localToScreen(it) }
    if (corners.any { !it.isSpecified }) return null
    return Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
}
