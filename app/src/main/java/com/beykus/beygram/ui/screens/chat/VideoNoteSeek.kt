package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** Clockwise fraction measured from 12 o'clock, in screen coordinates. */
internal fun videoNoteSeekFraction(position: Offset, center: Offset): Float {
    val relative = position - center
    val turns = atan2(relative.x, -relative.y) / (2 * PI)
    return ((turns + 1) % 1).toFloat()
}

internal fun advanceVideoNoteSeek(previous: Float, current: Float, scrub: Float): Float {
    val delta = (current - previous + 1.5f) % 1f - 0.5f
    return (scrub + delta).coerceIn(0f, 1f)
}

internal fun isOnVideoNoteSeekRing(position: Offset, center: Offset, radius: Float, tolerance: Float): Boolean =
    radius > 0f && abs((position - center).getDistance() - radius) <= tolerance

/** The circle's existing tap and long press still handle gestures that start inside the ring. */
internal fun Modifier.videoNoteSeekGesture(
    playbackKey: String,
    onPreview: (Float?) -> Unit,
    onSeek: (Float) -> Unit
): Modifier = pointerInput(playbackKey) {
    try {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(center.x, center.y) - 2.dp.toPx()
            if (!isOnVideoNoteSeekRing(down.position, center, radius, 18.dp.toPx())) return@awaitEachGesture

            // The ring takes the gesture before the play/pause target and the scrolling list see it.
            down.consume()
            val start = videoNoteSeekFraction(down.position, center)
            var previous = start
            var scrub = start
            var dragging = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                    change.consume()
                    if (!change.pressed) {
                        onSeek(if (dragging) scrub else start)
                        break
                    }
                    // At the exact center an angle has no meaning. Keep the last angle if the finger slips inward.
                    if ((change.position - center).getDistance() < radius / 2f) continue
                    val angle = videoNoteSeekFraction(change.position, center)
                    scrub = advanceVideoNoteSeek(previous, angle, scrub)
                    previous = angle
                    dragging = dragging || (change.position - down.position).getDistance() >= viewConfiguration.touchSlop
                    if (dragging) onPreview(scrub)
                }
            } finally {
                onPreview(null)
            }
        }
    } finally {
        onPreview(null)
    }
}
