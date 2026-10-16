package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.beykus.beygram.ui.components.beyGramFilledTextFieldColors
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
    chat: ChatPreview, info: ProfileInfo?, history: ChatHistory, conversation: ConversationRepository, chats: List<ChatPreview>,
    observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    onBack: () -> Unit, onChatInfo: () -> Unit, connectionState: TelegramConnectionState
) {
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var replyId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var actionsId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var editId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var editText by rememberSaveable(chat.id) { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable(chat.id) { mutableStateOf(false) }
    var deleteEveryone by remember { mutableStateOf(false) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var capabilities by remember { mutableStateOf<MessageCapabilities?>(null) }
    var capabilitiesError by remember { mutableStateOf<String?>(null) }
    var capabilitiesAttempt by remember { mutableIntStateOf(0) }
    var selectedMediaId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var viewerCapabilities by remember { mutableStateOf<MessageCapabilities?>(null) }
    var forwardId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var forwardBusy by remember { mutableStateOf(false) }
    var forwardError by remember { mutableStateOf<String?>(null) }
    var contextChatId by rememberSaveable(chat.id) { mutableStateOf(0L) }
    var contextMessageId by rememberSaveable(chat.id) { mutableStateOf<Long?>(null) }
    var highlighted by remember { mutableStateOf<Long?>(null) }
    var searching by rememberSaveable(chat.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(chat.id) { mutableStateOf("") }
    var searchResults by remember(chat.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var searchNextId by remember(chat.id) { mutableLongStateOf(0L) }
    var searchBusy by remember(chat.id) { mutableStateOf(false) }
    var searchError by remember(chat.id) { mutableStateOf<String?>(null) }
    fun closeSearch() { searching = false; searchQuery = "" }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val limits by conversation.messageLimits.collectAsStateWithLifecycle()
    val typingFlow = remember(conversation, chat.id) { conversation.observeTyping(chat.id) }
    val typingNames by typingFlow.collectAsStateWithLifecycle()
    val palette = beyGramPalette()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val attachments = rememberAttachmentState(chat.id) { media, caption ->
        val result = conversation.sendMedia(chat.id, media, caption, replyId)
        if (result.isSuccess) replyId = null
        result
    }
    val context = LocalContext.current
    val videoNote = rememberVideoNoteRecorder(
        onFinished = { media -> scope.launch { conversation.sendMedia(chat.id, media, "", replyId).onSuccess { replyId = null }.onFailure { error = it.message ?: "Не удалось отправить кружок" } } },
        onError = { error = it }
    )
    val videoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (!granted.values.all { it }) error = "Для кружков нужны камера и микрофон"
    }
    fun startVideoNote() { if (videoNoteAllowed(context)) videoNote.press() else videoPermissions.launch(videoNotePermissions) }
    val messages = history.messages
    val currentMessages by rememberUpdatedState(messages)
    val newest = messages.lastOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Голосовое и кружок не играют в фоне и после выхода из чата; запись кружка в фоне отменяется.
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                VoicePlayback.stop()
                InlinePlaybackFocus.stopAll()
                videoNote.release(cancel = true)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); VoicePlayback.stop(); InlinePlaybackFocus.stopAll() }
    }
    val window = LocalWindowInfo.current
    val minimumVisible = with(LocalDensity.current) { 64.dp.roundToPx() }
    val actionMessage = messages.firstOrNull { it.id == actionsId }
    val editMessage = messages.firstOrNull { it.id == editId }
    val editLimit = if (editMessage?.media == null) limits.text else limits.caption
    fun closeActions() { actionsId = null; deleting = false; deleteError = null; capabilities = null; capabilitiesError = null }
    fun closeEdit() { editId = null; editText = ""; editError = null }
    LaunchedEffect(actionMessage) {
        if (actionMessage == null) { deleting = false; deleteError = null; capabilities = null; capabilitiesError = null }
    }
    LaunchedEffect(actionsId, capabilitiesAttempt) {
        val id = actionsId ?: return@LaunchedEffect
        capabilities = null; capabilitiesError = null
        val result = conversation.messageCapabilities(chat.id, id)
        capabilities = result.getOrNull()
        capabilitiesError = result.exceptionOrNull()?.message
    }
    LaunchedEffect(selectedMediaId) {
        viewerCapabilities = null
        val id = selectedMediaId ?: return@LaunchedEffect
        viewerCapabilities = conversation.messageCapabilities(chat.id, id).getOrNull()
    }
    BackHandler(enabled = searching || actionMessage != null || editMessage != null || attachments.menuOpen || attachments.active) {
        when {
            searching -> closeSearch()
            deleting -> { deleting = false; deleteError = null }
            actionMessage != null -> closeActions()
            editMessage != null -> closeEdit()
            else -> attachments.discardAll()
        }
    }
    BackHandler(enabled = actionMessage == null && editMessage == null && !attachments.menuOpen && !attachments.active && !searching, onBack = onBack)
    BackHandler(enabled = videoNote.active) { videoNote.release(cancel = true) }

    // Loading/caching messages never triggers receipts; only the focused, resumed viewport does.
    LaunchedEffect(chat.id, lifecycle, conversation) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                if (!window.isWindowFocused || searching) emptyList() else {
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
    // Results refresh after a short pause in typing; a new query replaces the pending request.
    LaunchedEffect(searching, searchQuery) {
        searchResults = emptyList(); searchNextId = 0L; searchError = null
        val needle = searchQuery.trim()
        searchBusy = searching && needle.isNotEmpty()
        if (!searching || needle.isEmpty()) return@LaunchedEffect
        delay(300)
        conversation.searchMessages(chat.id, needle, 0L)
            .onSuccess { page -> searchResults = page.messages; searchNextId = page.nextFromMessageId }
            .onFailure { searchError = it.message ?: "Не удалось выполнить поиск" }
        searchBusy = false
    }
    fun loadMoreResults() {
        val needle = searchQuery.trim()
        scope.launch {
            searchBusy = true
            conversation.searchMessages(chat.id, needle, searchNextId)
                .onSuccess { page -> if (searchQuery.trim() == needle) { searchResults = searchResults + page.messages; searchNextId = page.nextFromMessageId } }
                .onFailure { if (searchQuery.trim() == needle) { searchError = it.message ?: "Не удалось выполнить поиск" } }
            if (searchQuery.trim() == needle) { searchBusy = false }
        }
    }
    fun openSearchResult(message: ChatMessage) {
        val index = messages.asReversed().indexOfFirst { it.id == message.id }
        closeSearch()
        if (index >= 0) scope.launch { listState.animateScrollToItem(index); highlighted = message.id }
        else { contextChatId = chat.id; contextMessageId = message.id }
    }

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (actionMessage != null) MessageActionsBar(
            capabilities = capabilities, error = capabilitiesError, hasText = actionMessage.text.isNotBlank(), isMedia = actionMessage.media != null,
            onClose = ::closeActions, onRetry = { capabilitiesAttempt++ },
            onReply = { replyId = actionMessage.id; closeActions() },
            onCopy = { clipboard.setPrimaryClip(ClipData.newPlainText("Сообщение", actionMessage.text)); closeActions() },
            onEdit = { editId = actionMessage.id; editText = actionMessage.text; closeActions() },
            onDelete = { deleteEveryone = capabilities?.canDeleteForSelf == false; deleting = true }
        ) else if (searching) ChatSearchBar(searchQuery, { searchQuery = it }, ::closeSearch)
        else ChatTopBar(chat, chatPresenceLine(chat, info), onBack, onChatInfo, { searching = true }, observeFile, onDownloadFile)
        if (connectionState == TelegramConnectionState.WaitingForNetwork) Row(
            Modifier.fillMaxWidth().background(palette.warningSurface).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WifiOff, null, tint = palette.warningText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Нет сети. Сообщения отправятся позже", color = palette.warningText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages.asReversed(), key = { it.id }) { message ->
                MessageBubble(message, observeFile, onDownloadFile, isRead = message.isOutgoing && message.sendState == MessageSendState.Sent && message.id <= history.lastReadOutboxMessageId,
                    highlighted = message.id == highlighted || message.id == actionsId, onOpen = { selectedMediaId = message.id }, onActions = { actionsId = message.id }, onReplyClick = ::jump,
                    onPlayed = { conversation.openMessageContent(chat.id, message.id) })
            }
            history.error?.let { item(key = "history_error") { Text(it, color = palette.warningText, modifier = Modifier.padding(8.dp)) } }
            if (history.isLoading) item(key = "history_loading") { Text("Загружаем историю…", color = palette.textSecondary, modifier = Modifier.padding(8.dp)) }
            else if (history.hasOlderMessages) item(key = "history_more") { TextButton(onClick = { conversation.loadOlderMessages(chat.id) }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить более ранние сообщения") } }
            else if (messages.isEmpty() && history.error == null) item(key = "history_empty") { Text("В чате пока нет сообщений", color = palette.textSecondary, modifier = Modifier.padding(8.dp)) }
        }
        val away by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
        if (away) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).clip(RoundedCornerShape(50)).background(palette.surface)
            .border(1.dp, palette.divider, RoundedCornerShape(50)).clickable { scope.launch { listState.animateScrollToItem(0) } }
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowDownward, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("К последним", color = palette.accentInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (searching) ChatSearchResults(searchQuery, searchResults, searchBusy, searchError, hasMore = searchNextId != 0L,
            onOpen = ::openSearchResult, onMore = ::loadMoreResults)
        if (videoNote.active) VideoNoteRecordingOverlay(videoNote)
        }
        error?.let { failure -> Row(Modifier.fillMaxWidth().background(palette.warningSurface).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(failure, color = palette.warningText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { error = null }) { Icon(Icons.Rounded.Close, "Закрыть", tint = palette.warningText) }
        } }
        val replyTarget = messages.firstOrNull { it.id == replyId }
        if (deleting && actionMessage != null) {
            val caps = capabilities
            if (caps != null) DeleteConfirmPanel(caps, deleteEveryone, deleteBusy, deleteError, onChoose = { deleteEveryone = it },
                onCancel = { deleting = false; deleteError = null }, onConfirm = { scope.launch {
                    deleteBusy = true; deleteError = null
                    try {
                        val result = conversation.deleteMessage(actionMessage.chatId, actionMessage.id, if (deleteEveryone) DeleteMessageScope.ForEveryone else DeleteMessageScope.ForSelf)
                        if (result.isSuccess) closeActions() else deleteError = result.exceptionOrNull()?.message
                    } finally { deleteBusy = false }
                } })
        } else if (editMessage != null) {
            EditBanner(editMessage.text.ifBlank { editMessage.summary() }, editMessage.media != null, editText.codePointCount(0, editText.length), editLimit, editError, onClose = ::closeEdit)
        } else if (!searching) {
            AttachmentPanel(attachments, draft, limits.caption)
            if (replyId != null) Row(Modifier.fillMaxWidth().background(palette.surface).padding(start = 18.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Ответ · " + (replyTarget?.senderName?.ifBlank { if (replyTarget.isOutgoing) "Вы" else chat.title } ?: "Сообщение"), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(replyTarget?.summary() ?: "Сообщение недоступно", color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                }
                IconButton(enabled = !sending, onClick = { replyId = null }) { Icon(Icons.Rounded.Close, "Отменить ответ", tint = palette.textSecondary) }
            }
        }
        val editing = editMessage != null
        val fieldText = if (editing) editText else draft
        val fieldLimit = when { editing -> editLimit; attachments.active -> limits.caption; else -> limits.text }
        val overLimit = fieldText.codePointCount(0, fieldText.length) > fieldLimit
        val busy = sending || editBusy || attachments.sending
        val canSend = !busy && !overLimit && when {
            editing -> fieldText != editMessage?.text && (editMessage?.media != null || fieldText.isNotBlank())
            attachments.active -> attachments.pending.isNotEmpty() && !attachments.importing
            else -> fieldText.isNotBlank()
        }
        val showRecord = videoNote.active || (!editing && !attachments.active && fieldText.isBlank())
        if (!deleting && !searching && typingNames.isNotEmpty()) TypingRow(typingNames)
        if (!deleting && !searching) Row(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            if (!videoNote.active) {
                if (!editing) AttachmentButton(attachments, enabled = !sending)
                else Spacer(Modifier.width(8.dp))
            }
            if (videoNote.active) VideoNoteRecordingHint(Modifier.weight(1f))
            else TextField(fieldText, { if (editing) editText = it else draft = it }, enabled = !busy, modifier = Modifier.weight(1f).defaultMinSize(minHeight = 40.dp),
                placeholder = { Text(if (attachments.active) "Подпись" else "Сообщение", color = palette.textSecondary) }, maxLines = 5,
                shape = RoundedCornerShape(20.dp), colors = beyGramFilledTextFieldColors(), isError = overLimit)
            Spacer(Modifier.width(8.dp))
            if (showRecord) VideoNoteRecordButton(videoNote, enabled = !sending && !busy, onPress = ::startVideoNote)
            else Box(Modifier.size(40.dp).clip(CircleShape).background(palette.accent.copy(alpha = if (canSend || busy) 1f else 0.4f))
                .clickable(enabled = canSend) {
                    when {
                        editing -> scope.launch {
                            val target = editMessage ?: return@launch
                            editBusy = true; editError = null
                            try {
                                val result = conversation.editMessage(target.chatId, target.id, editText)
                                if (result.isSuccess) closeEdit() else editError = result.exceptionOrNull()?.message
                            } finally { editBusy = false }
                        }
                        attachments.active -> attachments.send(draft) { draft = it }
                        else -> scope.launch {
                            sending = true; error = null
                            try {
                                val result = conversation.sendMessage(chat.id, draft, replyId)
                                if (result.isSuccess) { draft = ""; replyId = null } else error = result.exceptionOrNull()?.message
                            } finally { sending = false }
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = palette.onAccent, strokeWidth = 2.dp)
                else Icon(if (editing) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send, if (editing) "Сохранить" else "Отправить",
                    tint = palette.onAccent.copy(alpha = if (canSend) 1f else 0.8f), modifier = Modifier.size(22.dp))
            }
        }
    }
    messages.firstOrNull { it.id == selectedMediaId }?.let { viewed -> viewed.media?.let { media -> MediaViewer(media, observeFile, onDownloadFile,
        onViewed = { selectedMediaId?.let { conversation.openMessageContent(chat.id, it) } }, onClose = { selectedMediaId = null },
        title = if (viewed.isOutgoing) "Вы" else viewed.senderName.ifBlank { chat.title }, subtitle = viewed.time,
        canExport = viewerCapabilities?.canSave == true,
        actions = MediaViewerActions(
            canForward = viewerCapabilities?.canForward == true,
            canEdit = viewerCapabilities?.canEdit == true,
            canDelete = viewerCapabilities?.let { it.canDeleteForSelf || it.canDeleteForAll } == true,
            onForward = { forwardId = viewed.id; selectedMediaId = null },
            onEdit = { editId = viewed.id; editText = viewed.text; selectedMediaId = null },
            onDelete = { deleteEveryone = viewerCapabilities?.canDeleteForSelf == false; actionsId = viewed.id; deleting = true; selectedMediaId = null }
        )) } }
    forwardId?.let { id ->
        ForwardChatDialog(chats, forwardBusy, forwardError,
            onPick = { target -> scope.launch {
                forwardBusy = true; forwardError = null
                try {
                    val result = conversation.forwardMessage(chat.id, id, target.id)
                    if (result.isSuccess) { forwardId = null; Toast.makeText(context, "Сообщение переслано", Toast.LENGTH_SHORT).show() }
                    else forwardError = result.exceptionOrNull()?.message ?: "Не удалось переслать сообщение"
                } finally { forwardBusy = false }
            } },
            onClose = { forwardId = null; forwardError = null })
    }
    contextMessageId?.let { id -> MessageContextDialog(contextChatId, id, conversation, observeFile, onDownloadFile, onClose = { contextMessageId = null }) }
}

@Composable
private fun ChatTopBar(chat: ChatPreview, presence: String, onBack: () -> Unit, onInfo: () -> Unit, onSearch: () -> Unit, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = palette.textPrimary) }
            TelegramAvatar(chat.initials, Color(chat.avatarColorArgb), 40.dp, chat.photo, observeFile, downloadFile, Modifier.clickable(onClick = onInfo))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onInfo)) {
                Text(chat.title, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(presence, color = palette.textSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Поиск в чате", tint = palette.textSecondary) }
        }
        HorizontalDivider(color = palette.divider)
    }
}

