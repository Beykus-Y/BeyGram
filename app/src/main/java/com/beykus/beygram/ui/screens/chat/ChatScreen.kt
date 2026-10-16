package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Composable
fun ChatScreen(
    chat: ChatPreview, history: ChatHistory, conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    onBack: () -> Unit, onChatInfo: () -> Unit
) {
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var replyId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var actionsId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var editId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var selectedMediaId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var contextChatId by rememberSaveable(chat.id) { mutableStateOf(0L) }
    var contextMessageId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var highlighted by remember { mutableStateOf<Long?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val limits by conversation.messageLimits.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages = history.messages
    val currentMessages by rememberUpdatedState(messages)
    val newest = messages.lastOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    val minimumVisible = with(LocalDensity.current) { 64.dp.roundToPx() }
    BackHandler(onBack = onBack)

    // Loading/caching messages never triggers receipts; only the focused, resumed viewport does.
    LaunchedEffect(chat.id, lifecycle, conversation) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                if (!window.isWindowFocused) emptyList() else {
                    val layout = listState.layoutInfo
                    val eligible = currentMessages.filter { it.sendState == MessageSendState.Sent }.mapTo(hashSetOf()) { it.id }
                    layout.visibleItemsInfo.filter { item ->
                        val visible = minOf(item.offset + item.size, layout.viewportEndOffset) - maxOf(item.offset, layout.viewportStartOffset)
                        visible >= minOf(item.size / 2, minimumVisible).coerceAtLeast(1)
                    }.mapNotNull { it.key as? Long }.filter { it in eligible }.sorted()
                }
            }.debounce(350).distinctUntilChanged().collect { ids -> if (ids.isNotEmpty()) conversation.messagesVisible(chat.id, ids) }
        }
    }
    LaunchedEffect(chat.id, newest?.id) {
        if (messages.isNotEmpty() && (listState.firstVisibleItemIndex < 2 || newest?.isOutgoing == true)) listState.animateScrollToItem(0)
    }
    LaunchedEffect(highlighted) { if (highlighted != null) { delay(2200); highlighted = null } }
    fun jump(reply: MessageReply) {
        if (reply.isUnavailable || reply.messageId <= 0) { error = "Оригинал удалён или недоступен"; return }
        val index = messages.asReversed().indexOfFirst { it.id == reply.messageId && it.chatId == reply.chatId }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index); highlighted = reply.messageId }
        else { contextChatId = reply.chatId; contextMessageId = reply.messageId }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ChatTopBar(chat, onBack, onChatInfo, observeFile, onDownloadFile)
        LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages.asReversed(), key = { it.id }) { message ->
                MessageBubble(message, observeFile, onDownloadFile, isRead = message.isOutgoing && message.sendState == MessageSendState.Sent && message.id <= history.lastReadOutboxMessageId,
                    highlighted = message.id == highlighted, onOpen = { selectedMediaId = message.id }, onActions = { actionsId = message.id }, onReplyClick = ::jump)
            }
            history.error?.let { item(key = "history_error") { Text(it, color = palette.warningText, modifier = Modifier.padding(8.dp)) } }
            if (history.isLoading) item(key = "history_loading") { Text("Загружаем историю…", color = palette.textSecondary, modifier = Modifier.padding(8.dp)) }
            else if (history.hasOlderMessages) item(key = "history_more") { TextButton(onClick = { conversation.loadOlderMessages(chat.id) }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить более ранние сообщения") } }
            else if (messages.isEmpty() && history.error == null) item(key = "history_empty") { Text("В чате пока нет сообщений", color = palette.textSecondary, modifier = Modifier.padding(8.dp)) }
        }
        error?.let { failure -> Row(Modifier.fillMaxWidth().background(palette.warningSurface).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(failure, color = palette.warningText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { error = null }) { Text("×") }
        } }
        val replyTarget = messages.firstOrNull { it.id == replyId }
        if (replyId != null) Row(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ответ · ${replyTarget?.senderName?.ifBlank { if (replyTarget.isOutgoing) "Вы" else chat.title } ?: "Сообщение"}", color = palette.telegramBlue, fontSize = 12.sp)
                Text(replyTarget?.summary() ?: "Сообщение недоступно", color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            }
            TextButton(enabled = !sending, onClick = { replyId = null }) { Text("×") }
        }
        Row(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AttachmentButton(chat.id, draft, { draft = it }, captionLimit = limits.caption, enabled = !sending, sendMedia = { media, caption ->
                val result = conversation.sendMedia(chat.id, media, caption, replyId)
                if (result.isSuccess) replyId = null
                result
            })
            OutlinedTextField(draft, { draft = it }, enabled = !sending, modifier = Modifier.weight(1f), placeholder = { Text("Сообщение") }, maxLines = 5,
                shape = RoundedCornerShape(20.dp), colors = beyGramTextFieldColors(), isError = draft.codePointCount(0, draft.length) > limits.text)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { scope.launch {
                sending = true; error = null
                try {
                    val result = conversation.sendMessage(chat.id, draft, replyId)
                    if (result.isSuccess) { draft = ""; replyId = null } else error = result.exceptionOrNull()?.message
                } finally { sending = false }
            } }, enabled = !sending && draft.isNotBlank() && draft.codePointCount(0, draft.length) <= limits.text, shape = CircleShape,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.telegramBlue, contentColor = Color.White)) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(">", fontSize = 22.sp)
            }
        }
    }
    messages.firstOrNull { it.id == selectedMediaId }?.media?.let { media -> MediaViewer(media, observeFile, onDownloadFile,
        onViewed = { selectedMediaId?.let { conversation.openMessageContent(chat.id, it) } }, onClose = { selectedMediaId = null }) }
    messages.firstOrNull { it.id == actionsId }?.let { message -> MessageActionsDialog(message, conversation, onClose = { actionsId = null }, onReply = { replyId = message.id }, onEdit = { editId = message.id }) }
    messages.firstOrNull { it.id == editId }?.let { message -> EditMessageDialog(message, if (message.media == null) limits.text else limits.caption, conversation, onClose = { editId = null }) }
    contextMessageId?.let { id -> MessageContextDialog(contextChatId, id, conversation, observeFile, onDownloadFile, onClose = { contextMessageId = null }) }
}

