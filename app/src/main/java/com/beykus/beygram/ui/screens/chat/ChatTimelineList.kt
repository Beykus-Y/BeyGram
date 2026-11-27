package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackQueueSource
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
internal fun ChatTimelineList(
    listState: LazyListState,
    metrics: BubbleMetrics,
    chromeTopPad: Dp,
    chromeBottomPad: Dp,
    messageListItems: List<ChatMessageListItem>,
    daySeparators: Map<String, LocalDate>,
    feedTopicHeaders: Map<String, Long>,
    forumTopicsById: Map<Long, ForumTopic>,
    onOpenForumTopic: ((ForumTopic) -> Unit)?,
    history: ChatHistory,
    messages: List<ChatMessage>,
    chat: ChatPreview,
    accountId: String,
    forumTopicId: Long,
    waveChat: Boolean,
    linkedDiscussionChatId: Long,
    conversation: ConversationRepository,
    context: Context,
    scope: CoroutineScope,
    connectionState: TelegramConnectionState,
    messageAnimations: MessageAnimationConfig,
    animationTracker: MessageArrivalTracker,
    effectLimiter: MessageEffectPlaybackLimiter,
    mediaLaunchController: ComposerMediaLaunchController,
    launchTargets: Map<Long, ComposerLaunch>,
    bubbleCoordinates: MutableMap<Long, LayoutCoordinates>,
    mediaCoordinates: MutableMap<Long, LayoutCoordinates>,
    showGroupAvatars: Boolean,
    supportsGroupAvatars: Boolean,
    groupAvatars: Map<String, ChatMessage>,
    groupMembers: Map<Long, ProfileMember>,
    groupAvatarPhotos: Map<Long, AvatarPhoto?>,
    senderHeaderByKey: Map<String, Boolean>,
    localTextAttemptIds: Map<Long, TextSendAttempt>,
    highlighted: Long?,
    actionsId: Long?,
    contextSceneMessageId: Long?,
    selectionMode: Boolean,
    selectionIds: Set<Long>,
    swipeReplyAllowed: Boolean,
    translationLanguages: Map<Long, String>,
    translationStates: Map<Pair<Long, String>, TranslationUiState>,
    transcriptionErrors: Map<Long, LocalTranscriptionFailure>,
    transcribingIds: Set<Long>,
    ratedTranscriptions: Set<Long>,
    speechRecognitionUi: SpeechRecognitionUi,
    riskGuard: StealthRiskGuard,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)?,
    onServiceUser: (Long) -> Unit,
    onOpenUserChat: (Long) -> Unit,
    onOpenComments: (ChatMessage) -> Unit,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)?,
    onOpenMedia: (Long) -> Unit,
    onOpenActionsOnLongPress: (Long) -> Unit,
    onActionAnchorBounds: (Rect) -> Unit,
    onShowFailedSendTarget: (FailedSendTarget) -> Unit,
    onRetryLocalTextAttempt: (Long) -> Unit,
    onRetryFailedMessages: (List<Long>) -> Unit,
    onJump: (MessageReply) -> Unit,
    onSwipeReply: (Long) -> Unit,
    onReaction: (Long, ReactionOption) -> Unit,
    onToggleSelection: (ChatMessage) -> Unit,
    onRequestBotWebApp: (String, Long, Long, BotWebAppSource, String) -> Unit,
    onRetryTranslation: (ChatMessage) -> Unit,
    onCancelTranslation: (Long) -> Unit,
    onDismissTranslation: (Long) -> Unit,
    onTranscribe: (ChatMessage) -> Unit,
    onRateTranscription: (ChatMessage, Boolean) -> Unit,
    onStickerOpen: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val palette = beyGramPalette()
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = metrics.listPadding + chromeTopPad, bottom = metrics.listPadding + chromeBottomPad),
        verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing)
    ) {
        items(messageListItems, key = { it.key }, contentType = { chatListContentType(it) }) { listItem ->
            ChatScreenColumn(Modifier.fillMaxWidth()) {
                daySeparators[listItem.key]?.let { ChatHistoryDateSeparator(it) }
                feedTopicHeaders[listItem.key]?.let { topicId ->
                    ForumFeedTopicHeader(
                        topic = forumTopicsById[topicId],
                        observeFile = observeFile,
                        downloadFile = onDownloadFile,
                        observeCustomEmoji = conversation::observeCustomEmoji,
                        onOpen = onOpenForumTopic,
                        modifier = Modifier.padding(horizontal = metrics.sideInset)
                    )
                }
                if (listItem.unreadSeparator) {
                    UnreadMessagesSeparator()
                } else if (listItem.pendingVideoNote != null) {
                    PendingVideoNoteBubble(listItem.pendingVideoNote)
                } else if (listItem.mediaAlbumId != 0L) {
                    val latest = listItem.messages.maxByOrNull { it.id }
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listItem.messages.map { it.id }) }
                    val effectMessage = listItem.messages.firstOrNull { it.effectId != 0L }
                    val albumLaunches = mediaLaunchController.active.mapNotNull { flight ->
                        val id = resolveMessageIdThroughReplacements(flight.messageId, history.recentReplacements)
                        listItem.messages.firstOrNull {
                            it.id == id && it.isOutgoing &&
                                it.sendState != MessageSendState.Failed && it.media?.let { media ->
                                    !media.hasSpoiler && !media.isSecret && !media.isSecretChat
                                } == true
                        }?.let { id to flight }
                    }.toMap()
                    MessageAnimationFrame(
                        animate = animateArrival && albumLaunches.isEmpty(),
                        outgoing = latest?.isOutgoing == true,
                        config = messageAnimations,
                        sideInset = metrics.sideInset
                    ) {
                        MessageEffectFrame(
                            effectId = effectMessage?.effectId ?: 0L,
                            messageId = effectMessage?.id ?: latest?.id ?: 0L,
                            animate = animateArrival,
                            observeEffect = conversation::observeMessageEffect,
                            observeFile = observeFile,
                            downloadFile = onDownloadFile,
                            limiter = effectLimiter
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                DisposableEffect(listItem.key) {
                                    onDispose {
                                        listItem.messages.forEach { bubbleCoordinates.remove(it.id); mediaCoordinates.remove(it.id) }
                                    }
                                }
                                val commentsPost = listItem.messages.firstOrNull { it.replyCount > 0 }
                                    ?: listItem.messages.firstOrNull()
                                val albumComments = commentsPost?.takeIf { showsChannelComments(chat.isChannel, it.isChannelPost, linkedDiscussionChatId) }
                                ChatGroupAvatarRow(
                                    showGroupAvatars, latest, groupAvatars[listItem.key], groupMembers, groupAvatarPhotos,
                                    observeFile, onDownloadFile, onServiceUser.takeUnless { selectionMode },
                                    peerColors = conversation.takeIf { supportsGroupAvatars }
                                ) {
                                    MessageAlbumBubble(
                                        listItem.messages, observeFile, onDownloadFile,
                                        isRead = latest?.let { it.isOutgoing && it.sendState == MessageSendState.Sent && it.id <= history.lastReadOutboxMessageId } == true,
                                        highlighted = listItem.messages.any { it.id == highlighted || it.id == actionsId },
                                        onOpen = onOpenMedia, onActions = onOpenActionsOnLongPress, onReplyClick = onJump,
                                        onPlayed = { conversation.openMessageContent(chat.id, it) },
                                        onMediaBounds = { coordinates ->
                                            listItem.messages.forEach { mediaCoordinates[it.id] = coordinates }
                                            if (actionsId != null && listItem.messages.any { it.id == actionsId }) {
                                                coordinates.mediaScreenBounds()?.let(onActionAnchorBounds)
                                            }
                                        },
                                        onReply = if (swipeReplyAllowed) onSwipeReply else null,
                                        onReaction = onReaction, loadQuickReactions = conversation::availableReactions,
                                        observeCustomEmoji = conversation::observeCustomEmoji,
                                        onRetry = { onRetryFailedMessages(listItem.messages.filter { it.sendState == MessageSendState.Failed }.map { it.id }) },
                                        selectionMode = selectionMode,
                                        selected = listItem.messages.all { it.id in selectionIds },
                                        onToggleSelection = { listItem.messages.firstOrNull()?.let(onToggleSelection) },
                                        commentsBar = albumComments?.let { post ->
                                            { ChannelCommentsBar(listItem.messages.maxOfOrNull { it.replyCount } ?: 0) { onOpenComments(post) } }
                                        },
                                        onAutoDownloadFile = onAutoDownloadFile,
                                        showSenderHeader = senderHeaderByKey[listItem.key] == true,
                                        senderMember = latest?.let { groupMembers[it.senderId] },
                                        onOpenMessageEntity = onOpenMessageEntity,
                                        composerMediaLaunches = albumLaunches,
                                        onComposerMediaLaunchFinished = mediaLaunchController::finish,
                                        onLoadVersions = { chatId, messageId -> conversation.messageVersions(chatId, messageId) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val message = listItem.messages.single()
                    val animateArrival = remember(listItem.key) { animationTracker.claim(listOf(message.id)) }
                    // A confirmed send replaces the temporary id; the dice throw keeps its original identity.
                    val diceAnimationId = rememberDiceAnimationId(message.id, history.recentReplacements)
                    val launch = launchTargets[message.id]
                    val mediaLaunch = mediaLaunchController.active.firstOrNull {
                        message.media?.kind in setOf(MediaKind.Photo, MediaKind.Video) && message.isOutgoing &&
                            message.media?.let { !it.hasSpoiler && !it.isSecret && !it.isSecretChat } == true &&
                            message.sendState != MessageSendState.Failed &&
                            resolveMessageIdThroughReplacements(it.messageId, history.recentReplacements) == message.id
                    }
                    val launchModifier = if (launch != null) Modifier.graphicsLayer {
                        alpha = if (launch.ownsRow(message.id)) 0f else 1f
                    } else Modifier
                    // Only an open action menu and an in-flight launch follow the bubble while the list moves. Other rows only
                    // store their layout; the rectangle is computed when something reads it.
                    val bubblePosition: (LayoutCoordinates) -> Unit = { coordinates ->
                        bubbleCoordinates[message.id] = coordinates
                        val inActionMenu = actionsId == message.id
                        if (inActionMenu || launch != null) {
                            val rect = coordinates.boundsInWindow()
                            if (inActionMenu) onActionAnchorBounds(rect)
                            launch?.positionRow(message.id, rect)
                        }
                    }
                    val isCenteredRow = message.serviceText != null
                    val isCircleMedia = !isCenteredRow && message.media?.kind == MediaKind.VideoNote
                    val bubbleSideInset = if (isCenteredRow || isCircleMedia) 0.dp else metrics.sideInset
                    MessageAnimationFrame(
                        animate = animateArrival && launch == null && mediaLaunch == null,
                        outgoing = message.isOutgoing,
                        config = messageAnimations,
                        modifier = launchModifier,
                        centered = isCenteredRow,
                        sideInset = bubbleSideInset
                    ) {
                        MessageEffectFrame(
                            effectId = message.effectId,
                            messageId = message.id,
                            animate = animateArrival && launch == null,
                            observeEffect = conversation::observeMessageEffect,
                            observeFile = observeFile,
                            downloadFile = onDownloadFile,
                            limiter = effectLimiter
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                DisposableEffect(launch, message.id) {
                                    onDispose { launch?.releaseRow(message.id) }
                                }
                                DisposableEffect(message.id) {
                                    onDispose { bubbleCoordinates.remove(message.id); mediaCoordinates.remove(message.id) }
                                }
                                val translationLanguage = translationLanguages[message.id]
                                val translationState = translationLanguage?.let { translationStates[message.id to it] }
                                val media = message.media
                                val transcriptionFailure = transcriptionErrors[message.id]
                                    ?.takeIf { it.serverFailureAtStart == media?.transcriptionError }
                                    ?.failure
                                if (message.isCallEvent && message.serviceText != null) CallMessageChip(message.serviceText, message.time)
                                // A gift keeps the same text (search, previews and the screen reader use it) and gains the
                                // artwork, number and title that a plain chip cannot show.
                                else if (message.gift != null && message.serviceText != null) GiftChatCard(
                                    gift = message.gift,
                                    caption = message.serviceText,
                                    observeFile = observeFile,
                                    downloadFile = onDownloadFile
                                )
                                else if (message.serviceText != null) ServiceMessageChip(message.serviceText, message.serviceUserId, onServiceUser)
                                else {
                                    val localAttempt = localTextAttemptIds[message.id]
                                    ChatGroupAvatarRow(
                                        showGroupAvatars, message, groupAvatars[listItem.key], groupMembers, groupAvatarPhotos,
                                        observeFile, onDownloadFile, onServiceUser.takeUnless { selectionMode },
                                        peerColors = conversation.takeIf { supportsGroupAvatars }
                                    ) {
                                        MessageBubble(
                                            message, observeFile, onDownloadFile,
                                            isRead = message.isOutgoing && message.sendState == MessageSendState.Sent && message.id <= history.lastReadOutboxMessageId,
                                            highlighted = message.id == highlighted || message.id == actionsId,
                                            onOpen = { onOpenMedia(message.id) },
                                            onActions = {
                                                if (localAttempt != null) {
                                                    if (localAttempt.phase == TextSendAttemptPhase.Failed) onShowFailedSendTarget(FailedSendTarget(message, localAttempt.attemptId))
                                                } else {
                                                    onOpenActionsOnLongPress(message.id)
                                                }
                                            },
                                            onReplyClick = onJump,
                                            onReply = if (swipeReplyAllowed) onSwipeReply else null,
                                            onPlayed = { conversation.openMessageContent(chat.id, message.id) },
                                            musicLinkCards = !chat.isSecret,
                                            waveAccountId = accountId.takeIf { waveChat },
                                            onRetry = {
                                                if (localAttempt != null) {
                                                    onRetryLocalTextAttempt(localAttempt.attemptId)
                                                } else {
                                                    onRetryFailedMessages(listOf(message.id))
                                                }
                                            },
                                            connectionState = connectionState,
                                            onShowFailureDetails = if (message.isOutgoing && message.sendState == MessageSendState.Failed) {
                                                { onShowFailedSendTarget(FailedSendTarget(message, localAttempt?.attemptId)) }
                                            } else null,
                                            onReaction = onReaction, loadQuickReactions = conversation::availableReactions,
                                            quickReactionAcrossRow = true, observeCustomEmoji = conversation::observeCustomEmoji,
                                            inlineContent = { InlineBotButtons(message, conversation, riskGuard, onRequestBotWebApp, onOpenEntity = onOpenMessageEntity) },
                                            onBubblePositioned = bubblePosition,
                                            onMediaBounds = { coordinates -> mediaCoordinates[message.id] = coordinates },
                                            composerMediaLaunch = mediaLaunch,
                                            onComposerMediaLaunchFinished = mediaLaunchController::finish,
                                            translation = translationState,
                                            onTranslationRetry = { onRetryTranslation(message) },
                                            onTranslationCancel = { onCancelTranslation(message.id) },
                                            onTranslationDismiss = { onDismissTranslation(message.id) },
                                            transcriptionRequestPending = message.id in transcribingIds,
                                            transcriptionRequestError = transcriptionFailure,
                                            transcriptionRated = message.id in ratedTranscriptions,
                                            speechRecognitionUi = speechRecognitionUi,
                                            onTranscribe = if (message.sendState == MessageSendState.Sent && TranslationPolicy.canTranscribe(chat.isSecret, media)) {
                                                { onTranscribe(message) }
                                            } else null,
                                            onRateTranscription = if (chat.isSecret) {
                                                null
                                            } else {
                                                { isGood -> onRateTranscription(message, isGood) }
                                            },
                                            onPollVote = { messageId, choices ->
                                                riskGuard.run {
                                                    scope.launch {
                                                        conversation.votePoll(chat.id, messageId, choices)
                                                            .onFailure { onError(it.userMessage(context, R.string.chat_poll_vote_failed)) }
                                                    }
                                                }
                                            },
                                            onPollStop = { messageId ->
                                                riskGuard.run {
                                                    scope.launch {
                                                        conversation.stopPoll(chat.id, messageId)
                                                            .onFailure { onError(it.userMessage(context, R.string.chat_poll_stop_failed)) }
                                                    }
                                                }
                                            },
                                            onStopLiveLocation = { target ->
                                                if (target.special is MessageSpecialContent.LiveLocation) scope.launch {
                                                    conversation.stopLiveLocation(target.chatId, target.id)
                                                        .onFailure { onError(it.userMessage(context, R.string.chat_location_stop_failed)) }
                                                }
                                            },
                                            onWriteContact = onOpenUserChat,
                                            onLoadPollVoters = { messageId, optionIndex, offset ->
                                                conversation.pollVoters(chat.id, messageId, optionIndex, offset)
                                            },
                                            onLoadVersions = { chatId, messageId -> conversation.messageVersions(chatId, messageId) },
                                            onAddPollOption = { messageId, text ->
                                                if (riskGuard.confirm()) conversation.addPollOption(chat.id, messageId, text)
                                                else Result.failure(CancellationException("stealth risk declined"))
                                            },
                                            onAutoDownloadFile = onAutoDownloadFile,
                                            onMusicPlay = { selected, startMs ->
                                                // Never build a cross-topic, protected or secret conversation playlist.
                                                if (chat.isSecret || chat.hasProtectedContent || selected.systemPrivate) {
                                                    PlaybackEngine.toggle(context, selected.copy(secret = true), startPositionMs = startMs)
                                                } else if (PlaybackEngine.state.value.item?.key == selected.key) {
                                                    // A second tap preserves any pages already appended to the active queue.
                                                    PlaybackEngine.toggle(context, selected, startPositionMs = startMs)
                                                } else {
                                                    val fallback = context.getString(R.string.media_kind_audio)
                                                    val initial = orderedChatMusic(
                                                        messages.filter { forumTopicId == 0L || it.forumTopicId == forumTopicId },
                                                        accountId,
                                                        { id -> observeFile(id).value }, fallback
                                                    ).map { if (it.key == selected.key) selected else it }
                                                    val queue = if (initial.any { it.key == selected.key }) initial else
                                                        (initial + selected).sortedByDescending { it.origin?.messageId ?: 0L }
                                                    val index = queue.indexOfFirst { it.key == selected.key }
                                                    var cursor = queue.lastOrNull()?.origin?.messageId ?: 0L
                                                    suspend fun nextMusicPage(): List<PlaybackItem> {
                                                        // Empty filtered pages must advance the TDLib cursor, not stall pagination.
                                                        repeat(4) {
                                                            if (cursor == 0L) return emptyList()
                                                            val page = conversation.sharedMessages(chat.id, SharedMediaKind.Music, cursor)
                                                                .getOrElse { return emptyList() }
                                                            val previous = cursor
                                                            cursor = page.nextFromMessageId.takeIf { it != previous } ?: 0L
                                                            val next = orderedChatMusic(
                                                                page.messages.filter { forumTopicId == 0L || it.forumTopicId == forumTopicId },
                                                                accountId, { id -> observeFile(id).value }, fallback
                                                            )
                                                            if (next.isNotEmpty()) return next
                                                        }
                                                        return emptyList()
                                                    }
                                                    PlaybackEngine.playQueue(
                                                        context, queue, index,
                                                        PlaybackQueueSource(
                                                            accountId, observeFile, onDownloadFile, onAutoDownloadFile,
                                                            loadMore = { nextMusicPage() }
                                                        ),
                                                        startPositionMs = startMs
                                                    )
                                                }
                                            },
                                            animateSpecial = animateArrival && launch == null,
                                            hideForContextScene = contextSceneMessageId == message.id,
                                            diceAnimationId = diceAnimationId,
                                            selectionMode = selectionMode,
                                            selected = message.id in selectionIds,
                                            onToggleSelection = { onToggleSelection(message) },
                                            onStickerOpen = { stickerMedia -> onStickerOpen(stickerMedia.stickerSetId) },
                                            commentsBar = if (showsChannelComments(chat.isChannel, message.isChannelPost, linkedDiscussionChatId)) {
                                                { ChannelCommentsBar(message.replyCount) { onOpenComments(message) } }
                                            } else null,
                                            showSenderHeader = senderHeaderByKey[listItem.key] == true,
                                            senderMember = groupMembers[message.senderId],
                                            paidContent = rememberPaidContentActions(conversation),
                                            onOpenMessageEntity = onOpenMessageEntity
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        history.error?.let { historyError ->
            item(key = "history_error") {
                Column(Modifier.fillMaxWidth().errorAnnouncement(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(historyError.resolve(), color = palette.warningText, modifier = Modifier.padding(8.dp))
                    TextButton(onClick = { if (forumTopicId > 0L) conversation.loadOlderForumTopicMessages(chat.id, forumTopicId) else conversation.loadOlderMessages(chat.id) }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }
        if (history.isLoading) {
            item(key = "history_loading") {
                Text(
                    stringResource(R.string.chat_history_loading), color = palette.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center
                )
            }
        } else if (!history.hasOlderMessages && messages.isEmpty() && history.error == null) {
            item(key = "history_empty") {
                Text(stringResource(R.string.chat_history_empty), color = palette.textSecondary, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
