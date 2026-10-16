package com.beykus.beygram.ui.screens.chats

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.components.beyGramPalette
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
    val perform: suspend (ChatPreview) -> Boolean
)

internal enum class SwipeTarget { Closed, Revealed, Dismissed }

/** The newest drag step that writes the offset. Steps run in order on the main thread, so the last one is the final word. */
private class DragStep {
    var job: Job? = null
}

private const val FLING_PX_PER_SECOND = 800f
private const val DISMISS_FRACTION = 0.6f
private val revealButtonWidth = 84.dp

/** Две кнопки по [revealButtonWidth]. */
private val revealWidth = 168.dp

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
 * Строка чата со свайпом влево: под ней лежат кнопки [read] и [primary], полный свайп выполняет [primary].
 * Открыта может быть только одна строка: её id приходит в [openChatId], а [onOpenChange] сообщает об открытии и закрытии.
 */
@Composable
internal fun SwipeChatRow(
    chat: ChatPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    openChatId: Long?,
    onOpenChange: (Long?) -> Unit,
    onClick: () -> Unit,
    read: ChatSwipeAction,
    primary: ChatSwipeAction
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    val swipeOffset = remember { Animatable(0f) }
    var rowWidth by remember { mutableStateOf(0f) }
    val latestOpenChatId by rememberUpdatedState(openChatId)

    LaunchedEffect(openChatId) {
        if (openChatId != chat.id && swipeOffset.value != 0f) swipeOffset.animateTo(0f)
    }

    fun close() {
        scope.launch {
            swipeOffset.animateTo(0f)
            if (latestOpenChatId == chat.id) onOpenChange(null)
        }
    }

    suspend fun settle(target: SwipeTarget) {
        when (target) {
            SwipeTarget.Closed -> {
                swipeOffset.animateTo(0f)
                if (latestOpenChatId == chat.id) onOpenChange(null)
            }
            SwipeTarget.Revealed -> swipeOffset.animateTo(-revealPx)
            SwipeTarget.Dismissed -> {
                swipeOffset.animateTo(-rowWidth)
                if (latestOpenChatId == chat.id) onOpenChange(null)
                if (!primary.perform(chat)) swipeOffset.animateTo(0f)
            }
        }
    }

    val lastDragStep = remember { DragStep() }
    val dragState = rememberDraggableState { delta ->
        lastDragStep.job = scope.launch { swipeOffset.snapTo((swipeOffset.value + delta).coerceIn(-rowWidth, 0f)) }
    }

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width.toFloat() }) {
        Row(modifier = Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            RevealButton(action = read) {
                close()
                scope.launch { read.perform(chat) }
            }
            RevealButton(action = primary) {
                scope.launch { settle(SwipeTarget.Dismissed) }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = swipeOffset.value }
                .background(palette.screen)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { onOpenChange(chat.id) },
                    onDragStopped = { velocity ->
                        // The last step must land before the settle animation starts, or it would interrupt the animation.
                        lastDragStep.job?.join()
                        settle(swipeTarget(swipeOffset.value, revealPx, rowWidth * DISMISS_FRACTION, velocity))
                    }
                )
        ) {
            ChatListItem(chat, observeFile, downloadFile, onClick = { if (swipeOffset.value < 0f) close() else onClick() })
        }
    }
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
