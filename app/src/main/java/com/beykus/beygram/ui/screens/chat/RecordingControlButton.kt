package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.media.ComposerRecordingMode
import com.beykus.beygram.ui.components.beyGramPalette
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import android.os.SystemClock
import kotlin.math.abs
import android.util.Log

@Composable
internal fun RecordingControlButton(
    mode: ComposerRecordingMode,
    active: Boolean,
    canceling: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onStart: () -> Unit,
    onCanceling: (Boolean) -> Unit,
    onLock: () -> Unit,
    onRelease: (Boolean) -> Unit,
    onDragOffset: (Float, Float) -> Unit = { _, _ -> }
) {
    val palette = beyGramPalette()
    val dragX = remember { mutableFloatStateOf(0f) }
    val dragY = remember { mutableFloatStateOf(0f) }
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onStart)
    val cancelState by rememberUpdatedState(onCanceling)
    val lock by rememberUpdatedState(onLock)
    val release by rememberUpdatedState(onRelease)
    val dragOffset by rememberUpdatedState(onDragOffset)
    val cancelDistance = with(LocalDensity.current) { 92.dp.toPx() }
    val lockDistance = with(LocalDensity.current) { 72.dp.toPx() }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    Box(Modifier.size(44.dp).pointerInput(enabled, longPressTimeout) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var change = down
                val deadline = SystemClock.uptimeMillis() + longPressTimeout
                while (change.pressed && SystemClock.uptimeMillis() < deadline) {
                    val remaining = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1L)
                    val event = withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Main) }
                    if (event == null) break
                    change = event.changes.firstOrNull { it.id == down.id } ?: break
                }
                if (!change.pressed && SystemClock.uptimeMillis() < deadline) {
                    Log.i(TAG, "recording_gesture_tap mode=$mode")
                    tap()
                    return@awaitEachGesture
                }
                Log.i(TAG, "recording_gesture_long_press mode=$mode")
                start()
                var locked = false
                var cancel = false
                var finished = false
                fun updateGesture(position: androidx.compose.ui.geometry.Offset) {
                    val dx = position.x - down.position.x
                    val dy = position.y - down.position.y
                    dragX.floatValue = dx.coerceIn(-cancelDistance * 1.4f, cancelDistance * 0.4f)
                    dragY.floatValue = dy.coerceIn(-lockDistance * 1.8f, lockDistance * 0.5f)
                    cancel = dx < -cancelDistance
                    dragOffset(dragX.floatValue, dragY.floatValue)
                    cancelState(cancel)
                }
                try {
                    updateGesture(change.position)
                    var pressed = change.pressed
                    while (pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        updateGesture(change.position)
                        pressed = change.pressed
                    }
                    finished = true
                } finally {
                    locked = finished && !cancel && change.position.let {
                        val dy = it.y - down.position.y
                        val dx = it.x - down.position.x
                        dy < -lockDistance && -dy >= abs(dx)
                    }
                    if (locked) {
                        Log.i(TAG, "recording_gesture_locked_on_release mode=$mode")
                        lock()
                    }
                    Log.i(TAG, "recording_gesture_end mode=$mode locked=$locked cancel=${cancel && !locked} complete=$finished")
                    dragX.floatValue = 0f
                    dragY.floatValue = 0f
                    dragOffset(0f, 0f)
                    cancelState(false)
                    release(!finished || (cancel && !locked))
                }
            }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(44.dp).graphicsLayer { translationX = dragX.floatValue; translationY = dragY.floatValue }
            .clip(CircleShape).background(if (active || canceling) palette.danger else palette.surface2), contentAlignment = Alignment.Center) {
            Icon(if (mode == ComposerRecordingMode.Voice) Icons.Rounded.Mic else Icons.Rounded.Videocam,
                contentDescription = stringResource(if (mode == ComposerRecordingMode.Voice) R.string.voice_recorder_start_description else R.string.recorder_start_description),
                tint = if (active || canceling) Color.White else palette.textSecondary, modifier = Modifier.size(23.dp))
        }
    }
}

private const val TAG = "BeyGramRecorder"
