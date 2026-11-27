package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.DeleteMessageScope
import com.beykus.beygram.domain.model.MAX_MESSAGE_BATCH_SIZE
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Stable
internal class ChatSelectionController(
    private val chatId: Long,
    private val conversation: ConversationRepository,
    private val context: Context,
    private val clipboard: ClipboardManager,
    private val scope: CoroutineScope,
    private val messagesProvider: () -> List<ChatMessage>,
    private val observeFile: (Int) -> StateFlow<com.beykus.beygram.domain.model.MediaFileState>,
    private val onDownloadFile: (Int) -> Unit,
    private val closeActions: () -> Unit,
    selectionModeState: MutableState<Boolean>,
    selectionIdsState: MutableState<Set<Long>>,
    selectionAnchorIdState: MutableState<Long?>,
    selectionBusyState: MutableState<Boolean>,
    selectionErrorState: MutableState<String?>,
    selectionForwardOpenState: MutableState<Boolean>,
    frozenForwardSelectionState: MutableState<LongArray>,
    selectionDeleteOpenState: MutableState<Boolean>,
    selectionDeleteRightsState: MutableState<BatchDeleteRights?>,
    selectionDeleteEveryoneState: MutableState<Boolean>,
    selectionDeleteBusyState: MutableState<Boolean>,
    selectionDeleteErrorState: MutableState<String?>
) {
    var selectionMode by selectionModeState
    var selectionIds by selectionIdsState
    var selectionAnchorId by selectionAnchorIdState
    var selectionBusy by selectionBusyState
    var selectionError by selectionErrorState
    var selectionForwardOpen by selectionForwardOpenState
    var frozenForwardSelection by frozenForwardSelectionState
    var selectionDeleteOpen by selectionDeleteOpenState
    var selectionDeleteRights by selectionDeleteRightsState
    var selectionDeleteEveryone by selectionDeleteEveryoneState
    var selectionDeleteBusy by selectionDeleteBusyState
    var selectionDeleteError by selectionDeleteErrorState

    fun enterSelection(message: ChatMessage? = null) {
        val messages = messagesProvider()
        closeActions()
        selectionMode = true
        selectionError = null
        if (message != null) {
            val group = selectionGroupIds(messages, message.id)
            if (selectionIds.size + group.count { it !in selectionIds } > MAX_MESSAGE_BATCH_SIZE) {
                selectionError = context.getString(R.string.message_selection_limit, MAX_MESSAGE_BATCH_SIZE)
                return
            }
            selectionIds = selectionIds + group
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectionIds = emptySet()
        selectionAnchorId = null
        selectionError = null
        selectionForwardOpen = false
        frozenForwardSelection = longArrayOf()
        selectionDeleteOpen = false
        selectionDeleteRights = null
        selectionDeleteBusy = false
        selectionDeleteError = null
    }

    fun toggleSelection(message: ChatMessage) {
        val messages = messagesProvider()
        val group = selectionGroupIds(messages, message.id)
        val removing = group.all { it in selectionIds }
        if (!removing && selectionIds.size + group.count { it !in selectionIds } > MAX_MESSAGE_BATCH_SIZE) {
            selectionError = context.getString(R.string.message_selection_limit, MAX_MESSAGE_BATCH_SIZE)
            return
        }
        selectionIds = if (removing) selectionIds - group else selectionIds + group
        selectionError = null
    }

    fun copySelection(selectedMessages: List<ChatMessage>) {
        if (selectionBusy) return
        val messages = messagesProvider()
        val targets = selectedMessages.filter { it.text.isNotBlank() }
        if (targets.isEmpty()) {
            selectionError = context.getString(R.string.message_selection_copy_empty)
            return
        }
        selectionBusy = true
        selectionError = null
        scope.launch {
            try {
                val results = coroutineScope {
                    targets.map { message -> async { conversation.messageCapabilities(chatId, message.id) } }.awaitAll()
                }
                val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                if (failure != null) {
                    selectionError = failure.userMessage(context, R.string.err_chat_actions_load_failed)
                    return@launch
                }
                if (results.any { it.getOrNull()?.canCopy != true }) {
                    selectionError = context.getString(R.string.err_messages_cannot_copy)
                    return@launch
                }
                val text = selectedText(messages, targets.mapTo(hashSetOf()) { it.id })
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_message), text))
                Toast.makeText(context, context.getString(R.string.message_selection_copied, targets.size), Toast.LENGTH_SHORT).show()
                exitSelection()
            } finally {
                selectionBusy = false
            }
        }
    }

    fun saveSelection(selectedMessages: List<ChatMessage>) {
        if (selectionBusy) return
        val targets = selectedMessages.filter { it.media != null }
        if (targets.isEmpty()) {
            selectionError = context.getString(R.string.message_selection_save_none)
            return
        }
        selectionBusy = true
        selectionError = null
        scope.launch {
            var saved = 0
            var skipped = 0
            var failed = 0
            try {
                for (message in targets) {
                    val media = message.media ?: continue
                    val canSave = conversation.messageCapabilities(chatId, message.id).getOrNull()?.canSave == true
                    if (!canSave || media.isSecret) {
                        skipped++
                        continue
                    }
                    val state = observeFile(media.fileId)
                    val path = state.value.localPath ?: run {
                        onDownloadFile(media.fileId)
                        withTimeoutOrNull(60_000) {
                            state.first { it.localPath != null || it.error != null }.localPath
                        }
                    }
                    if (path == null) failed++
                    else if (withContext(Dispatchers.IO) { saveSelectedMediaToDevice(context, path, media) }.isSuccess) saved++
                    else failed++
                }
            } finally {
                selectionBusy = false
            }
            val message = when {
                saved > 0 && skipped + failed == 0 -> context.getString(R.string.message_selection_saved, saved)
                saved > 0 -> context.getString(R.string.message_selection_saved_partial, saved, skipped + failed)
                skipped > 0 -> context.getString(R.string.message_selection_save_denied)
                else -> context.getString(R.string.media_save_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (saved == 0) selectionError = message
        }
    }

    fun requestSelectionDelete(selectedMessages: List<ChatMessage>) {
        if (selectionBusy || selectionIds.isEmpty()) return
        selectionBusy = true
        selectionError = null
        scope.launch {
            try {
                val results = coroutineScope {
                    selectedMessages.map { message -> async { conversation.messageCapabilities(chatId, message.id) } }.awaitAll()
                }
                val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                if (failure != null) {
                    selectionError = failure.userMessage(context, R.string.err_chat_actions_load_failed)
                    return@launch
                }
                val rights = batchDeleteRights(results.mapNotNull { it.getOrNull() })
                if (!rights.canDelete) {
                    selectionError = context.getString(R.string.err_messages_cannot_delete)
                    return@launch
                }
                selectionDeleteRights = rights
                selectionDeleteEveryone = !rights.canDeleteForSelf
                selectionDeleteError = null
                selectionDeleteOpen = true
            } finally {
                selectionBusy = false
            }
        }
    }

    fun confirmSelectionDelete(selectedMessages: List<ChatMessage>) {
        scope.launch {
            selectionDeleteBusy = true
            selectionDeleteError = null
            try {
                val ids = selectedMessages.map { it.id }.sorted()
                val deleteScope = if (selectionDeleteEveryone) DeleteMessageScope.ForEveryone else DeleteMessageScope.ForSelf
                val result = conversation.deleteMessages(chatId, ids, deleteScope)
                if (result.isSuccess) {
                    selectionDeleteOpen = false
                    Toast.makeText(context, context.getString(R.string.message_selection_deleted, ids.size), Toast.LENGTH_SHORT).show()
                    exitSelection()
                } else {
                    selectionDeleteError = result.exceptionOrNull().userMessage(context, R.string.err_chat_delete_failed)
                }
            } finally {
                selectionDeleteBusy = false
            }
        }
    }
}

@Composable
internal fun rememberChatSelectionController(
    chatId: Long,
    accountId: String,
    conversation: ConversationRepository,
    context: Context,
    clipboard: ClipboardManager,
    scope: CoroutineScope,
    messages: List<ChatMessage>,
    observeFile: (Int) -> StateFlow<com.beykus.beygram.domain.model.MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    closeActions: () -> Unit
): ChatSelectionController {
    // Multi-select is a separate mode from the single-message action bar. Albums are selected as one group.
    val selectionModeState = rememberSaveable(chatId) { mutableStateOf(false) }
    val selectionSaver = remember {
        listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })
    }
    val selectionIdsState = rememberSaveable(chatId, stateSaver = selectionSaver) { mutableStateOf(emptySet<Long>()) }
    // The message the user long-pressed to start selection; its one-message actions stay reachable while the selection is still just it.
    val selectionAnchorIdState = rememberSaveable(chatId) { mutableStateOf<Long?>(null) }
    val selectionBusyState = remember { mutableStateOf(false) }
    val selectionErrorState = remember { mutableStateOf<String?>(null) }
    val selectionForwardOpenState = rememberSaveable(chatId) { mutableStateOf(false) }
    val frozenForwardSelectionState = rememberSaveable(accountId, chatId) { mutableStateOf(longArrayOf()) }
    val selectionDeleteOpenState = rememberSaveable(chatId) { mutableStateOf(false) }
    val selectionDeleteRightsState = remember { mutableStateOf<BatchDeleteRights?>(null) }
    val selectionDeleteEveryoneState = remember { mutableStateOf(false) }
    val selectionDeleteBusyState = remember { mutableStateOf(false) }
    val selectionDeleteErrorState = remember { mutableStateOf<String?>(null) }

    val messagesState = androidx.compose.runtime.rememberUpdatedState(messages)
    val controller = remember(chatId, accountId, conversation, context, clipboard, scope) {
        ChatSelectionController(
            chatId = chatId,
            conversation = conversation,
            context = context,
            clipboard = clipboard,
            scope = scope,
            messagesProvider = { messagesState.value },
            observeFile = observeFile,
            onDownloadFile = onDownloadFile,
            closeActions = closeActions,
            selectionModeState = selectionModeState,
            selectionIdsState = selectionIdsState,
            selectionAnchorIdState = selectionAnchorIdState,
            selectionBusyState = selectionBusyState,
            selectionErrorState = selectionErrorState,
            selectionForwardOpenState = selectionForwardOpenState,
            frozenForwardSelectionState = frozenForwardSelectionState,
            selectionDeleteOpenState = selectionDeleteOpenState,
            selectionDeleteRightsState = selectionDeleteRightsState,
            selectionDeleteEveryoneState = selectionDeleteEveryoneState,
            selectionDeleteBusyState = selectionDeleteBusyState,
            selectionDeleteErrorState = selectionDeleteErrorState
        )
    }

    // A deleted or reloaded-away message must not keep a phantom selection count. Wait for the first loaded page,
    // otherwise a restored selection would be cleared before history arrives.
    LaunchedEffect(controller.selectionMode, messages) {
        if (controller.selectionMode && messages.isNotEmpty()) {
            val loaded = messages.mapTo(hashSetOf()) { it.id }
            val kept = controller.selectionIds.filterTo(linkedSetOf()) { it in loaded }
            if (kept != controller.selectionIds) controller.selectionIds = kept
        }
    }

    return controller
}
