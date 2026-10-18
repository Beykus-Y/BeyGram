package com.beykus.beygram.ui.screens.chat

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Протяжка влево, после которой ответ взводится: отпускание дальше этой точки отвечает на сообщение. */
private val ReplyTrigger = 56.dp

/** Предел протяжки: дальше пальца пузырь не тянет. */
private val ReplyMaxTravel = 96.dp

/** Дальше порога протяжка идёт в эту долю: пузырь почти стоит, но ещё отвечает на палец. */
private const val PastTriggerResistance = 0.35f

/**
 * Свайп пузыря влево, как в Telegram: пузырь едет за пальцем, справа проявляется стрелка ответа.
 * У порога стрелка становится акцентной и звучит короткий отклик. Отпускание дальше порога вызывает [onReply],
 * раньше пузырь возвращается на место. Жест горизонтальный, поэтому вертикальная прокрутка ленты
 * и долгое нажатие на пузырь работают как прежде. При [enabled] = false пузырь не двигается.
 */
@Composable
internal fun SwipeToReply(
    enabled: Boolean,
    outgoing: Boolean,
    onReply: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val palette = beyGramPalette()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val triggerPx = with(density) { ReplyTrigger.toPx() }
    val maxTravelPx = with(density) { ReplyMaxTravel.toPx() }
    // The drag writes the travel directly, so a touch move starts no coroutine. The animation only brings the bubble back.
    var travel by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val armed by remember { derivedStateOf { isReplyArmed(travel, triggerPx) } }
    val dragState = rememberDraggableState { delta ->
        val wasArmed = isReplyArmed(travel, triggerPx)
        travel = (travel + delta).coerceIn(-maxTravelPx, 0f)
        if (!wasArmed && isReplyArmed(travel, triggerPx)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    // A bubble that stops being swipeable in the middle of a gesture must not stay pushed to the side.
    LaunchedEffect(enabled) { if (!enabled) travel = 0f }

    Box(Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Rounded.Reply, contentDescription = null,
            tint = if (armed) palette.accent else palette.textSecondary,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(24.dp).graphicsLayer {
                val progress = replySwipeProgress(travel, triggerPx)
                val scale = 0.6f + 0.4f * progress
                alpha = progress
                scaleX = scale
                scaleY = scale
            }
        )
        content(
            Modifier.align(if (outgoing) Alignment.TopEnd else Alignment.TopStart)
                .graphicsLayer { translationX = replySwipeOffset(travel, triggerPx) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = { settling?.cancel() },
                    onDragStopped = {
                        val reply = isReplyArmed(travel, triggerPx)
                        settling = scope.launch { animate(travel, 0f) { value, _ -> travel = value } }
                        if (reply) onReply()
                    }
                )
        )
    }
}

/** Смещение пузыря: до порога он идёт за пальцем, дальше протяжка замедляется. */
internal fun replySwipeOffset(travel: Float, triggerPx: Float): Float =
    if (travel > -triggerPx) travel else -triggerPx + (travel + triggerPx) * PastTriggerResistance

/** Доля пути до порога, от неё зависит проявление стрелки. */
internal fun replySwipeProgress(travel: Float, triggerPx: Float): Float = (-travel / triggerPx).coerceIn(0f, 1f)

/** Ответ взведён, когда протяжка дошла до порога. */
internal fun isReplyArmed(travel: Float, triggerPx: Float): Boolean = travel <= -triggerPx