@Composable
private fun ChatTopBar(chat: ChatPreview, onBack: () -> Unit, onInfo: () -> Unit, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("<") }
        TelegramAvatar(chat.initials, Color(chat.avatarColorArgb), 42.dp, chat.photo, observeFile, downloadFile, Modifier.clickable(onClick = onInfo))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onInfo)) {
            Text(chat.title, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (chat.isGroup) "Группа" else "Личный чат", color = palette.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun MessageBubble(message: ChatMessage, observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    isRead: Boolean = false, highlighted: Boolean = false, onOpen: () -> Unit, onActions: (() -> Unit)? = null, onReplyClick: (MessageReply) -> Unit = {}) {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 292.dp).clip(RoundedCornerShape(18.dp))
            .background(if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)
            .then(if (highlighted) Modifier.border(2.dp, palette.telegramBlue, RoundedCornerShape(18.dp)) else Modifier)
            .combinedClickable(onClick = { if (message.media != null) onOpen() }, onLongClick = onActions).padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (!message.isOutgoing && message.senderName.isNotBlank()) Text(message.senderName, color = palette.telegramBlue, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            message.reply?.let { reply ->
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(palette.screen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onReplyClick(reply) }.padding(8.dp)) {
                    Text(reply.senderName.ifBlank { "Ответ на сообщение" }, color = palette.telegramBlue, fontSize = 12.sp, maxLines = 1)
                    Text(reply.text, color = palette.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            message.media?.let { MediaPreview(it, observeFile, onDownloadFile, onOpen, onActions) }
            if (message.text.isNotBlank()) Text(message.text, color = palette.textPrimary, fontSize = 15.sp, lineHeight = 20.sp)
            Spacer(Modifier.size(4.dp))
            val status = if (message.isOutgoing) when (message.sendState) {
                MessageSendState.Sending -> " · отправка…"
                MessageSendState.Failed -> " · не отправлено"
                MessageSendState.Sent -> if (isRead) " ✓✓" else " ✓"
            } else ""
            Text((if (message.isEdited) "изменено · " else "") + message.time + status,
                color = if (message.sendState == MessageSendState.Failed) palette.warningText else palette.textSecondary,
                fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}
