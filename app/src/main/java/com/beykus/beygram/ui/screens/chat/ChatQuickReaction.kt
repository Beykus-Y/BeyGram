package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ReactionOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal fun preferredQuickReaction(allowed: List<ReactionOption>): ReactionOption? {
    val preferred = allowed.firstOrNull { !it.isPaid && it.customEmojiId == 0L && it.emoji.removeSuffix("\uFE0F") == "❤" }
    return preferred ?: allowed.firstOrNull { !it.isPaid }
}

/** The inner bubble handles its own taps before the row sees free-space gestures. */
@Composable
internal fun Modifier.quickReactionOnFreeSpace(chatId: Long, messageId: Long, onDoubleTap: (() -> Unit)?): Modifier {
    val currentReaction by rememberUpdatedState(onDoubleTap)
    return if (onDoubleTap == null) this else pointerInput(chatId, messageId) {
        detectTapGestures(onDoubleTap = { currentReaction?.invoke() })
    }
}

@Composable
internal fun rememberBubbleQuickReaction(
    message: ChatMessage,
    load: suspend (Long, Long) -> Result<List<ReactionOption>>,
    react: (Long, ReactionOption) -> Unit
): () -> Unit = key(message.chatId, message.id, load) {
    val scope = rememberCoroutineScope()
    val currentReact by rememberUpdatedState(react)
    val haptics = LocalHapticFeedback.current
    var busy by remember { mutableStateOf(false) }
    return@key {
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    val allowed = load(message.chatId, message.id).getOrNull().orEmpty()
                    currentCoroutineContext().ensureActive()
                    preferredQuickReaction(allowed)?.let {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentReact(message.id, it)
                    }
                } finally {
                    busy = false
                }
            }
        }
    }
}
