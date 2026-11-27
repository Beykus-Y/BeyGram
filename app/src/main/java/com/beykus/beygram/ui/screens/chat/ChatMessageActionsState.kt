package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Context actions for one message in a single account, chat and topic. */
@Stable
internal class ChatMessageActionsState(
    target: MutableState<Long?>,
    deletePrompt: MutableState<Boolean>,
    pinPrompt: MutableState<Boolean>
) {
    var targetId by target
        private set
    var deleting by deletePrompt
    var pinConfirm by pinPrompt
    var deleteEveryone by mutableStateOf(false)
    var deleteBusy by mutableStateOf(false)
    var deleteError by mutableStateOf<String?>(null)
    var capabilities by mutableStateOf<MessageCapabilities?>(null)
    var capabilitiesError by mutableStateOf<String?>(null)
    var capabilitiesAttempt by mutableIntStateOf(0)
    var reactionOptions by mutableStateOf<List<ReactionOption>>(emptyList())
    var reactionPickerOpen by mutableStateOf(false)

    fun open(id: Long) {
        if (targetId != id) {
            clearPresentation()
            targetId = id
        }
    }

    fun close() {
        targetId = null
        clearPresentation()
    }

    fun clearPresentation() {
        deleting = false
        deleteError = null
        pinConfirm = false
        capabilities = null
        capabilitiesError = null
        reactionOptions = emptyList()
        reactionPickerOpen = false
    }
}

@Composable
internal fun rememberChatMessageActions(accountId: String, chatId: Long, forumTopicId: Long): ChatMessageActionsState {
    val target = rememberSaveable(accountId, chatId, forumTopicId) { mutableStateOf<Long?>(null) }
    val deleting = rememberSaveable(accountId, chatId, forumTopicId) { mutableStateOf(false) }
    val pinning = rememberSaveable(accountId, chatId, forumTopicId) { mutableStateOf(false) }
    return remember(target, deleting, pinning) { ChatMessageActionsState(target, deleting, pinning) }
}

@Composable
internal fun ChatMessageActionEffects(
    state: ChatMessageActionsState,
    conversation: ConversationRepository,
    chatId: Long,
    actionMessageId: Long?,
    reactionOwnerId: Long?,
    formatError: (Throwable) -> String
) {
    val currentFormatError by rememberUpdatedState(formatError)
    LaunchedEffect(state, actionMessageId) {
        if (actionMessageId == null) state.clearPresentation()
    }
    LaunchedEffect(state, conversation, chatId, state.targetId, state.capabilitiesAttempt) {
        val id = state.targetId ?: return@LaunchedEffect
        state.capabilities = null
        state.capabilitiesError = null
        val result = conversation.messageCapabilities(chatId, id)
        currentCoroutineContext().ensureActive()
        if (state.targetId == id) {
            state.capabilities = result.getOrNull()
            state.capabilitiesError = result.exceptionOrNull()?.let(currentFormatError)
        }
    }
    LaunchedEffect(state, conversation, chatId, actionMessageId, reactionOwnerId) {
        state.reactionOptions = emptyList()
        val owner = reactionOwnerId ?: return@LaunchedEffect
        val result = conversation.availableReactions(chatId, owner)
        currentCoroutineContext().ensureActive()
        if (state.targetId == actionMessageId) state.reactionOptions = result.getOrNull().orEmpty()
    }
}

@Composable
internal fun ChatMessageDeletePrompt(
    message: ChatMessage,
    state: ChatMessageActionsState,
    conversation: ConversationRepository,
    scope: CoroutineScope,
    onDeleted: () -> Unit
) {
    val capabilities = state.capabilities ?: return
    val context = LocalContext.current
    DeleteConfirmPanel(
        capabilities, state.deleteEveryone, state.deleteBusy, state.deleteError,
        onChoose = { state.deleteEveryone = it },
        onCancel = { state.deleting = false; state.deleteError = null },
        onConfirm = {
            if (!state.deleteBusy && state.targetId == message.id) {
                val destination = if (state.deleteEveryone) DeleteMessageScope.ForEveryone else DeleteMessageScope.ForSelf
                state.deleteBusy = true
                state.deleteError = null
                scope.launch {
                    try {
                        val result = conversation.deleteMessage(message.chatId, message.id, destination)
                        if (state.targetId == message.id) {
                            if (result.isSuccess) onDeleted()
                            else state.deleteError = result.exceptionOrNull()?.userMessage(context, R.string.err_chat_delete_failed)
                        }
                    } finally {
                        state.deleteBusy = false
                    }
                }
            }
        }
    )
}
