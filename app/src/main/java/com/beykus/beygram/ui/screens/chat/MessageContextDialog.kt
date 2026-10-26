package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.R
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.deeplink.copyableMessageLinks
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A separate history window keeps distant replies from creating gaps in the main pagination. */
@Composable
internal fun MessageContextDialog(chatId: Long, targetId: Long, conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onClose: () -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    allowEntityCopy: Boolean = true) {
    var context by remember(chatId, targetId) { mutableStateOf<MessageContext?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var stickerSetId by remember { mutableStateOf<Long?>(null) }
    var viewerItem by remember { mutableStateOf<MediaViewerItem?>(null) }
    var viewerChatId by remember { mutableStateOf(0L) }
    var openedViewOnceIds by remember(chatId, targetId) { mutableStateOf(emptySet<Long>()) }
    var rights by remember { mutableStateOf<MessageRights?>(null) }
    var copyLinks by remember(chatId, targetId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(selected?.id) {
        rights = null
        val message = selected ?: return@LaunchedEffect
        rights = conversation.messageCapabilities(message.chatId, message.id).getOrNull()?.let { MessageRights(message.id, it) }
    }
    val palette = beyGramPalette()
    // Имя context занято состоянием контекста сообщения, поэтому контекст Android берём под другим именем.
    val androidContext = LocalContext.current
    val list = rememberLazyListState()
    val reactionScope = rememberCoroutineScope()
    LaunchedEffect(chatId, targetId, attempt) {
        context = null; error = null
        val result = conversation.messageContext(chatId, targetId)
        context = result.getOrNull(); error = result.exceptionOrNull()?.userMessage(androidContext, R.string.err_chat_history_load_failed)
    }
    LaunchedEffect(context) {
        context?.messages?.indexOfFirst { it.id == targetId }?.takeIf { it >= 0 }?.let { list.scrollToItem(it) }
    }
    // The dialog keeps its own copy of the messages, so the reactions TDLib returns are written back into that copy.
    fun react(messageId: Long, reaction: ReactionOption) {
        reactionScope.launch {
            conversation.toggleReaction(chatId, messageId, reaction, addToRecent = false)
                .onSuccess { reactions -> context = context?.let { current -> current.copy(messages = current.messages.map { if (it.id == messageId) it.copy(reactions = reactions) else it }) } }
                .onFailure { failure -> Toast.makeText(androidContext, failure.userMessage(androidContext, R.string.chat_reaction_failed), Toast.LENGTH_SHORT).show() }
        }
    }
    // Poll mutations refresh the shared history store; reload this separate snapshot so vote/results/options are visible.
    fun refreshPollSnapshot() {
        reactionScope.launch {
            conversation.messageContext(chatId, targetId).onSuccess { context = it }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.textPrimary) }
                Column(Modifier.weight(1f)) {
                    Text(context?.title ?: stringResource(R.string.message_original_reply), color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.message_context_subtitle), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = palette.divider)
            when {
                error != null -> Column(Modifier.padding(24.dp)) {
                    Text(error.orEmpty(), color = palette.warningText, modifier = Modifier.errorAnnouncement())
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.common_retry), color = palette.accent) }
                }
                context == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(context!!.messages, key = { it.id }) { message ->
                        MessageBubble(message, observeFile, downloadFile, highlighted = message.id == targetId, onOpen = {
                            selected = message
                            viewerItem = message.media?.let { MediaViewerItem(message.id, it, message.isViewOnce) }
                            viewerChatId = message.chatId
                        }, onStickerOpen = { media -> stickerSetId = media.stickerSetId },
                            onReaction = ::react, observeCustomEmoji = conversation::observeCustomEmoji, inlineContent = { InlineBotButtons(message, conversation) },
                            onActions = if (!allowEntityCopy || message.isViewOnce) null else copyableMessageLinks(message.text, message.entities)
                                .takeIf { it.isNotEmpty() }
                                ?.let { links -> { copyLinks = links } },
                            onPollVote = { messageId, choices ->
                                reactionScope.launch {
                                    conversation.votePoll(message.chatId, messageId, choices)
                                        .onSuccess { refreshPollSnapshot() }
                                        .onFailure { Toast.makeText(androidContext, it.userMessage(androidContext, R.string.chat_poll_vote_failed), Toast.LENGTH_SHORT).show() }
                                }
                            },
                            onPollStop = { messageId ->
                                reactionScope.launch {
                                    conversation.stopPoll(message.chatId, messageId)
                                        .onSuccess { refreshPollSnapshot() }
                                        .onFailure { Toast.makeText(androidContext, it.userMessage(androidContext, R.string.chat_poll_stop_failed), Toast.LENGTH_SHORT).show() }
                                }
                            },
                            onLoadPollVoters = { messageId, optionIndex, offset ->
                                conversation.pollVoters(message.chatId, messageId, optionIndex, offset)
                            },
                            onAddPollOption = { messageId, text ->
                                conversation.addPollOption(message.chatId, messageId, text)
                                    .onSuccess { refreshPollSnapshot() }
                            },
                            paidContent = rememberPaidContentActions(conversation),
                            onOpenMessageEntity = onOpenMessageEntity)
                    }
                }
            }
        }
    }
    StickerSetBrowser(
        setId = stickerSetId,
        conversation = conversation,
        observeFile = observeFile,
        downloadFile = downloadFile,
        onDismiss = { stickerSetId = null }
    )
    if (copyLinks.isNotEmpty()) MessageLinkCopyDialog(
        links = copyLinks,
        onCopy = { value ->
            androidContext.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(androidContext.getString(R.string.message_action_copy_entity), value))
            Toast.makeText(androidContext, androidContext.getString(R.string.message_entity_copied), Toast.LENGTH_SHORT).show()
            copyLinks = emptyList()
        },
        onDismiss = { copyLinks = emptyList() }
    )
    // The viewer keeps its own opened page; deleting the live message must not dismiss it mid-view.
    val activeItem = viewerItem
    if (activeItem != null) {
        val activeChatId = viewerChatId.takeIf { it > 0L } ?: chatId
        val pages = MediaViewerPolicy.retainViewOncePage(listOf(activeItem), activeItem)
        MediaViewer(pages = pages, selectedId = activeItem.messageId, onSelect = {},
            observeFile = observeFile, downloadFile = downloadFile, autoDownloadFile = autoDownloadFile,
            onViewed = { id ->
                if (!activeItem.viewOnce || id !in openedViewOnceIds) {
                    if (activeItem.viewOnce) openedViewOnceIds = openedViewOnceIds + id
                    conversation.openMessageContent(activeChatId, id)
                }
            },
            onClose = { viewerItem = null; viewerChatId = 0L; selected = null },
            canExport = MediaViewerPolicy.rightsFor(activeItem.messageId, rights)?.canSave == true && !activeItem.viewOnce,
            confirmExport = { id -> conversation.messageCapabilities(activeChatId, id).getOrNull()?.canSave == true })
    }
}
