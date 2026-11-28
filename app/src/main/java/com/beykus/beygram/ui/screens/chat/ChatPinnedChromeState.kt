package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.domain.repository.ConversationRepository

/** Keeps loaded pins and transition geometry alive while the preview row is hidden. */
internal class ChatPinnedChromeState(
    val messages: PinnedMessagesState,
    private val listOpen: MutableState<Boolean>
) {
    val isListOpen: Boolean get() = listOpen.value

    var previewShown by mutableStateOf(false)
    var livePreview by mutableStateOf<PinnedPreviewSource?>(null)
    var liveHeader by mutableStateOf<Rect?>(null)

    var previewSource by mutableStateOf<PinnedPreviewSource?>(null)
        private set
    var headerSource by mutableStateOf<Rect?>(null)
        private set

    fun openList() {
        previewSource = null
        headerSource = null
        listOpen.value = true
    }

    fun openListFromPreview(source: PinnedPreviewSource) {
        previewSource = source
        headerSource = liveHeader
        listOpen.value = true
    }

    fun closeList() {
        listOpen.value = false
        previewSource = null
        headerSource = null
    }
}

@Composable
internal fun rememberChatPinnedChromeState(
    conversation: ConversationRepository,
    chatId: Long,
    forumTopicId: Long,
    connected: Boolean,
    listOpen: MutableState<Boolean>
): ChatPinnedChromeState {
    val version by remember(conversation, chatId) {
        conversation.observePinnedMessagesVersion(chatId)
    }.collectAsStateWithLifecycle()
    val state = remember(conversation, chatId, forumTopicId, listOpen) {
        val messages = PinnedMessagesState(chatId, forumTopicId) { from ->
            conversation.pinnedMessages(chatId, from, forumTopicId)
        }
        ChatPinnedChromeState(messages, listOpen)
    }
    LaunchedEffect(state, version, connected, state.messages.retryTick) {
        state.messages.refresh(version, connected)
    }
    return state
}
