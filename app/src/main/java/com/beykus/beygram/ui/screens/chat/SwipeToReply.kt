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
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Протяжка к центру чата, после которой ответ взводится: отпускание дальше этой точки отвечает на сообщение. */
private val ReplyTrigger = 56.dp

/** Предел протяжки: дальше пальца пузырь не тянет. */
private val ReplyMaxPull = 96.dp

/** Дальше порога протяжка идёт в эту долю: пузырь почти стоит, но ещё отвечает на палец. */
private const val PastTriggerResistance = 0.35f

/**
 * Свайп пузыря к центру чата: исходящий уезжает влево, входящий вправо. Так пузырь у края экрана не уходит за него,
 * а стрелка ответа проявляется на том краю, который пузырь освобождает. Пузырь едет за пальцем, у порога стрелка
 * становится акцентной и звучит короткий отклик. Отпускание дальше порога вызывает [onReply], раньше пузырь
 * возвращается на место. Жест горизонтальный, поэтому вертикальная прокрутка ленты и долгое нажатие работают как прежде.
 * При [enabled] = false пузырь не двигается.
 */
@Composable
internal fun SwipeToReply(
    enabled: Boolean,
    outgoing: Boolean,
    onReply: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryIcon: ImageVector = Icons.Rounded.Favorite,
    content: @Composable (Modifier) -> Unit
) {
    val palette = beyGramPalette()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val triggerPx = with(density) { ReplyTrigger.toPx() }
    val maxPullPx = with(density) { ReplyMaxPull.toPx() }
    val direction = if (outgoing) -1f else 1f
    val hasSecondary = onSecondaryAction != null
    // Positive pull = reply toward chat center; negative pull = secondary quick action in opposite direction.
    var pull by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val armedReply by remember { derivedStateOf { isReplyArmed(pull, triggerPx) } }
    val armedSecondary by remember { derivedStateOf { hasSecondary && isReplyArmed(-pull, triggerPx) } }
    val dragState = rememberDraggableState { delta ->
        val wasReply = isReplyArmed(pull, triggerPx)
        val wasSecondary = hasSecondary && isReplyArmed(-pull, triggerPx)
        val minPull = if (hasSecondary) -maxPullPx else 0f
        pull = (pull + delta * direction).coerceIn(minPull, maxPullPx)
        if ((!wasReply && isReplyArmed(pull, triggerPx)) || (!wasSecondary && hasSecondary && isReplyArmed(-pull, triggerPx))) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // A bubble that stops being swipeable in the middle of a gesture must not stay pushed to the side.
    LaunchedEffect(enabled) { if (!enabled) pull = 0f }

    Box(Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Rounded.Reply, contentDescription = null,
            tint = if (armedReply) palette.accent else palette.textSecondary,
            modifier = Modifier.align(if (outgoing) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 16.dp).size(24.dp).graphicsLayer {
                    val progress = replySwipeProgress(pull.coerceAtLeast(0f), triggerPx)
                    val scale = 0.6f + 0.4f * progress
                    alpha = progress
                    // Flipped for incoming messages, so the arrow points at the bubble it belongs to.
                    scaleX = if (outgoing) scale else -scale
                    scaleY = scale
                }
        )
        if (hasSecondary) {
            Icon(
                secondaryIcon, contentDescription = null,
                tint = if (armedSecondary) palette.accent else palette.textSecondary,
                modifier = Modifier.align(if (outgoing) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp).size(22.dp).graphicsLayer {
                        val progress = replySwipeProgress((-pull).coerceAtLeast(0f), triggerPx)
                        val scale = 0.6f + 0.4f * progress
                        alpha = progress
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
        content(
            Modifier.align(if (outgoing) Alignment.TopEnd else Alignment.TopStart)
                .graphicsLayer {
                    val signedOffset = if (pull >= 0f) replySwipeOffset(pull, triggerPx)
                    else -replySwipeOffset(-pull, triggerPx)
                    translationX = signedOffset * direction
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = { settling?.cancel() },
                    onDragStopped = {
                        val reply = isReplyArmed(pull, triggerPx)
                        val secondary = hasSecondary && isReplyArmed(-pull, triggerPx)
                        settling = scope.launch { animate(pull, 0f) { value, _ -> pull = value } }
                        if (reply) onReply()
                        else if (secondary) onSecondaryAction?.invoke()
                    }
                )
        )
    }
}

/** Смещение пузыря от места: до порога он идёт за пальцем, дальше протяжка замедляется. */
internal fun replySwipeOffset(pull: Float, triggerPx: Float): Float =
    if (pull < triggerPx) pull else triggerPx + (pull - triggerPx) * PastTriggerResistance

/** Доля пути до порога, от неё зависит проявление стрелки. */
internal fun replySwipeProgress(pull: Float, triggerPx: Float): Float = (pull / triggerPx).coerceIn(0f, 1f)

/** Ответ взведён, когда протяжка дошла до порога. */
internal fun isReplyArmed(pull: Float, triggerPx: Float): Boolean = pull >= triggerPx
