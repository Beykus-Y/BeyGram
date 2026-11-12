package com.beykus.beygram.ui.screens.chats

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionSpec
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Кнопка за правым краем строки чата: подпись, иконка, цвета и операция, которая возвращает успех. */
internal data class ChatSwipeAction(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val enabled: Boolean,
    val kind: ChatSwipeActionKind,
    val perform: suspend (ChatPreview) -> Boolean
)

internal enum class SwipeTarget { Closed, Revealed, Dismissed }

/**
 * A dismissed row must not be auto-restored while its archive action is in flight.
 * Otherwise clearing the global open-swipe ID re-opens the just-dismissed row before TDLib finishes.
 */
internal fun shouldAutoCloseSwipe(
    openChatId: Long?,
    chatId: Long,
    offset: Float,
    dismissalInProgress: Boolean
): Boolean = openChatId != chatId && offset != 0f && !dismissalInProgress

/**
 * The animation that finishes the latest gesture. Cancelling it or starting a new one bumps [gesture], so a rollback
 * scheduled earlier can tell that the row has moved on.
 */
private class Settle {
    var job: Job? = null
    var gesture = 0
}

private const val FLING_PX_PER_SECOND = 800f
private const val DISMISS_FRACTION = 0.6f
private val revealButtonWidth = 84.dp

/** A single action on each side: the left and right edge stay independent. */
private val revealWidth = revealButtonWidth

/**
 * Куда встаёт строка после отпускания пальца. Быстрый взмах влево открывает кнопки, быстрый взмах вправо
 * закрывает их даже из середины, а протяжка дальше [dismissPx] выполняет основное действие.
 */
internal fun swipeTarget(offset: Float, revealPx: Float, dismissPx: Float, velocity: Float): SwipeTarget = when {
    velocity >= FLING_PX_PER_SECOND -> SwipeTarget.Closed
    offset <= -dismissPx -> SwipeTarget.Dismissed
    velocity <= -FLING_PX_PER_SECOND -> SwipeTarget.Revealed
    offset <= -revealPx / 2f -> SwipeTarget.Revealed
    else -> SwipeTarget.Closed
}

/**
 * Each swipe direction has one configured action; a full swipe executes only that side.
 * Exactly one chat can stay revealed. Vertical list scrolling keeps priority over horizontal dragging.
 */