@Composable
internal fun MessageBubble(message: ChatMessage, observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    isRead: Boolean = false, highlighted: Boolean = false, onOpen: () -> Unit, onActions: (() -> Unit)? = null, onReplyClick: (MessageReply) -> Unit = {}, onPlayed: () -> Unit = {}) {
    val palette = beyGramPalette()
    val shape = if (message.isOutgoing) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val hasText = message.text.isNotBlank()
    val circle = message.media?.kind == MediaKind.VideoNote
    // Кружок показывает время и статус поверх себя; при ошибке отправки статус остаётся под пузырём.
    val inCircle = circle && message.sendState != MessageSendState.Failed
    val statusIcon = when {
        !message.isOutgoing -> null
        message.sendState == MessageSendState.Sending -> Icons.Rounded.Schedule
        message.sendState == MessageSendState.Sent -> if (isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done
        else -> null
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        // Голосовое и кружок управляются своими элементами в пузыре и не открывают просмотрщик.
        Column(Modifier.widthIn(max = 292.dp).clip(shape)
            .background(if (circle) Color.Transparent else if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)
            .then(if (highlighted) Modifier.border(2.dp, palette.accent, if (circle) CircleShape else shape) else Modifier)
            .combinedClickable(onClick = { if (message.media != null && !message.media.kind.playsInline()) onOpen() }, onLongClick = onActions)) {
            // Media sits flush against the bubble's top and side edges; text keeps its own padding.
            message.media?.let { media ->
                if (media.kind == MediaKind.VideoNote) VideoNoteBubble(media, observeFile, onDownloadFile, onPlayed, onActions,
                    time = if (inCircle) message.time else "", statusIcon = if (inCircle) statusIcon else null, unseen = !message.isOutgoing && !media.isViewed)
                else MediaPreview(media, observeFile, onDownloadFile, onOpen, onActions, onPlayed)
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = if (circle) 4.dp else 8.dp, bottom = if (circle) 0.dp else 6.dp)) {
                if (!message.isOutgoing && message.senderName.isNotBlank()) Text(message.senderName, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                message.reply?.let { reply ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.accent, RoundedCornerShape(12.dp))
                        .clickable { onReplyClick(reply) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(reply.senderName.ifBlank { "Ответ на сообщение" }, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(reply.text, color = palette.textSecondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (hasText) Text(message.text, color = palette.textPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                if (!inCircle) Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    val failed = message.sendState == MessageSendState.Failed
                    val status = if (message.isOutgoing && message.sendState == MessageSendState.Failed) " · не отправлено" else ""
                    Text((if (message.isEdited) "изменено · " else "") + message.time + status,
                        color = if (failed) palette.warningText else palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (message.isOutgoing && message.sendState == MessageSendState.Sending) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Rounded.Schedule, "Отправляется", tint = palette.textSecondary, modifier = Modifier.size(14.dp))
                    }
                    if (message.isOutgoing && message.sendState == MessageSendState.Sent) {
                        Spacer(Modifier.width(3.dp))
                        Icon(if (isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done, if (isRead) "Прочитано" else "Отправлено",
                            tint = palette.accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
