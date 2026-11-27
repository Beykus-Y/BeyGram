package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.components.LocalWindowLayout
import com.beykus.beygram.ui.components.WindowLayout
import com.beykus.beygram.ui.components.WindowNavigation
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.playback.parseShareTimecodeMs
import kotlinx.coroutines.flow.StateFlow

/** One chat bubble: regular text/media, special cards, and the placeholder for expired self-destructing media. */
@Composable
internal fun MessageBubble(message: ChatMessage, observeFile: (Int) -> StateFlow<MediaFileState>, onDownloadFile: (Int) -> Unit,
    isRead: Boolean = false, highlighted: Boolean = false, onOpen: () -> Unit, onActions: (() -> Unit)? = null,
    /** Chat bubbles start multi-select on long press; other callers keep their own long-press action. */
    longPressSelects: Boolean = false, onReplyClick: (MessageReply) -> Unit = {}, onPlayed: () -> Unit = {}, onRetry: () -> Unit = {}, onReply: ((Long) -> Unit)? = null, onReaction: (Long, ReactionOption) -> Unit, observeCustomEmoji: (Long) -> StateFlow<CustomEmoji?>, inlineContent: @Composable () -> Unit = {},
    onSenderClick: (() -> Unit)? = null,
    loadQuickReactions: (suspend (Long, Long) -> Result<List<ReactionOption>>)? = null,
    quickReactionAcrossRow: Boolean = false,
    /** Callers pass the chat/grouping decision; the sender name itself stays available for replies, search and profiles. */
    showSenderHeader: Boolean = true,
    senderMember: ProfileMember? = null,
    /** Music link cards (Yandex Music) are drawn only where the caller turns them on: the chat list, not secret chats. */
    musicLinkCards: Boolean = false,
    waveAccountId: String? = null,
    onBubblePositioned: ((LayoutCoordinates) -> Unit)? = null,
    /** Reports the layout of a bubble that carries media, so the viewer can open from it. */
    onMediaBounds: ((LayoutCoordinates) -> Unit)? = null,
    translation: TranslationUiState? = null,
    onTranslationRetry: (() -> Unit)? = null,
    onTranslationCancel: (() -> Unit)? = null,
    onTranslationDismiss: (() -> Unit)? = null,
    transcriptionRequestPending: Boolean = false,
    transcriptionRequestError: SpeechRecognitionFailure? = null,
    transcriptionRated: Boolean = false,
    onTranscribe: (() -> Unit)? = null,
    onRateTranscription: ((Boolean) -> Unit)? = null,
    speechRecognitionUi: SpeechRecognitionUi = SpeechRecognitionUi(),
    onAutoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    onMusicPlay: ((PlaybackItem, Long?) -> Unit)? = null,
    commentsBar: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onPollVote: ((Long, List<Int>) -> Unit)? = null,
    onPollStop: ((Long) -> Unit)? = null,
    onStopLiveLocation: ((ChatMessage) -> Unit)? = null,
    onWriteContact: ((Long) -> Unit)? = null,
    onLoadPollVoters: (suspend (messageId: Long, optionIndex: Int, offset: Int) -> Result<PollVotersPage>)? = null,
    onAddPollOption: (suspend (messageId: Long, text: String) -> Result<Unit>)? = null,
    /** Opens the set of a sticker message; when absent a sticker tap does not fall through to the media viewer. */
    onStickerOpen: ((MessageMedia) -> Unit)? = null,
    animateSpecial: Boolean = false,
    diceAnimationId: Long = 0L,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    paidContent: PaidContentActions? = null,
    /** Loads the earlier texts that Chat Memory kept for this message; without it a bubble shows no versions link. */
    onLoadVersions: (suspend (chatId: Long, messageId: Long) -> List<ChatMemoryVersion>)? = null,
    connectionState: TelegramConnectionState = TelegramConnectionState.Connected,
    onShowFailureDetails: (() -> Unit)? = null,
    composerMediaLaunch: ComposerMediaLaunch? = null,
    onComposerMediaLaunchFinished: (ComposerMediaLaunch) -> Unit = {},
    paneInset: Boolean = true,
    hideForContextScene: Boolean = false,
    contextTextSelection: MessageContextTextSelection? = null,
    contextScenePassive: Boolean = false
) {
    val palette = beyGramPalette()
    val metrics = LocalBubbleMetrics.current
    // The versions sheet belongs to the bubble that opened it, and is composed only while it is open.
    var showVersions by remember(message.id) { mutableStateOf(false) }
    val loadVersions = onLoadVersions
    if (showVersions && loadVersions != null) ChatMemoryVersionsSheet(message, loadVersions, onDismiss = { showVersions = false })
    val openVersions: () -> Unit = { showVersions = true }
    val shape = bubbleShape(message.isOutgoing, metrics)
    val hasText = message.text.isNotBlank()
    val special = message.special
    // Expired media owns its card; unknown content still gets the explicit fallback, never an empty bubble.
    val renderedSpecial = resolvedSpecialContent(message)
    // A BG Wave invitation is drawn as a card; its hidden service link is never shown as text or as a page preview.
    val isWave = remember(message.id, message.text, message.entities, message.isForwarded, message.senderId, waveAccountId) { waveAccountId != null && bgWaveStateOf(message) != null }
    val showText = hasText && special !is MessageSpecialContent.Contact && !isWave
    val circle = message.media?.kind == MediaKind.VideoNote
    // Стикер лежит без пузыря: ни цвета, ни ширины фото.
    val stickerMedia = message.media?.takeIf { it.kind == MediaKind.Sticker }
    val sticker = stickerMedia != null
    // Время и статус кружка находятся под кругом, вне обрезки видео.
    val inCircle = circle && message.sendState != MessageSendState.Failed
    // Пока сообщение отправляется или не ушло, его id временный и потом меняется: место для общего плеера не ставим.
    val playbackOrigin = if (!contextScenePassive && message.sendState == MessageSendState.Sent) PlaybackOrigin(message.chatId, message.id, LocalPlaybackAccountId.current) else null
    val statusIcon = when {
        !message.isOutgoing -> null
        message.sendState == MessageSendState.Sending -> Icons.Rounded.Schedule
        message.sendState == MessageSendState.Sent -> if (isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done
        else -> null
    }
    // Фото, видео и гифки без подписи: время ставится поверх нижнего правого угла, как в Telegram.
    val overlayMeta = !circle && !hasText && message.reply == null && message.sendState != MessageSendState.Failed &&
        message.media?.kind?.let { it == MediaKind.Photo || it == MediaKind.Video || it == MediaKind.Animation } == true
    val compactCollection = message.media?.let { com.beykus.beygram.ui.screens.bgmusic.isBgMusicDocument(it) } == true && !hasText && message.reply == null && renderedSpecial == null && message.linkPreview == null && translation == null
    val lastLineMeta = showText && useLastLineTextMetadata(message, hasTranslation = translation != null)
    val showAuthor = showSenderHeader && !message.isOutgoing && message.senderName.isNotBlank()
    val bodyTopInset = when {
        circle -> metrics.circleTopInset
        lastLineMeta && (showAuthor || message.isForwarded) -> minOf(4.dp, metrics.textTopInset)
        else -> metrics.textTopInset
    }
    val openMediaLabel = stringResource(R.string.a11y_open_attachment)
    val openStickerSetLabel = if (sticker && onStickerOpen != null) stringResource(R.string.sticker_picker_open_set) else null
    val longPressLabel = stringResource(if (longPressSelects) R.string.message_action_select else R.string.a11y_message_actions)
    val selectionLabel = if (selectionMode) stringResource(if (selected) R.string.message_selection_deselect else R.string.message_selection_select) else null
    val selectionState = if (selectionMode) stringResource(if (selected) R.string.message_selection_selected else R.string.message_selection_not_selected) else null
    var captionPlay by remember(message.id) { mutableStateOf<AudioPlayRequest?>(null) }
    // On a tablet the pane is wide: a bubble grows with it (70 % of the pane, 292 to 480 dp) instead of hugging the edge.
    val paneWidth = com.beykus.beygram.ui.screens.chats.LocalChatPaneWidthDp.current
        ?: chatPaneWidthDp(LocalWindowLayout.current)
    val bubbleMax = chatBubbleMaxWidthDp(paneWidth).dp
    val mediaSize = message.media?.let { media ->
        val availableWidth = minOf(bubbleMax.value, paneWidth - metrics.sideInset.value * 2)
        when {
            media.kind.isSingleVisualMedia() -> singleMediaSize(paneWidth, availableWidth, media.width, media.height)
            sticker -> sentStickerSize(paneWidth, availableWidth, media.width, media.height)
            else -> null
        }
    }
    val mediaFlightModifier = composerMediaLaunchModifier(composerMediaLaunch, onComposerMediaLaunchFinished)
    val quickReaction = if (loadQuickReactions != null && !selectionMode && message.sendState == MessageSendState.Sent) {
        rememberBubbleQuickReaction(message, loadQuickReactions, onReaction)
    } else null
    Column(
        Modifier.fillMaxWidth()
            .quickReactionOnFreeSpace(message.chatId, message.id, quickReaction.takeIf { quickReactionAcrossRow })
            .then(if (circle || !paneInset) Modifier else Modifier.padding(horizontal = metrics.sideInset)),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        SwipeToReply(
            enabled = onReply != null && message.sendState == MessageSendState.Sent,
            outgoing = message.isOutgoing,
            onReply = { onReply?.invoke(message.id) }
        ) { swipe ->
            // Голосовое и кружок управляются своими элементами в пузыре и не открывают просмотрщик.
            Box(swipe) {
                BubbleLayout(
                    Modifier.testTag("message-bubble:${message.id}")
                        .widthIn(max = if (circle) androidx.compose.ui.unit.Dp.Infinity else bubbleMax)
                        .then(if (message.media != null && !circle && !sticker)
                             Modifier.width(mediaSize?.width ?: if (message.media.kind == MediaKind.Audio) 280.dp else 244.dp) else Modifier)
                        .then(mediaFlightModifier).clip(shape)
                        .then(if (hideForContextScene) Modifier.alpha(0f) else Modifier)
                        .background(if (circle || sticker) Color.Transparent else if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)
                        .then(if (message.memory?.deletedAtMillis != null) Modifier.alpha(0.6f) else Modifier)
                        .then(if ((selected || highlighted) && !circle) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                        .then(if (onBubblePositioned != null) Modifier.onGloballyPositioned { onBubblePositioned(it) } else Modifier)
                        .then(if (onMediaBounds != null && message.media != null) Modifier.onGloballyPositioned { onMediaBounds(it) } else Modifier)
                        .semantics {
                            if (selectionMode) {
                                this.selected = selected
                                if (selectionState != null) stateDescription = selectionState
                            }
                        }
                        .combinedClickable(
                            onClickLabel = when {
                                selectionMode -> selectionLabel
                                sticker -> openStickerSetLabel
                                message.media?.kind == MediaKind.Audio -> null
                                message.media != null && !message.media.kind.playsInline() -> openMediaLabel
                                else -> null
                            },
                            onLongClickLabel = if (selectionMode) selectionLabel else longPressLabel,
                            onClick = {
                                if (!contextScenePassive) {
                                    when {
                                        selectionMode -> onToggleSelection()
                                        sticker -> stickerMedia?.let { onStickerOpen?.invoke(it) }
                                        // A tap on the caption of a track, such as a shared timecode, plays it in the music player.
                                        message.media?.kind == MediaKind.Audio ->
                                            captionPlay = AudioPlayRequest(parseShareTimecodeMs(message.text, message.media.durationSeconds))
                                        message.media != null && !message.media.kind.playsInline() -> onOpen()
                                    }
                                }
                            },
                            // Media and other interactive content need an immediate single tap.
                            onDoubleClick = quickReaction.takeIf { !contextScenePassive && message.media == null && message.special == null },
                            onLongClick = if (contextScenePassive) ({}) else if (selectionMode) onToggleSelection else onActions
                        ),
                    overlayMeta = overlayMeta,
                    body = {
                    Column {
                        if (showAuthor) ChatGroupAuthorName(message.senderName, senderMember,
                            modifier = (if (lastLineMeta) Modifier.padding(horizontal = metrics.textInset).padding(top = 6.dp)
                            else Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                .then(if (onSenderClick != null) Modifier.clickable { onSenderClick() } else Modifier),
                            observeCustomEmoji = observeCustomEmoji,
                            observeFile = observeFile,
                            downloadFile = onDownloadFile
                        )
                        ForwardedMessageHeader(message)
                        // Media sits flush against the bubble's top and side edges; text keeps its own padding.
                        message.media?.let { media ->
                            if (media.kind == MediaKind.VideoNote) VideoNoteBubble(media, observeFile, onDownloadFile,
                                onPlayed = onPlayed,
                                onLongPress = if (selectionMode) onToggleSelection else onActions,
                                time = if (inCircle) message.time else "", statusIcon = if (inCircle) statusIcon else null,
                                statusDescription = when {
                                    !message.isOutgoing -> null
                                    message.sendState == MessageSendState.Sending -> stringResource(R.string.chat_sending_description)
                                    isRead -> stringResource(R.string.chat_read_description)
                                    else -> stringResource(R.string.chat_sent_description)
                                }, unseen = !message.isOutgoing && !media.isViewed, highlighted = highlighted, origin = playbackOrigin,
                                transcriptionRequestPending = transcriptionRequestPending,
                                transcriptionRequestError = transcriptionRequestError,
                                transcriptionRated = transcriptionRated,
                                onTranscribe = onTranscribe,
                                onRateTranscription = onRateTranscription,
                                speechRecognitionUi = speechRecognitionUi,
                                autoDownloadFile = onAutoDownloadFile, outgoing = message.isOutgoing)
                            else MediaPreview(media, observeFile, onDownloadFile,
                                onOpen = if (selectionMode) onToggleSelection else onOpen,
                                onLongPress = if (selectionMode) onToggleSelection else onActions,
                                onPlayed = onPlayed, origin = playbackOrigin,
                                transcriptionRequestPending = transcriptionRequestPending,
                                transcriptionRequestError = transcriptionRequestError,
                                transcriptionRated = transcriptionRated,
                                onTranscribe = onTranscribe,
                                onRateTranscription = onRateTranscription,
                                speechRecognitionUi = speechRecognitionUi,
                                viewOnce = message.isViewOnce,
                                autoDownloadFile = onAutoDownloadFile,
                                onMusicPlay = onMusicPlay,
                                externalPlay = captionPlay,
                                onStickerOpen = onStickerOpen?.let { callback ->
                                    stickerMedia?.let { stickerMedia ->
                                        { if (selectionMode) onToggleSelection() else callback(stickerMedia) }
                                    }
                                }, selectionActive = selectionMode, mediaSize = mediaSize)
                        }
                        message.expiredMediaKind?.let { kind -> ExpiredMediaCard(kind) }
                        // Без текста и ответа под фото нет строки: время уже стоит поверх него.
                        if (showText || renderedSpecial != null || message.reply != null || (!overlayMeta && !compactCollection)) Column(Modifier.padding(start = metrics.textInset, end = metrics.textInset, top = bodyTopInset)) {
                            message.reply?.let { reply ->
                                ReplyQuote(
                                    reply = reply,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    onClick = { if (selectionMode) onToggleSelection() else onReplyClick(reply) },
                                    onLongClick = if (selectionMode) onToggleSelection else onActions,
                                    onClickLabel = selectionLabel,
                                    onLongClickLabel = if (selectionMode) selectionLabel else longPressLabel,
                                )
                            }
                            renderedSpecial?.let { content ->
                                SpecialMessageContentView(
                                    special = content,
                                    message = message,
                                    onPollVote = onPollVote,
                                    onPollStop = onPollStop,
                                    onStopLiveLocation = onStopLiveLocation,
                                    onWriteContact = onWriteContact,
                                    onLoadPollVoters = onLoadPollVoters,
                                    onAddPollOption = onAddPollOption,
                                    observeFile = observeFile,
                                    downloadFile = onDownloadFile,
                                    autoDownloadFile = onAutoDownloadFile,
                                    animateDice = animateSpecial,
                                    diceAnimationId = diceAnimationId,
                                    paidContent = paidContent
                                )
                            }
                            if (isWave && waveAccountId != null) BgWaveMessageCard(message, waveAccountId)
                            else MessageMusicLinkCard(
                                message, observeFile, onDownloadFile, autoDownloadFile = onAutoDownloadFile,
                                onOpenEntity = onOpenMessageEntity, enabled = musicLinkCards,
                                messageText = {
                                    val trailingMetadata: (@Composable () -> Unit)? = if (lastLineMeta) {
                                        {
                                            BubbleMeta(message, isRead, retryAvailable = false, overlay = false,
                                                onRetry = onRetry, inline = true, insideTextInsets = true, connectionState = connectionState)
                                        }
                                    } else null
                                    val textModifier = if (lastLineMeta && message.reply != null) Modifier.fillMaxWidth() else Modifier
                                    if (contextTextSelection != null && showText) {
                                        SelectableMessageBody(contextTextSelection, textModifier, trailingMetadata)
                                    } else if (trailingMetadata != null && message.entities.isEmpty()) {
                                        PlainMessageWithMeta(message.text, textModifier, trailingMetadata)
                                    } else if (showText) {
                                        FormattedMessageText(
                                            message, observeCustomEmoji, observeFile, onDownloadFile,
                                            onOpenEntity = onOpenMessageEntity,
                                            entityClicksEnabled = !selectionMode,
                                            modifier = textModifier,
                                            trailingMetadata = trailingMetadata
                                        )
                                    }
                                }
                            )
                            translation?.let { state ->
                                TranslationBlock(
                                    state = state,
                                    onRetry = { onTranslationRetry?.invoke() },
                                    onCancel = { onTranslationCancel?.invoke() },
                                    onDismiss = { onTranslationDismiss?.invoke() },
                                    messageId = message.id,
                                    observeCustomEmoji = observeCustomEmoji,
                                    observeFile = observeFile,
                                    downloadFile = onDownloadFile
                                )
                            }
                        }
                    }
                },
                meta = {
                    if (!inCircle && !lastLineMeta) BubbleMeta(message, isRead, retryAvailable = message.isOutgoing && message.sendState == MessageSendState.Failed,
                        overlay = overlayMeta, onRetry = onRetry,
                        onShowVersions = if (onLoadVersions != null) openVersions else null,
                        connectionState = connectionState, onShowFailureDetails = onShowFailureDetails, compactDocument = compactCollection)
                },
                footer = {
                    // Inline keyboards and reactions must stack, never paint over one another.
                    Column {
                        inlineContent()
                        // Reactions stay inside the clipped bubble and close to its bottom edge.
                        // Compact pills keep their Compose-expanded touch targets.
                        if (!selectionMode) MessageReactionChips(
                            message.reactions, observeCustomEmoji, observeFile, onDownloadFile,
                            onToggle = { onReaction(message.id, it) },
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 2.dp)
                        )
                    }
                },
                bottomBar = { if (!circle && !sticker) commentsBar?.invoke() }
            )
                // Media previews own tap gestures (play, open, video note). In selection mode a transparent overlay
                // makes the whole bubble selectable and keeps playback/download controls out of the way.
                if (selectionMode && message.media != null) Box(
                    Modifier.matchParentSize().combinedClickable(
                        onClickLabel = selectionLabel,
                        onLongClickLabel = selectionLabel,
                        onClick = onToggleSelection,
                        onLongClick = onToggleSelection
                    )
                )
                if (selectionMode) SelectionCheckBadge(selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        // Кружок и стикер лежат без пузыря: полосе комментариев нужна своя подложка того же цвета.
        if (circle || sticker) commentsBar?.let { bar ->
            Box(Modifier.padding(top = 2.dp).width(244.dp).clip(RoundedCornerShape(metrics.replyRadius))
                .background(if (message.isOutgoing) palette.outgoingBubble else palette.incomingBubble)) { bar() }
        }
     }
}


/** A Telegram call is an event with a direction and result, not an empty message bubble. */
@Composable
internal fun CallMessageChip(text: String, time: String) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            Modifier.clip(styledShape(16.dp))
                .background(palette.surface.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Call, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(text, color = palette.textPrimary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2)
                Text(time, color = palette.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

/** Telegram service actions have no sender bubble, read checks or swipe-to-reply affordances. */
@Composable
internal fun ServiceMessageChip(text: String, userId: Long = 0L, onOpenUser: (Long) -> Unit = {}) {
    val palette = beyGramPalette()
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.clip(styledShape(14.dp))
                .background(palette.surface.copy(alpha = 0.87f))
                .then(if (userId > 0L) Modifier.clickable { onOpenUser(userId) } else Modifier)
                .padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}


/** A bubble is 70 % of the chat pane, never narrower than the phone's 292 dp and never wider than 480 dp. */
internal fun chatBubbleMaxWidthDp(paneWidthDp: Float): Float = (paneWidthDp * 0.7f).coerceIn(292f, 480f)

/** The width the chat pane gets in [layout]: the whole window on a phone, what the rail and the list leave in two panes. */
internal fun chatPaneWidthDp(layout: WindowLayout): Float {
    if (!layout.twoPane) return layout.widthDp
    val rail = when (layout.navigation) {
        WindowNavigation.SlimRail -> 56f
        WindowNavigation.Rail -> 80f
        else -> 0f
    }
    return layout.widthDp - rail - com.beykus.beygram.ui.screens.chats.chatListPaneWidthDp(layout.widthDp, layout.heightCompact) - 1f
}
