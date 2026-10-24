package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.MessageThread
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.mergeThreadMessages
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A channel post's discussion thread. The channel post stays pinned above the comments, so paging and jumping can
 * never lose it; only the discussion chat is read here, the channel history behind the overlay stays untouched.
 */
@Composable
internal fun MessageThreadScreen(
    channelChatId: Long,
    channelTitle: String,
    postId: Long,
    initialPost: ChatMessage?,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    initialDraftText: String = "",
    initialReplyToId: Long = 0L,
    onDraftChange: (String, Long) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var post by remember(channelChatId, postId) { mutableStateOf(initialPost) }
    var thread by remember(channelChatId, postId) { mutableStateOf<MessageThread?>(null) }
    var messages by remember(channelChatId, postId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember(channelChatId, postId) { mutableStateOf(true) }
    var errorText by remember(channelChatId, postId) { mutableStateOf<String?>(null) }
    var pagingError by remember(channelChatId, postId) { mutableStateOf<String?>(null) }
    var loadingOlder by remember(channelChatId, postId) { mutableStateOf(false) }
    var hasOlder by remember(channelChatId, postId) { mutableStateOf(false) }
    var text by rememberSaveable(channelChatId, postId) { mutableStateOf(initialDraftText) }
    var entities by remember(channelChatId, postId) { mutableStateOf<List<MessageTextEntity>>(emptyList()) }
    var replyTarget by remember(channelChatId, postId) { mutableStateOf<ChatMessage?>(null) }
    var replyTargetId by rememberSaveable(channelChatId, postId) { mutableLongStateOf(initialReplyToId) }
    var focusRequest by remember(channelChatId, postId) { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var reactionTarget by remember { mutableStateOf<Pair<Long, ChatMessage>?>(null) }
    var reactionOptions by remember { mutableStateOf<List<ReactionOption>>(emptyList()) }
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(initialPost) {
        if (initialPost != null) post = initialPost
    }
    // A restored route may have no post in the channel window yet: fetch it without touching the channel's scroll.
    LaunchedEffect(channelChatId, postId) {
        if (post != null) return@LaunchedEffect
        conversation.messageContext(channelChatId, postId)
            .onSuccess { context -> post = context.messages.firstOrNull { it.id == postId } }
    }
    LaunchedEffect(channelChatId, postId, conversation) {
        loading = true
        errorText = null
        conversation.loadMessageThread(channelChatId, postId)
            .onSuccess { loaded ->
                thread = loaded
                messages = loaded.messages.filterNot { it.isChannelPost }.distinctBy { it.id }.sortedBy { it.id }
                hasOlder = loaded.hasOlderMessages && messages.isNotEmpty()
                loading = false
                if (messages.isNotEmpty()) scope.launch { listState.scrollToItem(0) }
            }
            .onFailure { failure ->
                loading = false
                errorText = failure.userMessage(context, R.string.thread_comments_unavailable)
            }
    }
    // The discussion supergroup is a real chat: opening it lets TDLib route new comments, delivery updates and
    // deletions through the shared history store. Only messages from this thread are merged into the local snapshot.
    LaunchedEffect(thread?.discussionChatId, thread?.threadId, conversation) {
        val chatId = thread?.discussionChatId ?: 0L
        val currentThreadId = thread?.threadId ?: 0L
        if (chatId == 0L || currentThreadId == 0L) return@LaunchedEffect
        conversation.openChat(chatId)
        try {
            coroutineScope {
                launch {
                    conversation.observeChatHistory(chatId).collect { history ->
                        val fresh = history.messages.filter {
                            it.messageThreadId == currentThreadId && !it.isChannelPost
                        }
                        if (fresh.isNotEmpty()) {
                            messages = mergeThreadMessages(messages, fresh)
                        }
                    }
                }
                // Read receipts go through the existing privacy-aware gateway and only for genuinely visible rows.
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } }
                    .distinctUntilChanged()
                    .collect { visibleIds ->
                        val sent = visibleIds.filter { id ->
                            messages.any { it.id == id && it.sendState == MessageSendState.Sent }
                        }
                        if (sent.isNotEmpty()) conversation.messagesVisible(chatId, sent)
                    }
            }
        } finally {
            conversation.closeChat(chatId)
        }
    }
    // A restored draft can name a reply target before that page has loaded; attach it as soon as it appears.
    LaunchedEffect(replyTargetId, messages) {
        if (replyTargetId > 0L && replyTarget == null) {
            replyTarget = messages.firstOrNull { it.id == replyTargetId }
        }
    }
    LaunchedEffect(reactionTarget) {
        val target = reactionTarget ?: return@LaunchedEffect
        reactionOptions = conversation.availableReactions(target.first, target.second.id).getOrDefault(emptyList())
    }

    fun changeReaction(chatId: Long, message: ChatMessage, option: ReactionOption, addToRecent: Boolean) {
        scope.launch {
            conversation.toggleReaction(chatId, message.id, option, addToRecent)
                .onSuccess { reactions ->
                    if (message.id == post?.id && chatId == channelChatId) {
                        post = post?.copy(reactions = reactions)
                    } else {
                        messages = messages.map { if (it.id == message.id && it.chatId == chatId) it.copy(reactions = reactions) else it }
                    }
                }
                .onFailure { sendError = it.userMessage(context, R.string.chat_reaction_failed) }
        }
    }

    fun loadOlder() {
        val current = thread ?: return
        if (loadingOlder || !hasOlder || messages.isEmpty()) return
        loadingOlder = true
        pagingError = null
        scope.launch {
            try {
                conversation.loadMessageThreadComments(current.discussionChatId, current.threadId, messages.first().id)
                    .onSuccess { page ->
                        if (page.messages.isEmpty()) {
                            hasOlder = false
                        } else {
                            messages = mergeThreadMessages(messages, page.messages)
                            hasOlder = page.hasMore
                        }
                    }
                    .onFailure { pagingError = it.userMessage(context, R.string.thread_comments_load_failed) }
            } finally {
                loadingOlder = false
            }
        }
    }

    fun jump(reply: MessageReply) {
        if (reply.isUnavailable || reply.messageId <= 0L) {
            sendError = context.getString(R.string.chat_reply_original_unavailable)
            return
        }
        // The channel post is permanently pinned above the comments, so replying to it needs no list jump.
        if (reply.chatId == channelChatId && reply.messageId == postId) return
        val index = messages.indexOfFirst { it.id == reply.messageId && it.chatId == reply.chatId }
        if (index < 0) {
            sendError = context.getString(R.string.thread_reply_not_loaded)
            return
        }
        scope.launch { listState.animateScrollToItem(messages.lastIndex - index) }
    }

    fun send() {
        val current = thread ?: return
        if (!current.canReply || sending || text.isBlank()) return
        val outgoingText = text
        val outgoingEntities = entities
        val replyTo = replyTarget?.id ?: replyTargetId.takeIf { it > 0L }
        sending = true
        sendError = null
        scope.launch {
            try {
                conversation.sendThreadReply(current.discussionChatId, current.threadId, outgoingText, outgoingEntities, replyTo)
                    .onSuccess { sent ->
                        messages = mergeThreadMessages(messages, listOf(sent))
                        thread = current.copy(replyCount = current.replyCount + 1)
                        text = ""
                        entities = emptyList()
                        replyTarget = null
                        replyTargetId = 0L
                        onDraftChange("", 0L)
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    .onFailure { sendError = it.userMessage(context, R.string.thread_comments_send_failed) }
            } finally {
                sending = false
            }
        }
    }

    val commentsCount = thread?.replyCount?.takeIf { it > 0 } ?: post?.replyCount ?: 0
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.thread_comments_title, commentsCount),
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    thread?.discussionTitle?.ifBlank { channelTitle } ?: channelTitle,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)

        post?.let { root ->
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                MessageBubble(
                    message = root,
                    observeFile = observeFile,
                    onDownloadFile = downloadFile,
                    onOpen = { selectedMediaId = it },
                    onActions = { reactionTarget = channelChatId to root },
                    onReplyClick = ::jump,
                    onReaction = { _, option -> changeReaction(channelChatId, root, option, addToRecent = false) },
                    observeCustomEmoji = conversation::observeCustomEmoji,
                    onRetry = {}
                )
            }
            HorizontalDivider(color = palette.divider)
        }

        if (errorText != null && thread == null) {
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    errorText.orEmpty(),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            }
        } else Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(messages.asReversed(), key = { it.id }) { message ->
                    ThreadCommentRow(
                        message = message,
                        observeFile = observeFile,
                        onDownloadFile = downloadFile,
                        onOpenProfile = onOpenProfile,
                        onOpenMedia = { selectedMediaId = message.id },
                        onActions = { reactionTarget = message.chatId to message },
                        onReplyClick = ::jump,
                        onReply = if (thread?.canReply == true) {
                            { target: Long ->
                                replyTarget = messages.firstOrNull { it.id == target }
                                replyTargetId = target
                                onDraftChange(text, target)
                                focusRequest++
                            }
                        } else null,
                        onReaction = { option -> changeReaction(message.chatId, message, option, addToRecent = false) },
                        observeCustomEmoji = conversation::observeCustomEmoji
                    )
                }
                if (loadingOlder) item(key = "older_loading") {
                    Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                } else if (hasOlder || pagingError != null) item(key = "older_more") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        pagingError?.let { message ->
                            Text(message, color = palette.warningText, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
                        }
                        TextButton(onClick = ::loadOlder) {
                            Text(stringResource(if (pagingError == null) R.string.thread_comments_load_earlier else R.string.common_retry))
                        }
                    }
                }
                if (thread != null && messages.isEmpty() && !loading) item(key = "thread_empty") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.thread_comments_empty), color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        reactionTarget?.let { (chatId, message) ->
            MessageReactionPicker(
                options = reactionOptions,
                chosen = message.reactions.filter { it.isChosen }.mapTo(hashSetOf()) { it.option },
                observeCustomEmoji = conversation::observeCustomEmoji,
                observeFile = observeFile,
                downloadFile = downloadFile,
                onPick = { option ->
                    changeReaction(chatId, message, option, addToRecent = true)
                    reactionTarget = null
                }
            )
        }
        sendError?.let { message ->
            Row(Modifier.fillMaxWidth().background(palette.warningSurface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = palette.warningText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { sendError = null }) { Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = palette.warningText) }
            }
        }
        val currentThread = thread
        if (currentThread != null) {
            if (currentThread.canReply) {
                Column(Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().imePadding()) {
                    replyTarget?.let { target ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(palette.surface2)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.chat_reply_label) + " · " + target.senderName.ifBlank { stringResource(R.string.chat_message) },
                                    color = palette.accentInk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(target.summary(), color = palette.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { replyTarget = null; replyTargetId = 0L; onDraftChange(text, 0L) }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.chat_cancel_reply_description), tint = palette.textSecondary)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                        FormattingTextField(
                            value = text,
                            entities = entities,
                            onChange = { text = it; onDraftChange(it, replyTarget?.id ?: replyTargetId) },
                            onEntitiesChange = { entities = it },
                            placeholder = stringResource(R.string.thread_reply_placeholder),
                            locked = sending,
                            isError = false,
                            modifier = Modifier.weight(1f),
                            focusRequest = focusRequest
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(palette.accent.copy(alpha = if (text.isNotBlank() && !sending) 1f else 0.4f))
                                .clickable(enabled = text.isNotBlank() && !sending) { send() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = palette.onAccent, strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.common_send), tint = palette.onAccent, modifier = Modifier.size(21.dp))
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().background(palette.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.thread_read_only), color = palette.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }

    val mediaMessages = (messages + listOfNotNull(post)).filter { it.media != null }
    val viewed = mediaMessages.firstOrNull { it.id == selectedMediaId }
    if (viewed?.media != null) {
        MediaViewer(
            pages = mediaMessages.mapNotNull { message -> message.media?.let { MediaViewerItem(message.id, it) } },
            selectedId = viewed.id,
            onSelect = { selectedMediaId = it },
            observeFile = observeFile,
            downloadFile = downloadFile,
            onViewed = { id -> conversation.openMessageContent(viewed.chatId, id) },
            onClose = { selectedMediaId = null },
            title = viewed.senderName.ifBlank { channelTitle },
            subtitle = viewed.time
        )
    }
}

@Composable
private fun ThreadCommentRow(
    message: ChatMessage,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenMedia: () -> Unit,
    onActions: () -> Unit,
    onReplyClick: (MessageReply) -> Unit,
    onReply: ((Long) -> Unit)?,
    onReaction: (ReactionOption) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>
) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), verticalAlignment = Alignment.Top) {
        if (message.senderId > 0L) {
            Box(
                Modifier.padding(top = 10.dp).size(28.dp).clip(CircleShape)
                    .background(palette.surface2)
                    .clickable { onOpenProfile(message.senderId) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    message.senderName.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.weight(1f)) {
            val openSender: (() -> Unit)? = if (message.senderId > 0L) {
                { onOpenProfile(message.senderId) }
            } else null
            MessageBubble(
                message = message,
                observeFile = observeFile,
                onDownloadFile = onDownloadFile,
                onOpen = { onOpenMedia() },
                onActions = onActions,
                onReplyClick = onReplyClick,
                onReply = onReply,
                onReaction = { _, option -> onReaction(option) },
                observeCustomEmoji = observeCustomEmoji,
                onRetry = {},
                onSenderClick = openSender
            )
        }
    }
}