@Composable
internal fun SwipeChatRow(
    modifier: Modifier = Modifier,
    chat: ChatPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    openChatId: Long?,
    onOpenChange: (Long?) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    left: ChatSwipeAction,
    right: ChatSwipeAction,
    onPerformed: (ChatPreview, ChatSwipeActionKind) -> Unit = { _, _ -> },
    actionScope: CoroutineScope,
    /** The chat open in the other pane of the two-pane layout. */
    highlighted: Boolean = false
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    // The drag writes the offset directly, so a touch move starts no coroutine. Animations only finish a gesture.
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableStateOf(0f) }
    var dismissalInProgress by remember { mutableStateOf(false) }
    var crossedCommitThreshold by remember { mutableStateOf(false) }
    val latestOpenChatId by rememberUpdatedState(openChatId)
    val settle = remember { Settle() }
    // A release that snaps back or reveals is touch feedback; a dismissal removes the row from the list.
    val feedbackSpec = rememberMotionSpec(MotionToken.MicroFeedback)
    val removalSpec = rememberMotionSpec(MotionToken.ListReorder)

    fun cancelSettle() {
        settle.gesture++
        settle.job?.cancel()
    }

    fun startSettle(block: suspend () -> Unit) {
        cancelSettle()
        settle.job = scope.launch { block() }
    }

    suspend fun animateOffset(target: Float, spec: MotionSpec = feedbackSpec) {
        // Off, reduced motion and disabled categories land on the target at once, with no intermediate frames.
        if (spec.isInstant) {
            swipeOffset = target
            return
        }
        animate(swipeOffset, target, animationSpec = spec.animationSpec()) { value, _ -> swipeOffset = value }
    }

    LaunchedEffect(openChatId) {
        if (shouldAutoCloseSwipe(openChatId, chat.id, swipeOffset, dismissalInProgress)) {
            startSettle { animateOffset(0f) }
        }
    }

    LaunchedEffect(left.kind, right.kind) {
        if (swipeOffset != 0f) startSettle { animateOffset(0f) }
    }

    fun close() = startSettle {
        animateOffset(0f)
        if (latestOpenChatId == chat.id) onOpenChange(null)
    }

    suspend fun settleTo(target: SwipeTarget) {
        when (target) {
            SwipeTarget.Closed -> {
                animateOffset(0f)
                if (latestOpenChatId == chat.id) onOpenChange(null)
            }
            SwipeTarget.Revealed -> animateOffset(if (swipeOffset < 0f) -revealPx else revealPx)
            SwipeTarget.Dismissed -> {
                val direction = if (swipeOffset < 0f) -1f else 1f
                val action = if (direction < 0f) left else right
                if (!action.enabled) {
                    animateOffset(0f)
                    if (latestOpenChatId == chat.id) onOpenChange(null)
                    return
                }
                // Keep the row dismissed through the asynchronous Telegram operation.
                dismissalInProgress = true
                val gesture = settle.gesture
                animateOffset(direction * rowWidth, removalSpec)
                if (latestOpenChatId == chat.id) onOpenChange(null)
                actionScope.launch {
                    val success = runCatching { action.perform(chat) }.getOrDefault(false)
                    if (success) onPerformed(chat, action.kind)
                    // An archived chat may remain inside a server folder: restore only after the action.
                    if (settle.gesture == gesture) {
                        dismissalInProgress = false
                        startSettle { animateOffset(0f) }
                    }
                }
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        swipeOffset = (swipeOffset + delta).coerceIn(-rowWidth, rowWidth)
        val overThreshold = rowWidth > 0f && abs(swipeOffset) >= rowWidth * DISMISS_FRACTION
        if (overThreshold && !crossedCommitThreshold) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        crossedCommitThreshold = overThreshold
    }

    Box(modifier = modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width.toFloat() }) {
        Row(modifier = Modifier.matchParentSize(), horizontalArrangement = Arrangement.SpaceBetween) {
            RevealButton(action = right) {
                close()
                actionScope.launch {
                    if (runCatching { right.perform(chat) }.getOrDefault(false)) onPerformed(chat, right.kind)
                }
            }
            RevealButton(action = left) {
                close()
                actionScope.launch {
                    if (runCatching { left.perform(chat) }.getOrDefault(false)) onPerformed(chat, left.kind)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = swipeOffset }
                .background(palette.screen)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        cancelSettle()
                        crossedCommitThreshold = false
                        dismissalInProgress = false
                        onOpenChange(chat.id)
                    },
                    onDragStopped = { velocity ->
                        crossedCommitThreshold = false
                        val target = bidirectionalSwipeTarget(swipeOffset, revealPx, rowWidth * DISMISS_FRACTION, velocity)
                        startSettle { settleTo(target) }
                    }
                )
        ) {
            ChatListItem(chat, observeFile, downloadFile,
                onClick = { if (swipeOffset < 0f) close() else onClick() },
                onLongClick = { if (swipeOffset < 0f) close() else onLongClick() },
                highlighted = highlighted)
        }
    }
}

/**
 * Reuse the tested left-swipe threshold in either direction. Flinging back toward the centre
 * closes a revealed action; the opposite edge can never commit the previous direction's action.
 */
internal fun bidirectionalSwipeTarget(offset: Float, revealPx: Float, dismissPx: Float, velocity: Float): SwipeTarget =
    when {
        offset < 0f -> swipeTarget(offset, revealPx, dismissPx, velocity)
        offset > 0f -> swipeTarget(-offset, revealPx, dismissPx, -velocity)
        else -> SwipeTarget.Closed
    }

@Composable
private fun RevealButton(action: ChatSwipeAction, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(revealButtonWidth)
            .fillMaxHeight()
            .alpha(if (action.enabled) 1f else 0.4f)
            .background(action.container)
            .clickable(enabled = action.enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(action.icon, contentDescription = null, tint = action.content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(action.label, color = action.content, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
