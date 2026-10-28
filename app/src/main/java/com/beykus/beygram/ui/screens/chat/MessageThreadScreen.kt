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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.AutoDownloadRequest
import com.beykus.beygram.domain.model.CustomEmoji
import com.beykus.beygram.domain.model.InteractionEffect
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageReply
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.MessageThread
import com.beykus.beygram.domain.model.PollVotersPage
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.mergeThreadHistory
import com.beykus.beygram.domain.model.mergeThreadMessages
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.NowPlayingBar
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A channel post's discussion thread, shown as one list: the channel post once at the top, the comments below it,
 * as in Telegram. Only the discussion chat is read here; the channel history behind the overlay stays untouched.
 * The discussion group's own copy of the post (the thread root) is never shown as a comment.
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
    initialDraftEntities: List<MessageTextEntity> = emptyList(),
    initialReplyToId: Long = 0L,
    onDraftChange: (String, List<MessageTextEntity>, Long) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    /** Repository-backed automatic download for the shared media viewer. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null
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
    var entities by rememberSaveable(channelChatId, postId) { mutableStateOf(initialDraftEntities) }
    var replyTarget by remember(channelChatId, postId) { mutableStateOf<ChatMessage?>(null) }
    var replyTargetId by rememberSaveable(channelChatId, postId) { mutableLongStateOf(initialReplyToId) }
    var focusRequest by remember(channelChatId, postId) { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var reactionTarget by remember { mutableStateOf<Pair<Long, ChatMessage>?>(null) }
    var reactionOptions by remember(conversation) { mutableStateOf<List<ReactionOption>>(emptyList()) }
    // A Stars reaction or a paid media payment on the post leaves the post copy stale, so the post is reloaded after it.
    fun reloadPost() {
        scope.launch { conversation.messageContext(channelChatId, postId).onSuccess { context -> post = context.messages.firstOrNull { it.id == postId } } }
    }
    val paidReactions = rememberPaidReactions(conversation) { reloadPost() }
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }
    var selectedMediaChatId by remember { mutableStateOf(0L) }
    var stickerSetId by remember { mutableStateOf<Long?>(null) }
    // A view-once message is deleted as soon as it is opened; keep its page for this viewer session.
    var retainedViewOnce by remember(channelChatId, postId) { mutableStateOf<ChatMessage?>(null) }
    var openedViewOnceIds by remember(channelChatId, postId) { mutableStateOf(emptySet<Long>()) }
    val viewOnceGate = rememberViewOnceStealthGate(channelChatId to postId)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val messageLimits by conversation.messageLimits.collectAsStateWithLifecycle()

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
                messages = loaded.messages.filterNot { it.isChannelPost || it.id == loaded.threadId }.distinctBy { it.id }.sortedBy { it.id }
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
                        // A temporary comment that TDLib confirmed is replaced by its server copy, never shown beside it.
                        val merged = mergeThreadHistory(messages, history, chatId, currentThreadId)
                        if (merged != messages) messages = merged
                    }
                }
                launch {
                    conversation.observeDeletedMessages(chatId).collect { deleted ->
                        if (deleted.isNotEmpty()) {
                            messages = messages.filterNot { it.chatId == chatId && it.id in deleted }
                        }
                    }
                }
                // Read receipts go through the existing privacy-aware gateway and only for genuinely visible rows.
                // Restarting on RESUMED re-reports the same viewport after the app returns from background.
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } }
                        .distinctUntilChanged()
                        .collect { visibleIds ->
                            val sent = visibleIds.filter { id ->
                                messages.any { it.id == id && it.sendState == MessageSendState.Sent }
                            }
                            if (sent.isNotEmpty()) conversation.messagesVisible(chatId, sent)
                        }
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
    LaunchedEffect(conversation, reactionTarget) {
        val target = reactionTarget ?: return@LaunchedEffect
        reactionOptions = conversation.availableReactions(target.first, target.second.id).getOrDefault(emptyList())
    }

    fun changeReaction(chatId: Long, message: ChatMessage, option: ReactionOption, addToRecent: Boolean) {
        if (option.isPaid) {
            paidReactions.open(chatId, message.id)
            return
        }
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
        // The channel post is the topmost row of the reversed list.
        val isPost = (reply.chatId == channelChatId && reply.messageId == postId) ||
            (reply.chatId == thread?.discussionChatId && reply.messageId == thread?.threadId)
        if (isPost) {
            if (post != null) scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
            return
        }
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
        if (text.codePointCount(0, text.length) > messageLimits.text) {
            sendError = context.getString(R.string.chat_truncated_notice, messageLimits.text)
            return
        }
        val outgoingText = text
        val outgoingEntities = entities
        val replyTo = replyTarget?.id ?: replyTargetId.takeIf { it > 0L }
        sending = true
        sendError = null
        scope.launch {
            try {
                conversation.sendThreadReply(current.discussionChatId, current.threadId, outgoingText, outgoingEntities, replyTo)
                    .onSuccess { sent ->
                        // TDLib may already have confirmed this comment, so the snapshot must not bring its temporary copy back.
                        val history = conversation.observeChatHistory(current.discussionChatId).value
                        messages = mergeThreadHistory(mergeThreadMessages(messages, listOf(sent)), history, current.discussionChatId, current.threadId)
                        thread = current.copy(replyCount = current.replyCount + 1)
                        text = ""
                        entities = emptyList()
                        replyTarget = null
                        replyTargetId = 0L
                        onDraftChange("", emptyList(), 0L)
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    .onFailure { sendError = it.userMessage(context, R.string.thread_comments_send_failed) }
            } finally {
                sending = false
            }
        }
    }

    val commentsCount = thread?.replyCount?.takeIf { it > 0 } ?: post?.replyCount ?: 0
    Box(
        Modifier.fillMaxSize().background(palette.screen).pointerInput(Unit) {
            // The channel remains composed underneath for scroll restoration; consume what the thread does not handle.
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
    ) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                        onOpenMedia = { selectedMediaId = message.id; selectedMediaChatId = message.chatId; if (message.isViewOnce) retainedViewOnce = message },
                        onPlayed = { conversation.openMessageContent(message.chatId, message.id) },
                        onRetry = {
                            scope.launch {
                                conversation.retryMessages(message.chatId, listOf(message.id))
                                    .onFailure { sendError = it.userMessage(context, R.string.chat_retry_send_failed) }
                            }
                        },
                        onActions = { reactionTarget = message.chatId to message },
                        onReplyClick = ::jump,
                        onReply = if (thread?.canReply == true) {
                            { target: Long ->
                                replyTarget = messages.firstOrNull { it.id == target }
                                replyTargetId = target
                                onDraftChange(text, entities, target)
                                focusRequest++
                            }
                        } else null,
                        onReaction = { option -> changeReaction(message.chatId, message, option, addToRecent = false) },
                        onStickerOpen = { id -> stickerSetId = id },
                        observeCustomEmoji = conversation::observeCustomEmoji,
                        onPollVote = { chatId, messageId, choices ->
                            scope.launch {
                                conversation.votePoll(chatId, messageId, choices)
                                    .onFailure { sendError = it.userMessage(context, R.string.chat_poll_vote_failed) }
                            }
                        },
                        onPollStop = { chatId, messageId ->
                            scope.launch {
                                conversation.stopPoll(chatId, messageId)
                                    .onFailure { sendError = it.userMessage(context, R.string.chat_poll_stop_failed) }
                            }
                        },
                        onLoadPollVoters = { chatId, messageId, optionIndex, offset ->
                            conversation.pollVoters(chatId, messageId, optionIndex, offset)
                        },
                        onAddPollOption = { chatId, messageId, text ->
                            conversation.addPollOption(chatId, messageId, text)
                        },
                        paidContent = rememberPaidContentActions(conversation),
                        onOpenMessageEntity = onOpenMessageEntity
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
                // Reverse layout: the last item is drawn on top, so the post opens the discussion exactly once.
                post?.let { root ->
                    item(key = "thread_post") {
                        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            MessageBubble(
                                message = root,
                                observeFile = observeFile,
                                onDownloadFile = downloadFile,
                                onOpen = { selectedMediaId = root.id; selectedMediaChatId = channelChatId; if (root.isViewOnce) retainedViewOnce = root },
                                onStickerOpen = { media -> stickerSetId = media.stickerSetId },
                                onPlayed = { conversation.openMessageContent(channelChatId, root.id) },
                                onActions = { reactionTarget = channelChatId to root },
                                onReplyClick = ::jump,
                                onReaction = { _, option -> changeReaction(channelChatId, root, option, addToRecent = false) },
                                observeCustomEmoji = conversation::observeCustomEmoji,
                                onPollVote = { messageId, choices ->
                                    scope.launch {
                                        conversation.votePoll(channelChatId, messageId, choices)
                                            .onFailure { sendError = it.userMessage(context, R.string.chat_poll_vote_failed) }
                                    }
                                },
                                onPollStop = { messageId ->
                                    scope.launch {
                                        conversation.stopPoll(channelChatId, messageId)
                                            .onFailure { sendError = it.userMessage(context, R.string.chat_poll_stop_failed) }
                                    }
                                },
                                onLoadPollVoters = { messageId, optionIndex, offset ->
                                    conversation.pollVoters(channelChatId, messageId, optionIndex, offset)
                                },
                                onAddPollOption = { messageId, text ->
                                    conversation.addPollOption(channelChatId, messageId, text)
                                },
                                onRetry = {},
                                paidContent = rememberPaidContentActions(conversation) { reloadPost() },
                                onOpenMessageEntity = onOpenMessageEntity
                            )
                        }
                    }
                }
            }
        }

        reactionTarget?.let { (chatId, message) ->
            MessageReactionPicker(
                options = reactionOptions,
                chosen = message.reactions.filter { it.isChosen && !it.isPaid }.mapTo(hashSetOf()) { it.option },
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
        NowPlayingBar(bottomInset = currentThread?.canReply != true)
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
                            IconButton(onClick = { replyTarget = null; replyTargetId = 0L; onDraftChange(text, entities, 0L) }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.chat_cancel_reply_description), tint = palette.textSecondary)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                        FormattingTextField(
                            value = text,
                            entities = entities,
                            onChange = { text = it; onDraftChange(it, entities, replyTarget?.id ?: replyTargetId) },
                            onEntitiesChange = { entities = it; onDraftChange(text, it, replyTarget?.id ?: replyTargetId) },
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
    }

    // Message ids are chat-scoped: keep the owning chat so a post and a comment with the same id never collide.
    val mediaMessages = when (selectedMediaChatId) {
        channelChatId -> listOfNotNull(post)
        else -> messages.filter { it.chatId == selectedMediaChatId }
    }.filter { it.media != null }
    val liveViewed = mediaMessages.firstOrNull { it.id == selectedMediaId }
    val retainedViewed = retainedViewOnce?.takeIf { it.id == selectedMediaId }
    val viewed = liveViewed ?: retainedViewed
    LaunchedEffect(selectedMediaId, liveViewed?.media?.fileId, liveViewed?.isViewOnce) {
        if (selectedMediaId == null) {
            retainedViewOnce = null
            return@LaunchedEffect
        }
        if (liveViewed?.media != null && liveViewed.isViewOnce) retainedViewOnce = liveViewed
    }
    var viewerCanSave by remember(selectedMediaId) { mutableStateOf(false) }
    LaunchedEffect(selectedMediaId, viewed?.chatId) {
        viewerCanSave = if (selectedMediaId != null && viewed != null) {
            conversation.messageCapabilities(viewed.chatId, viewed.id).getOrNull()?.canSave == true
        } else false
    }
    val viewOnceAsked = viewed?.takeIf { viewOnceGate.needsConsent(it.chatId, it.id, it.isViewOnce, conversation.interactionAllowed(InteractionEffect.OpenContent, it.chatId)) }
    if (viewOnceAsked != null) viewOnceGate.ConsentDialog(viewOnceAsked.chatId, viewOnceAsked.id) { selectedMediaId = null; selectedMediaChatId = 0L }
    if (viewed != null) {
        val viewedMedia = viewed.media
        if (viewedMedia != null && viewOnceAsked == null) {
            val viewerPages = MediaViewerPolicy.retainViewOncePage(
                pages = mediaMessages.mapNotNull { message -> message.media?.let { MediaViewerItem(message.id, it, message.isViewOnce, viewed.chatId) } },
                retained = MediaViewerItem(viewed.id, viewedMedia, viewed.isViewOnce, viewed.chatId)
            )
            MediaViewer(
                pages = viewerPages,
                selectedId = viewed.id,
                onSelect = { selectedMediaId = it },
                observeFile = observeFile,
                downloadFile = downloadFile,
                autoDownloadFile = autoDownloadFile,
                onViewed = { id ->
                    val once = (mediaMessages.firstOrNull { it.id == id } ?: retainedViewOnce)?.isViewOnce == true
                    if (!once || id !in openedViewOnceIds) {
                        if (once) openedViewOnceIds = openedViewOnceIds + id
                        viewOnceGate.openContent(conversation, viewed.chatId, id)
                    }
                },
                onClose = { selectedMediaId = null; selectedMediaChatId = 0L },
                title = viewed.senderName.ifBlank { channelTitle },
                subtitle = viewed.time,
                canExport = viewerCanSave && !viewed.isViewOnce,
                confirmExport = { id ->
                    conversation.messageCapabilities(viewed.chatId, id).getOrNull()?.canSave == true
                }
            )
        }
    }
    StickerSetBrowser(
        setId = stickerSetId,
        conversation = conversation,
        observeFile = observeFile,
        downloadFile = downloadFile,
        onDismiss = { stickerSetId = null }
    )
}

@Composable
private fun ThreadCommentRow(
    message: ChatMessage,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenMedia: () -> Unit,
    onPlayed: () -> Unit,
    onRetry: () -> Unit,
    onActions: () -> Unit,
    onReplyClick: (MessageReply) -> Unit,
    onReply: ((Long) -> Unit)?,
    onReaction: (ReactionOption) -> Unit,
    onPollVote: (chatId: Long, messageId: Long, optionIndices: List<Int>) -> Unit,
    onPollStop: (chatId: Long, messageId: Long) -> Unit,
    onLoadPollVoters: suspend (chatId: Long, messageId: Long, optionIndex: Int, offset: Int) -> Result<PollVotersPage>,
    onAddPollOption: suspend (chatId: Long, messageId: Long, text: String) -> Result<Unit>,
    onStickerOpen: (Long) -> Unit,
    observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    paidContent: PaidContentActions? = null
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
                onStickerOpen = { media -> onStickerOpen(media.stickerSetId) },
                onPlayed = onPlayed,
                onActions = onActions,
                onReplyClick = onReplyClick,
                onReply = onReply,
                onReaction = { _, option -> onReaction(option) },
                observeCustomEmoji = observeCustomEmoji,
                onRetry = onRetry,
                onSenderClick = openSender,
                onPollVote = { messageId, choices -> onPollVote(message.chatId, messageId, choices) },
                onPollStop = { messageId -> onPollStop(message.chatId, messageId) },
                onLoadPollVoters = { messageId, optionIndex, offset ->
                    onLoadPollVoters(message.chatId, messageId, optionIndex, offset)
                },
                onAddPollOption = { messageId, text -> onAddPollOption(message.chatId, messageId, text) },
                paidContent = paidContent,
                onOpenMessageEntity = onOpenMessageEntity
            )
        }
    }
}
