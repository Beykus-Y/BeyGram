package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.diagnostics.recordAppEvent
import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

internal class TdConversationActions(
    client: () -> Client, private val history: MessageHistoryStore,
    private val map: (TdApi.Message) -> ChatMessage,
    private val onTopicDraft: (Long, Int, TdApi.DraftMessage?) -> Unit = { _, _, _ -> }
) {
    private val request = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replyRequests = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()
    // Нажатия на одно сообщение идут по очереди: каждое читает реакции, которые меняет. Другие сообщения не ждут.
    private val reactionLocks = KeyedMutex<Pair<Long, Long>>()
    private val _limits = MutableStateFlow(MessageLimits())
    val limits = _limits.asStateFlow()
    private val _activeReactions = MutableStateFlow<List<String>>(emptyList())
    fun activeEmojiReactions(emojis: List<String>) { _activeReactions.value = emojis }
    fun option(name: String, value: TdApi.OptionValue) {
        val limit = (value as? TdApi.OptionValueInteger)?.value?.toInt()?.coerceIn(1, 65536) ?: return
        when (name) {
            "message_text_length_max" -> _limits.update { it.copy(text = limit) }
            "message_caption_length_max" -> _limits.update { it.copy(caption = limit) }
        }
    }
    fun initialize() { scope.launch { listOf("message_text_length_max", "message_caption_length_max").forEach { name -> runCatching { option(name, request(TdApi.GetOption(name))) } } } }

    suspend fun capabilities(chatId: Long, messageId: Long): Result<MessageCapabilities> = conversationResult {
        val properties = request(TdApi.GetMessageProperties(chatId, messageId))
        val message = request(TdApi.GetMessage(chatId, messageId))
        MessageCapabilities(properties.canBeReplied, properties.canBeCopied && editable(message.content),
            properties.canBeEdited && message.isOutgoing && editable(message.content),
            properties.canBeDeletedOnlyForSelf, properties.canBeDeletedForAllUsers,
            canForward = properties.canBeForwarded, canSave = properties.canBeSaved,
            canPin = properties.canBePinned, canGetLink = properties.canGetLink)
    }

    /** Предполагаем, что TDLib считает позицию от новых к старым, как результаты поиска: номер от самого раннего — total - position + 1. */
    suspend fun mediaPosition(chatId: Long, messageId: Long, kind: MediaKind): Result<MediaPosition> = conversationResult {
        val filter = searchFilter(kind)
        val fromNewest = request(TdApi.GetChatMessagePosition(chatId, null, filter, messageId)).count
        val total = request(TdApi.GetChatMessageCount(chatId, null, filter, false)).count
        if (fromNewest !in 1..total) throw UserMessageException(UiText.Res(R.string.err_media_position_unknown))
        MediaPosition(total - fromNewest + 1, total)
    }
    private fun searchFilter(kind: MediaKind): TdApi.SearchMessagesFilter = when (kind) {
        MediaKind.Photo, MediaKind.Video -> TdApi.SearchMessagesFilterPhotoAndVideo()
        MediaKind.Animation -> TdApi.SearchMessagesFilterAnimation()
        MediaKind.Audio -> TdApi.SearchMessagesFilterAudio()
        MediaKind.Document -> TdApi.SearchMessagesFilterDocument()
        MediaKind.Voice -> TdApi.SearchMessagesFilterVoiceNote()
        MediaKind.VideoNote -> TdApi.SearchMessagesFilterVideoNote()
        MediaKind.Sticker -> error("Стикеры не нумеруются")
    }

    /** True for a forum supergroup: its replies belong to a topic, which a plain send cannot pick. */
    suspend fun isForum(chatId: Long): Result<Boolean> = conversationResult {
        val type = request(TdApi.GetChat(chatId)).type as? TdApi.ChatTypeSupergroup ?: return@conversationResult false
        request(TdApi.GetSupergroup(type.supergroupId)).isForum
    }

    /** The linked discussion supergroup of a broadcast channel; zero for ordinary chats and channels without one. */
    suspend fun linkedDiscussionChatId(chatId: Long): Result<Long> = requestResult {
        val type = request(TdApi.GetChat(chatId)).type as? TdApi.ChatTypeSupergroup ?: return@requestResult 0L
        if (!type.isChannel) return@requestResult 0L
        request(TdApi.GetSupergroupFullInfo(type.supergroupId)).linkedChatId
    }

    /**
     * Resolves a channel post to its discussion thread with GetMessageThread. A channel post whose comments were
     * disabled is reported by canGetMessageThread; TDLib errors are translated so hidden/removed threads never look
     * like a normal empty discussion.
     */
    suspend fun messageThread(channelChatId: Long, messageId: Long): Result<MessageThread> = conversationResult {
        if (channelChatId == 0L || messageId <= 0L) throw UserMessageException(UiText.Res(R.string.thread_comments_unavailable))
        val properties = try {
            request(TdApi.GetMessageProperties(channelChatId, messageId))
        } catch (error: TdRequestError) {
            throw threadRequestFailure(error)
        }
        if (!properties.canGetMessageThread) throw UserMessageException(UiText.Res(R.string.thread_comments_closed))
        val info = try {
            request(messageThreadRequest(channelChatId, messageId))
        } catch (error: TdRequestError) {
            throw threadRequestFailure(error)
        }
        if (info.chatId == 0L || info.messageThreadId == 0L) {
            throw UserMessageException(UiText.Res(R.string.thread_comments_unavailable))
        }
        val discussion = request(TdApi.GetChat(info.chatId))
        val supergroup = (discussion.type as? TdApi.ChatTypeSupergroup)?.let { type ->
            try { request(TdApi.GetSupergroup(type.supergroupId)) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: TdRequestError) { null }
        }
        val status = supergroup?.status
        val isMember = status !is TdApi.ChatMemberStatusLeft && status !is TdApi.ChatMemberStatusBanned
        // MessageThreadInfo.messages is only the thread prefix. The history call is the authoritative newest page,
        // and it keeps the initial scroll at the latest comments on a busy post.
        // Do not hide a failed first history request behind the thread prefix: that prefix is filtered out below,
        // which would turn a transient failure into a permanent empty discussion with no retry action.
        val firstPage = try {
            request(messageThreadHistoryRequest(info.chatId, info.messageThreadId, 0L))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: TdRequestError) {
            throw UserMessageException(UiText.Res(R.string.thread_comments_load_failed), error)
        }
        val pageMessages = firstPage.messages.orEmpty().filterNotNull()
        val initialMessages = pageMessages
            .filterNot { it.isChannelPost || it.id == info.messageThreadId }
            .sortedBy { it.id }
            .map(map)
        // These comments can lie outside the discussion chat's newest window; keeping them lets later edits and reactions reach the thread.
        history.recordOutsideWindow(info.chatId, initialMessages)
        // A page may be shorter than the reply count even when no older page remains, but a full page is not proof of
        // the end either; the next history call is the only authoritative check.
        val hasOlderMessages = initialMessages.isNotEmpty()
        MessageThread(
            channelChatId = channelChatId,
            channelMessageId = messageId,
            discussionChatId = info.chatId,
            threadId = info.messageThreadId,
            discussionTitle = discussion.title,
            replyCount = info.replyInfo?.replyCount ?: 0,
            unreadCount = info.unreadMessageCount,
            canReply = canWriteThreadComment(
                canGetMessageThread = properties.canGetMessageThread,
                hasReplyInfo = info.replyInfo != null,
                isMember = isMember,
                canSendBasicMessages = status.allowsThreadComment(discussion.permissions)
            ),
            messages = initialMessages,
            hasOlderMessages = hasOlderMessages
        )
    }

    /** One older page of comments from GetMessageThreadHistory, normalized to oldest-first order. */
    suspend fun threadComments(discussionChatId: Long, threadId: Long, fromMessageId: Long): Result<MessageThreadPage> = conversationResult {
        if (discussionChatId == 0L || threadId == 0L) throw UserMessageException(UiText.Res(R.string.thread_comments_unavailable))
        val page = request(messageThreadHistoryRequest(discussionChatId, threadId, fromMessageId))
        val messages = page.messages.orEmpty().filterNotNull()
            .filter { it.id != fromMessageId && it.id != threadId && !it.isChannelPost }
            .sortedBy { it.id }
            .map(map)
        history.recordOutsideWindow(discussionChatId, messages)
        val nextFromMessageId = messages.firstOrNull()?.id ?: 0L
        MessageThreadPage(
            messages = messages,
            nextFromMessageId = nextFromMessageId,
            hasMore = messages.isNotEmpty() && nextFromMessageId != 0L && nextFromMessageId != fromMessageId
        )
    }

    /**
     * Sends into the linked discussion group with a MessageTopicThread. A reply to a loaded comment keeps the same
     * thread and adds InputMessageReplyToMessage for the target; the channel itself is never the send target.
     */
    suspend fun sendThreadReply(
        discussionChatId: Long,
        threadId: Long,
        text: String,
        entities: List<MessageTextEntity>,
        replyToMessageId: Long?
    ): Result<ChatMessage> = conversationResult {
        val bounded = boundTelegramText(text, entities, limits.value.text)
        if (bounded.text.isBlank()) throw UserMessageException(UiText.Res(R.string.err_message_empty))
        val replyTarget = replyToMessageId?.takeIf { it > 0L }?.let { targetId ->
            if (!request(TdApi.GetMessageProperties(discussionChatId, targetId)).canBeReplied) {
                throw UserMessageException(UiText.Res(R.string.err_message_cannot_reply))
            }
            request(TdApi.GetMessage(discussionChatId, targetId))
        }
        val content = TdApi.InputMessageText().apply {
            this.text = formattedText(bounded.text, bounded.entities)
            // The discussion chat may have its own draft; a thread reply must not clear it.
            clearDraft = false
        }
        val sent = request(threadReplyRequest(discussionChatId, threadId, replyTarget?.id, content))
        history.put(map(sent))
        hydrateReply(sent)
        map(sent)
    }

    /**
     * Темы группы с темами, в которые можно писать; null для всего остального. TDLib отдаёт до 100 тем за запрос, поэтому идём по страницам.
     * Закрытые темы видны, если их создал этот аккаунт или он управляет темами: в закрытую тему пишут создатель и администраторы с этим правом.
     */
    suspend fun forumTopics(chatId: Long, onPage: (List<ForumTopic>) -> Unit = {}): Result<List<ForumTopic>?> = conversationResult<List<ForumTopic>?> {
        val type = request(TdApi.GetChat(chatId)).type as? TdApi.ChatTypeSupergroup ?: return@conversationResult null
        val supergroup = request(TdApi.GetSupergroup(type.supergroupId))
        if (!supergroup.isForum) return@conversationResult null
        val manageTopics = when (val status = supergroup.status) {
            is TdApi.ChatMemberStatusCreator -> true
            is TdApi.ChatMemberStatusAdministrator -> status.rights.canManageTopics
            else -> false
        }
        val topics = mutableListOf<ForumTopic>()
        var offsetDate = 0
        var offsetMessageId = 0L
        var offsetTopicId = 0
        var pages = 0
        val visitedCursors = mutableSetOf<Triple<Int, Long, Int>>()
        while (true) {
            if (!visitedCursors.add(Triple(offsetDate, offsetMessageId, offsetTopicId))) break
            val page = request(TdApi.GetForumTopics(chatId, "", offsetDate, offsetMessageId, offsetTopicId, FORUM_TOPIC_PAGE_SIZE))
            page.topics.filter { !it.info.isHidden }
                .mapTo(topics) {
                    onTopicDraft(chatId, it.info.forumTopicId, it.draftMessage)
                    ForumTopic(
                        id = it.info.forumTopicId.toLong(),
                        title = it.info.name,
                        unreadCount = it.unreadCount,
                        isPinned = it.isPinned,
                        isClosed = it.info.isClosed,
                        canWrite = !it.info.isClosed || it.info.isOutgoing || manageTopics,
                        canManage = it.info.isOutgoing || manageTopics,
                        canPin = manageTopics,
                        order = it.order,
                        lastReadInboxMessageId = it.lastReadInboxMessageId,
                        lastReadOutboxMessageId = it.lastReadOutboxMessageId,
                        lastMessagePreview = (it.lastMessage?.content as? TdApi.MessageText)?.text?.text.orEmpty()
                    )
                }
            // Present page one without waiting for slow/large forum pagination.
            // Snapshot immutable data before the next suspendable TDLib request.
            onPage(uniqueForumTopics(topics))
            pages++
            if (page.topics.isEmpty() || page.nextOffsetForumTopicId == 0 || pages == MAX_FORUM_TOPIC_PAGES) break
            offsetDate = page.nextOffsetDate
            offsetMessageId = page.nextOffsetMessageId
            offsetTopicId = page.nextOffsetForumTopicId
        }
        uniqueForumTopics(topics)
    }

    /**
     * GetForumTopics can omit lastMessage for long-inactive topics or return non-text content.
     * Request the newest message only for a visible topic without a usable preview.
     * This does not alter the full, separately paged topic history.
     */
    suspend fun latestForumTopicPreview(chatId: Long, forumTopicId: Long): Result<String?> =
        conversationResult<String?> {
            if (forumTopicId <= 0L) return@conversationResult null
            val messages = request(TdApi.GetForumTopicHistory().apply {
                this.chatId = chatId
                this.forumTopicId = forumTopicId.toInt()
                fromMessageId = 0L
                offset = 0
                limit = 1
            })
            messages.messages.orEmpty().filterNotNull().firstOrNull()?.let { forumTopicMessagePreview(map(it)) }
        }

    /** All mutations go through TDLib; the server is the final authority for permissions. */
    suspend fun canCreateTopic(chatId: Long): Result<Boolean> = conversationResult {
        val chat = request(TdApi.GetChat(chatId))
        val supergroupType = chat.type as? TdApi.ChatTypeSupergroup ?: return@conversationResult false
        val group = request(TdApi.GetSupergroup(supergroupType.supergroupId))
        if (!group.isForum) return@conversationResult false
        when (val status = group.status) {
            is TdApi.ChatMemberStatusCreator -> true
            is TdApi.ChatMemberStatusAdministrator -> status.rights.canManageTopics
            else -> chat.permissions.canCreateTopics
        }
    }

    suspend fun createTopic(chatId: Long, title: String): Result<Long> = conversationResult {
        val name = title.trim()
        require(name.isNotEmpty() && name.codePointCount(0, name.length) <= 128)
        val info = request(TdApi.CreateForumTopic().apply {
            this.chatId = chatId
            this.name = name
            isNameImplicit = false
            icon = TdApi.ForumTopicIcon(0x6FB9F0, 0L)
        })
        info.forumTopicId.toLong()
    }

    suspend fun renameTopic(chatId: Long, topicId: Long, title: String): Result<Unit> = conversationResult {
        val name = title.trim()
        require(name.isNotEmpty() && name.codePointCount(0, name.length) <= 128)
        request(TdApi.EditForumTopic(chatId, topicId.toInt(), name, false, 0L))
        Unit
    }

    suspend fun closeTopic(chatId: Long, topicId: Long, closed: Boolean): Result<Unit> = conversationResult {
        request(TdApi.ToggleForumTopicIsClosed(chatId, topicId.toInt(), closed))
        Unit
    }

    suspend fun pinTopic(chatId: Long, topicId: Long, pinned: Boolean): Result<Unit> = conversationResult {
        request(TdApi.ToggleForumTopicIsPinned(chatId, topicId.toInt(), pinned))
        Unit
    }

    suspend fun deleteTopic(chatId: Long, topicId: Long): Result<Unit> = conversationResult {
        request(TdApi.DeleteForumTopic(chatId, topicId.toInt()))
        Unit
    }

    suspend fun sendText(chatId: Long, text: String, replyId: Long?, entities: List<MessageTextEntity> = emptyList(), forumTopicId: Long = 0L, clearDraft: Boolean = true, options: MessageSendOptions = MessageSendOptions()): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        if (options.viewOnce) throw UserMessageException(UiText.Res(R.string.err_view_once_media_only))
        val bounded = boundTelegramText(text, entities, limits.value.text)
        if (bounded.text.isBlank()) throw UserMessageException(UiText.Res(R.string.err_message_empty))
        send(chatId, TdApi.InputMessageText().apply { this.text = formattedText(bounded.text, bounded.entities); this.clearDraft = clearDraft }, replyId, forumTopicId, options)
    }.onFailure { recordSendFailure("text", it) }

    /** A poll carries its own question and options; no caption is involved, so only the send options are checked. */
    suspend fun sendPoll(chatId: Long, poll: OutgoingPoll, replyId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        val value = poll.normalized()
        value.problem()?.let { throw UserMessageException(it.message()) }
        val content = TdApi.InputMessagePoll().apply {
            question = formattedText(value.question, emptyList())
            this.options = value.options.map { option ->
                TdApi.InputPollOption().apply {
                    text = formattedText(option, emptyList())
                    media = null
                }
            }.toTypedArray()
            description = null
            media = null
            isAnonymous = value.isAnonymous || value.isQuiz
            allowsMultipleAnswers = value.allowsMultipleAnswers
            // Telegram quizzes are answered once; regular polls let the user change an answer.
            allowsRevoting = !value.isQuiz
            membersOnly = false
            countryCodes = emptyArray()
            shuffleOptions = false
            hideResultsUntilCloses = false
            type = if (value.isQuiz) TdApi.InputPollTypeQuiz().apply {
                correctOptionIds = value.correctOptionIndices.sorted().toIntArray()
                explanation = value.explanation.takeIf { it.isNotBlank() }?.let { formattedText(it, emptyList()) }
                explanationMedia = null
            } else TdApi.InputPollTypeRegular().apply { allowAddingOptions = false }
            openPeriod = 0
            closeDate = 0
            isClosed = false
        }
        send(chatId, content, replyId, forumTopicId, options)
    }.onFailure { recordSendFailure("poll", it) }

    suspend fun sendLocation(chatId: Long, point: GeoPoint, live: Boolean, replyId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        if (!point.isValid) throw UserMessageException(UiText.Res(R.string.chat_location_unavailable))
        val location = TdApi.Location(point.latitude, point.longitude, point.accuracyMeters)
        val content: TdApi.InputMessageContent = if (live) {
            TdApi.InputMessageLiveLocation().apply {
                this.location = TdApi.LiveLocation().apply {
                    this.location = location
                    livePeriod = LIVE_LOCATION_PERIOD_SECONDS
                    heading = 0
                    proximityAlertRadius = 0
                }
            }
        } else {
            TdApi.InputMessageLocation().apply { this.location = location }
        }
        send(chatId, content, replyId, forumTopicId, options)
    }.onFailure { recordSendFailure("location", it) }

    suspend fun sendDice(chatId: Long, emoji: String, replyId: Long?, forumTopicId: Long, options: MessageSendOptions): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        val value = emoji.trim()
        if (value.isEmpty()) throw UserMessageException(UiText.Res(R.string.chat_dice_send_failed))
        send(chatId, TdApi.InputMessageDice().apply { this.emoji = value; clearDraft = false }, replyId, forumTopicId, options)
    }.onFailure { recordSendFailure("dice", it) }

    suspend fun votePoll(chatId: Long, messageId: Long, optionIndices: List<Int>): Result<Unit> = conversationResult<Unit> {
        val choices = optionIndices.distinct().sorted()
        if (choices.isEmpty()) throw UserMessageException(UiText.Res(R.string.chat_poll_restriction_other))
        request(TdApi.SetPollAnswer(chatId, messageId, choices.toIntArray()))
        refreshMappedMessage(chatId, messageId)
    }.onFailure { recordSendFailure("poll_vote", it) }

    suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit> = conversationResult<Unit> {
        request(TdApi.StopPoll().apply { this.chatId = chatId; this.messageId = messageId; replyMarkup = null })
        refreshMappedMessage(chatId, messageId)
    }.onFailure { recordSendFailure("poll_stop", it) }

    /**
     * Appends an option only when TDLib reports canAddOption on the current message. The server can revoke that
     * permission between the bubble render and this request, so the check is repeated here.
     */
    suspend fun addPollOption(chatId: Long, messageId: Long, text: String): Result<Unit> = conversationResult<Unit> {
        val current = request(TdApi.GetMessage(chatId, messageId))
        val poll = (map(current).special as? MessageSpecialContent.Poll)?.poll
            ?: throw UserMessageException(UiText.Res(R.string.chat_poll_add_option_unavailable))
        if (!poll.canAddOption || poll.isClosed) {
            throw UserMessageException(UiText.Res(R.string.chat_poll_add_option_unavailable))
        }
        val value = text.trim()
        poll.addOptionProblem(value)?.let { throw UserMessageException(it.message()) }
        val option = TdApi.InputPollOption().apply {
            this.text = formattedText(value, emptyList())
            media = null
        }
        request(TdApi.AddPollOption(chatId, messageId, option))
        refreshMappedMessage(chatId, messageId)
    }.onFailure { recordSendFailure("poll_add_option", it) }

    /** TDLib stops a live-location share only when EditMessageLiveLocation.location is null. */
    suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<Unit> = conversationResult<Unit> {
        val updated = request(stopLiveLocationRequest(chatId, messageId))
        if (updated.schedulingState == null) history.put(map(updated))
    }.onFailure { recordSendFailure("live_location_stop", it) }

    private suspend fun refreshMappedMessage(chatId: Long, messageId: Long) {
        val message = try {
            request(TdApi.GetMessage(chatId, messageId))
        } catch (_: TimeoutCancellationException) {
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        if (message.schedulingState == null) {
            history.put(map(message))
            hydrateReply(message)
        }
    }
    /** Отправляет карточку контакта как TDLib InputMessageContact, а не как текст с vCard. */
    suspend fun sendContact(
        chatId: Long,
        contact: TdApi.Contact,
        replyId: Long? = null,
        forumTopicId: Long = 0L,
        options: MessageSendOptions = MessageSendOptions()
    ): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        send(chatId, contactMessageContent(contact), replyId, forumTopicId, options)
    }.onFailure { recordSendFailure("contact", it) }
    suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyId: Long?, entities: List<MessageTextEntity> = emptyList(), forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        requireViewOnceSupported(media, options)
        android.util.Log.i(TAG, "send_media_requested kind=${media.kind} bytes=${media.size} path=${media.path}")
        val bounded = boundTelegramText(caption, entities, limits.value.caption)
        send(chatId, media.toInputContent(bounded.text, bounded.entities, selfDestructType(options)), replyId, forumTopicId, options)
        android.util.Log.i(TAG, "send_media_request_accepted kind=${media.kind}")
    }.onSuccess { recordMediaAccepted(media.kind.name) }.onFailure { recordSendFailure(media.kind.name, it) }

    /** A sticker keeps its native content type; TDLib sends it by file id without downloading the picker preview. */
    suspend fun sendSticker(chatId: Long, sticker: OutgoingMedia, replyId: Long?, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit> = conversationResult<Unit> {
        requireFutureSchedule(options)
        require(sticker.kind == MediaKind.Sticker && sticker.remoteFileId > 0) { "Sticker file id is missing" }
        send(chatId, sticker.toInputContent(""), replyId, forumTopicId, options)
    }.onSuccess { recordMediaAccepted("sticker") }.onFailure { recordSendFailure("sticker", it) }

    suspend fun sendMediaAlbum(chatId: Long, media: List<OutgoingMedia>, caption: String, replyId: Long?, entities: List<MessageTextEntity> = emptyList(), forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()): Result<Unit> = conversationResult<Unit> {
        require(media.size in 2..10 && media.all { it.kind == MediaKind.Photo || it.kind == MediaKind.Video })
        requireFutureSchedule(options)
        if (options.viewOnce) throw UserMessageException(UiText.Res(R.string.err_view_once_single_media))
        val boundedCaption = boundTelegramText(caption, entities, limits.value.caption)
        val replyTarget = replyId?.let {
            if (!request(TdApi.GetMessageProperties(chatId, it)).canBeReplied) throw UserMessageException(UiText.Res(R.string.err_message_cannot_reply))
            request(TdApi.GetMessage(chatId, it))
        }
        val protocolOptions = sendOptions(options)
        val messages = request(TdApi.SendMessageAlbum().apply {
            this.chatId = chatId
            this.options = protocolOptions
            inputMessageContents = media.mapIndexed { index, item -> item.toInputContent(if (index == 0) boundedCaption.text else "", if (index == 0) boundedCaption.entities else emptyList()) }.toTypedArray()
            if (replyTarget != null) {
                topicId = replyTarget.topicId
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyTarget.id; pollOptionId = "" }
            } else if (forumTopicId > 0L) {
                topicId = TdApi.MessageTopicForum(forumTopicId.toInt())
            }
        })
        // A scheduled album belongs to TDLib's schedule queue, not to the visible chat history.
        messages.messages.orEmpty().filterNotNull().filter { it.schedulingState == null }.forEach { message -> history.put(map(message)); hydrateReply(message) }
    }.onSuccess { recordMediaAccepted("album") }.onFailure { recordSendFailure("album", it) }

    suspend fun pinnedMessages(chatId: Long, fromMessageId: Long, forumTopicId: Long = 0L): Result<PinnedMessagePage> = conversationResult {
        val page = request(pinnedMessagesRequest(chatId, fromMessageId, forumTopicId))
        PinnedMessagePage(page.messages.orEmpty().filterNotNull().map(map), page.totalCount, page.nextFromMessageId)
    }

    suspend fun retryMessages(chatId: Long, messageIds: List<Long>): Result<Unit> = conversationResult {
        val orderedIds = messageIds.distinct().sorted()
        require(orderedIds.isNotEmpty()) { "Нет сообщений для повтора" }
        request(TdApi.ResendMessages(chatId, orderedIds.toLongArray(), null, 0L))
        Unit
    }
    /** [forumTopicId] is used only without a reply target: a reply already carries its topic. */
    private suspend fun send(chatId: Long, content: TdApi.InputMessageContent, replyId: Long?, forumTopicId: Long = 0L, options: MessageSendOptions = MessageSendOptions()) {
        val replyTarget = replyId?.let {
            if (!request(TdApi.GetMessageProperties(chatId, it)).canBeReplied) throw UserMessageException(UiText.Res(R.string.err_message_cannot_reply))
            request(TdApi.GetMessage(chatId, it))
        }
        val protocolOptions = sendOptions(options)
        val message = request(TdApi.SendMessage().apply {
            this.chatId = chatId; inputMessageContent = content; this.options = protocolOptions
            if (replyTarget != null) {
                topicId = replyTarget.topicId
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyTarget.id; pollOptionId = "" }
            } else if (forumTopicId > 0L) {
                topicId = TdApi.MessageTopicForum(forumTopicId.toInt())
            }
        })
        // TDLib keeps scheduled messages in a separate server-side queue.
        if (message.schedulingState == null) { history.put(map(message)); hydrateReply(message) }
    }

    /** All scheduled messages in the chat, newest first, straight from TDLib (the only scheduled-message store). */
    suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>> = conversationResult {
        request(TdApi.GetChatScheduledMessages(chatId)).messages.orEmpty().filterNotNull().map(map)
    }

    /** Cancels a queued message. The server owns the queue, so no local copy is kept. */
    suspend fun deleteScheduledMessage(chatId: Long, messageId: Long): Result<Unit> = conversationResult {
        request(TdApi.DeleteMessages(chatId, longArrayOf(messageId), false))
        Unit
    }

    /** Moves a queued message to [atSeconds]; TDLib also moves the rest of its album. */
    suspend fun rescheduleMessage(chatId: Long, messageId: Long, atSeconds: Long): Result<Unit> = conversationResult {
        if (!MessageSendOptions(scheduledAtSeconds = atSeconds).scheduledTimeIsFuture()) {
            throw UserMessageException(UiText.Res(R.string.err_schedule_time_future))
        }
        requireSchedulingEditable(chatId, messageId)
        request(TdApi.EditMessageSchedulingState(chatId, messageId, scheduledState(atSeconds)))
        Unit
    }

    /** Sends a queued message now through TDLib; null scheduling state means immediate delivery. */
    suspend fun sendScheduledNow(chatId: Long, messageId: Long): Result<Unit> = conversationResult {
        requireSchedulingEditable(chatId, messageId)
        request(TdApi.EditMessageSchedulingState(chatId, messageId, null))
        Unit
    }

    private suspend fun requireSchedulingEditable(chatId: Long, messageId: Long) {
        if (!request(TdApi.GetMessageProperties(chatId, messageId)).canEditSchedulingState) {
            throw UserMessageException(UiText.Res(R.string.err_schedule_edit_unavailable))
        }
    }

    suspend fun edit(chatId: Long, messageId: Long, text: String, entities: List<MessageTextEntity>): Result<Unit> = conversationResult {
        val properties = request(TdApi.GetMessageProperties(chatId, messageId))
        val original = request(TdApi.GetMessage(chatId, messageId))
        if (!(properties.canBeEdited && original.isOutgoing && editable(original.content))) throw UserMessageException(UiText.Res(R.string.err_message_cannot_edit))
        val isText = original.content is TdApi.MessageText
        val bounded = boundTelegramText(text, entities, if (isText) limits.value.text else limits.value.caption)
        if (isText && bounded.text.isBlank()) throw UserMessageException(UiText.Res(R.string.err_edit_text_empty))
        // An appended suffix can disappear after clipping. Do not send an unchanged edit:
        // Telegram rejects EditMessageText/Caption with MESSAGE_NOT_MODIFIED.
        val previous = messageFormattedText(original.content)
        if (previous?.text == bounded.text &&
            previous.domainEntities().sortedWith(compareBy({ it.offset }, { it.length })) ==
            bounded.entities.sortedWith(compareBy({ it.offset }, { it.length }))) {
            return@conversationResult Unit
        }
        val message = if (isText) {
            request(TdApi.EditMessageText(chatId, messageId, null, TdApi.InputMessageText().apply { this.text = formattedText(bounded.text, bounded.entities) }))
        } else {
            val above = when (val c = original.content) {
                is TdApi.MessagePhoto -> c.showCaptionAboveMedia
                is TdApi.MessageVideo -> c.showCaptionAboveMedia
                is TdApi.MessageAnimation -> c.showCaptionAboveMedia
                else -> false
            }
            request(TdApi.EditMessageCaption(chatId, messageId, null, formattedText(bounded.text, bounded.entities), above))
        }
        // Editing a scheduled message must not insert it into the visible chat history before it is sent.
        if (message.schedulingState == null) { history.put(map(message)); hydrateReply(message) }
    }
    suspend fun delete(chatId: Long, messageId: Long, deleteScope: DeleteMessageScope): Result<Unit> =
        deleteMessages(chatId, listOf(messageId), deleteScope)

    /**
     * Deletes all [messageIds] with one DeleteMessages request. Every message is checked for the chosen scope first:
     * TDLib applies one revoke flag to the whole batch, so a mixed batch must not silently delete more or less than asked.
     */
    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, deleteScope: DeleteMessageScope): Result<Unit> = conversationResult {
        val ids = messageIds.distinct().sorted()
        require(ids.isNotEmpty()) { "Нет сообщений для удаления" }
        if (ids.size > MAX_MESSAGE_BATCH_SIZE) throw UserMessageException(
            UiText.Res(R.string.err_messages_too_many, listOf(MAX_MESSAGE_BATCH_SIZE))
        )
        val blocked = ids.any { messageId ->
            val properties = request(TdApi.GetMessageProperties(chatId, messageId))
            if (deleteScope == DeleteMessageScope.ForEveryone) !properties.canBeDeletedForAllUsers
            else !properties.canBeDeletedOnlyForSelf
        }
        if (blocked) throw UserMessageException(UiText.Res(
            if (ids.size == 1) R.string.err_delete_option_unavailable else R.string.err_messages_cannot_delete
        ))
        request(deleteMessagesRequest(chatId, ids, deleteScope == DeleteMessageScope.ForEveryone))
        history.delete(chatId, ids)
    }

    /**
     * Pins or unpins [messageId] in its parent chat. A forum message keeps its topic: TDLib pins by message id, while the
     * pinned list is already filtered by topic when read.
     */
    suspend fun pinMessage(chatId: Long, messageId: Long, pinned: Boolean): Result<Unit> = conversationResult {
        val properties = request(TdApi.GetMessageProperties(chatId, messageId))
        if (!canTogglePin(properties.canBePinned)) throw UserMessageException(UiText.Res(R.string.err_message_pin_unavailable))
        request(pinMessageRequest(chatId, messageId, pinned))
        Unit
    }

    /**
     * Пересылка обычная или без автора ([asCopy]). TDLib сам отказывает, если сообщение нельзя переслать
     * или содержимое защищено.
     */
    suspend fun forward(
        fromChatId: Long,
        messageId: Long,
        toChatId: Long,
        asCopy: Boolean = false,
        messageThreadId: Long? = null,
        options: MessageSendOptions = MessageSendOptions()
    ): Result<Unit> = forwardMessages(fromChatId, listOf(messageId), toChatId, asCopy, messageThreadId, options).map { Unit }

    /**
     * One ForwardMessages request for a whole selection. Album members are kept next to each other by the caller, so
     * TDLib can preserve the album. Each message is checked separately: one protected message refuses the whole batch
     * instead of silently forwarding only part of the selection.
     */
    suspend fun forwardMessages(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        asCopy: Boolean = false,
        messageThreadId: Long? = null,
        options: MessageSendOptions = MessageSendOptions()
    ): Result<ForwardedMessages> = conversationResult {
        // TDLib requires strictly increasing IDs and caps one request at 100 messages.
        val ids = messageIds.distinct().sorted()
        require(ids.isNotEmpty()) { "Нет сообщений для пересылки" }
        if (ids.size > MAX_MESSAGE_BATCH_SIZE) throw UserMessageException(
            UiText.Res(R.string.err_messages_too_many, listOf(MAX_MESSAGE_BATCH_SIZE))
        )
        val blocked = ids.any { messageId ->
            !request(TdApi.GetMessageProperties(fromChatId, messageId)).canBeForwarded
        }
        if (blocked) throw UserMessageException(UiText.Res(
            if (ids.size == 1) R.string.err_message_cannot_forward else R.string.err_messages_cannot_forward
        ))
        requireFutureSchedule(options)
        val forwarded = request(
            forwardMessagesRequest(toChatId, fromChatId, ids, asCopy, messageThreadId, sendOptions(options))
        )
        val returned = forwarded.messages.orEmpty()
        val forwardedIds = ids.filterIndexed { index, _ -> returned.getOrNull(index) != null }
        val forwardedSet = forwardedIds.toSet()
        val failedIds = ids.filterNot { it in forwardedSet }
        if (forwardedIds.isEmpty()) throw UserMessageException(UiText.Res(R.string.chat_forward_failed))
        ForwardedMessages(forwardedIds, failedIds)
    }

    /** A Telegram link to the message; comments in a thread need the thread form. */
    suspend fun messageLink(chatId: Long, messageId: Long, inMessageThread: Boolean): Result<String> = conversationResult {
        val link = request(messageLinkRequest(chatId, messageId, inMessageThread)).link
        if (link.isBlank()) throw UserMessageException(UiText.Res(R.string.err_message_link_unavailable))
        link
    }

    /**
     * Reactions the user can add to the message, in the picker's order: TDLib's top, recent and popular reactions, then the chat's
     * own list, or every active emoji when the chat allows all of them. Premium-only ones are left out for users without Premium,
     * and so are custom emoji, which only Premium users can add. Paid reactions are display-only, so the picker never offers them.
     */
    suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<ReactionOption>> = conversationResult<List<ReactionOption>> {
        val available = request(TdApi.GetMessageAvailableReactions(chatId, messageId, PICKER_ROW_SIZE))
        if (available.unavailabilityReason != null) emptyList()
        else pickerOptions(available, request(TdApi.GetChat(chatId)).availableReactions, _activeReactions.value, premium = request(TdApi.GetMe()).isPremium)
    }

    /**
     * Removes [reaction] when the user chose it, otherwise adds it. When the message is at the chat's reaction limit, the first
     * chosen reaction in the server's order makes room, and it comes back if the new reaction can't be added.
     * [addToRecent] is set only for a reaction picked from the extended menu. Returns the message's reactions after the change.
     * A paid reaction is rejected before any request: spending Stars requires the separate confirmation flow that this tap path
     * does not have.
     */
    suspend fun toggleReaction(chatId: Long, messageId: Long, reaction: ReactionOption, addToRecent: Boolean): Result<List<MessageReaction>> = conversationResult<List<MessageReaction>> {
        if (reaction.isPaid) throw UserMessageException(UiText.Res(R.string.chat_reaction_paid_confirmation_required))
        reactionLocks.withLock(chatId to messageId) {
            val chosen = request(TdApi.GetMessage(chatId, messageId)).interactionInfo?.reactions?.reactions.orEmpty()
                .filter { it.isChosen && it.type !is TdApi.ReactionTypePaid }
            val own = chosen.firstOrNull { optionOf(it.type) == reaction }
            if (own != null) {
                request(TdApi.RemoveMessageReaction(chatId, messageId, own.type))
            } else {
                replaceReactions(
                    displaced = overflowReactions(chosen.map { it.type }, maxReactionCount(chatId)),
                    add = { request(TdApi.AddMessageReaction(chatId, messageId, reactionTypeOf(reaction), false, addToRecent)) },
                    remove = { request(TdApi.RemoveMessageReaction(chatId, messageId, it)) },
                    // Возвращённые реакции были выбраны раньше, поэтому в недавние они не попадают.
                    restore = { request(TdApi.AddMessageReaction(chatId, messageId, it, false, false)) },
                )
            }
            val reactions = messageReactions(request(TdApi.GetMessage(chatId, messageId)).interactionInfo)
            history.update(chatId, messageId) { it.copy(reactions = reactions) }
            reactions
        }
    }

    private suspend fun maxReactionCount(chatId: Long): Int {
        val available = request(TdApi.GetChat(chatId)).availableReactions
        val limit = when (available) {
            is TdApi.ChatAvailableReactionsAll -> available.maxReactionCount
            is TdApi.ChatAvailableReactionsSome -> available.maxReactionCount
            else -> 1
        }
        return limit.coerceAtLeast(1)
    }

    /**
     * One page newer than [afterMessageId]. Used when the boundary window contains only outgoing messages and the first
     * unread incoming message is farther ahead.
     */
    suspend fun newerMessages(chatId: Long, forumTopicId: Long, afterMessageId: Long): Result<List<ChatMessage>> = conversationResult {
        if (chatId == 0L || afterMessageId <= 0L) throw UserMessageException(UiText.Res(R.string.err_message_original_unavailable))
        val page = if (forumTopicId > 0L) {
            request(TdApi.GetForumTopicHistory().apply {
                this.chatId = chatId
                this.forumTopicId = forumTopicId.toInt()
                fromMessageId = afterMessageId
                offset = 1
                limit = 50
            })
        } else request(TdApi.GetChatHistory(chatId, afterMessageId, 1, 50, false))
        page.messages.orEmpty().filterNotNull().filter { it.id > afterMessageId }.sortedBy { it.id }.map(map)
    }

    /**
     * Messages around [messageId], newest last. TDLib may return fewer messages, so the target is fetched separately.
     * A read watermark can outlive its message: if the target was deleted, the surrounding page is still usable and is
     * returned instead of failing the whole unread-boundary jump.
     */
    suspend fun window(chatId: Long, messageId: Long): Result<List<TdApi.Message>> = conversationResult {
        if (chatId == 0L || messageId <= 0) throw UserMessageException(UiText.Res(R.string.err_message_original_unavailable))
        val page = request(TdApi.GetChatHistory(chatId, messageId, -15, 40, false)).messages.orEmpty().filterNotNull()
        val target = try {
            request(TdApi.GetMessage(chatId, messageId))
        } catch (error: TdRequestError) {
            if (error.code == 404) null else throw error
        }
        (page + listOfNotNull(target)).distinctBy { it.id }.sortedBy { it.id }
    }

    suspend fun context(chatId: Long, messageId: Long): Result<MessageContext> = conversationResult {
        if (chatId == 0L || messageId <= 0) throw UserMessageException(UiText.Res(R.string.err_message_original_unavailable))
        val target = request(TdApi.GetMessage(chatId, messageId))
        val chat = request(TdApi.GetChat(chatId))
        val page = conversationResult { request(TdApi.GetChatHistory(chatId, messageId, -15, 40, false)) }.getOrNull()
        MessageContext(chatId, chat.title, messageId, (page?.messages.orEmpty().toList() + target).distinctBy { it.id }.sortedBy { it.id }.map(map))
    }

    /**
     * Поиск по всем чатам аккаунта, без фильтра по папке: chatList = null. TDLib отдаёт страницы
     * через nextOffset, который передаётся обратно как есть.
     */
    suspend fun searchGlobal(query: String, offset: String, filter: GlobalSearchFilter): Result<GlobalMessagePage> = requestResult {
        if (query.isBlank()) return@requestResult GlobalMessagePage(emptyList(), "")
        val minDate = filter.period.minDate(System.currentTimeMillis() / 1000) ?: 0
        val found = request(
            TdApi.SearchMessages(
                null,
                query,
                offset,
                SEARCH_PAGE_SIZE,
                filter.kind?.toSearchMessagesFilter(),
                filter.chatType?.let { chatTypeFilter(it) },
                minDate,
                0
            )
        )
        GlobalMessagePage(found.messages.orEmpty().filterNotNull().map(map), found.nextOffset.orEmpty())
    }
    private fun chatTypeFilter(type: GlobalChatType): TdApi.SearchMessagesChatTypeFilter = when (type) {
        GlobalChatType.Private -> TdApi.SearchMessagesChatTypeFilterPrivate()
        GlobalChatType.Group -> TdApi.SearchMessagesChatTypeFilterGroup()
        GlobalChatType.Channel -> TdApi.SearchMessagesChatTypeFilterChannel()
    }

    /**
     * Поиск внутри одного чата: TDLib отдаёт новые сообщения первыми, дальше страницы идут через nextFromMessageId.
     * TDLib применяет отправителя и тип вложения, а диапазон дат фильтруется на каждой странице локально:
     * курсор остаётся тем, что вернул сервер, поэтому страницы не смешиваются.
     */
    suspend fun search(chatId: Long, query: String, fromMessageId: Long, forumTopicId: Long = 0L, filter: ChatSearchFilter = ChatSearchFilter()): Result<MessageSearchPage> = requestResult {
        val found = request(chatSearchRequest(chatId, query, fromMessageId, filter, forumTopicId))
        val messages = found.messages.orEmpty().filterNotNull().map(map).filter(filter::matchesDate)
        MessageSearchPage(messages, found.nextFromMessageId, found.totalCount)
    }

    /**
     * Вкладка профиля и окно просмотрщика: пустой запрос с фильтром вида вложения, новые сообщения первыми. С [newer]
     * страница идёт от [fromMessageId] к более новым: TDLib отдаёт их при отрицательном смещении.
     */
    suspend fun shared(chatId: Long, kind: SharedMediaKind, fromMessageId: Long, newer: Boolean): Result<MessageSearchPage> = requestResult {
        // На одно меньше лимита: повтор [fromMessageId] в ответе окно отсекает по id.
        val offset = if (newer) -(SEARCH_PAGE_SIZE - 1) else 0
        val found = request(TdApi.SearchChatMessages(chatId, null, "", null, fromMessageId, offset, SEARCH_PAGE_SIZE, kind.toSearchMessagesFilter()))
        MessageSearchPage(found.messages.orEmpty().filterNotNull().map(map), found.nextFromMessageId, found.totalCount)
    }

    fun hydrateReply(message: TdApi.Message) {
        val reply = message.replyTo as? TdApi.MessageReplyToMessage ?: return
        val key = message.chatId to message.id
        if (!replyRequests.add(key)) return
        scope.launch {
            try {
                val original = request(TdApi.GetRepliedMessage(message.chatId, message.id))
                val mapped = map(original)
                val preview = MessageReply(original.chatId, original.id, reply.quote?.text?.text?.takeIf { it.isNotBlank() }?.take(180)?.let { UiText.Raw(it) } ?: mapped.preview(), mapped.senderName)
                history.update(message.chatId, message.id) { if (it.reply?.messageId == reply.messageId) it.copy(reply = preview) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: TdRequestError) {
                if (error.code == 404) history.update(message.chatId, message.id) { it.copy(reply = it.reply?.copy(text = UiText.Res(R.string.err_tdlib_message_unavailable), isUnavailable = true)) }
            } finally { replyRequests.remove(key) }
        }
    }
    private fun editable(content: TdApi.MessageContent): Boolean = content is TdApi.MessageText || content is TdApi.MessagePhoto || content is TdApi.MessageVideo || content is TdApi.MessageAnimation || content is TdApi.MessageAudio || content is TdApi.MessageDocument || content is TdApi.MessageVoiceNote
}

private const val TAG = "BeyGramNet"
private const val FORUM_TOPIC_PAGE_SIZE = 100
private const val MAX_FORUM_TOPIC_PAGES = 20
/** Telegram's shortest live-location window offered by the composer. */
private const val LIVE_LOCATION_PERIOD_SECONDS = 15 * 60

private fun PollDraftProblem.message(): UiText = when (this) {
    PollDraftProblem.QuestionEmpty -> UiText.Res(R.string.poll_error_question_empty)
    PollDraftProblem.QuestionTooLong -> UiText.Res(
        R.string.poll_error_question_long, listOf(OutgoingPoll.MAX_POLL_QUESTION_LENGTH)
    )
    PollDraftProblem.TooFewOptions -> UiText.Res(R.string.poll_error_options_few)
    PollDraftProblem.TooManyOptions -> UiText.Res(
        R.string.poll_error_options_many, listOf(OutgoingPoll.MAX_POLL_OPTIONS)
    )
    PollDraftProblem.OptionEmpty -> UiText.Res(R.string.poll_error_option_empty)
    PollDraftProblem.OptionTooLong -> UiText.Res(
        R.string.poll_error_option_long, listOf(OutgoingPoll.MAX_POLL_OPTION_LENGTH)
    )
    PollDraftProblem.CorrectAnswerMissing -> UiText.Res(R.string.poll_error_correct_missing)
    PollDraftProblem.CorrectAnswerInvalid -> UiText.Res(R.string.poll_error_correct_invalid)
    PollDraftProblem.CorrectAnswerCount -> UiText.Res(R.string.poll_error_correct_count)
}

private fun PollOptionAddProblem.message(): UiText = when (this) {
    PollOptionAddProblem.Empty -> UiText.Res(R.string.poll_error_option_empty)
    PollOptionAddProblem.TooLong -> UiText.Res(
        R.string.poll_error_option_long, listOf(OutgoingPoll.MAX_POLL_OPTION_LENGTH)
    )
    PollOptionAddProblem.Duplicate -> UiText.Res(R.string.poll_option_error_duplicate)
    PollOptionAddProblem.LimitReached -> UiText.Res(
        R.string.poll_error_options_many, listOf(OutgoingPoll.MAX_POLL_OPTIONS)
    )
}

/** TDLib scheduling state for a Unix timestamp in seconds; null keeps an immediate send. */
internal fun scheduledState(atSeconds: Long?): TdApi.MessageSchedulingState? =
    atSeconds?.let { TdApi.MessageSchedulingStateSendAtDate(it.toInt(), 0) }

/** Protocol options for an ordinary send, a silent send, or a scheduled one. */
internal fun sendOptions(options: MessageSendOptions): TdApi.MessageSendOptions = TdApi.MessageSendOptions().apply {
    disableNotification = options.disableNotification
    schedulingState = scheduledState(options.scheduledAtSeconds)
}

private fun requireFutureSchedule(options: MessageSendOptions) {
    if (!options.scheduledTimeIsFuture()) throw UserMessageException(UiText.Res(R.string.err_schedule_time_future))
}

/** View-once is TDLib's per-message immediately-self-destructing photo/video. */
internal fun selfDestructType(options: MessageSendOptions): TdApi.MessageSelfDestructType? =
    if (options.viewOnce) TdApi.MessageSelfDestructTypeImmediately() else null

private fun requireViewOnceSupported(media: OutgoingMedia, options: MessageSendOptions) {
    if (!options.viewOnce) return
    if (options.scheduledAtSeconds != null) throw UserMessageException(UiText.Res(R.string.err_view_once_scheduled))
    if (media.kind !in setOf(MediaKind.Photo, MediaKind.Video)) {
        throw UserMessageException(UiText.Res(R.string.err_view_once_media_only))
    }
}

internal fun ChatMessage.preview(): UiText = if (text.isNotBlank()) UiText.Raw(text.take(180)) else when (val content = special) {
    is MessageSpecialContent.Poll -> content.poll.question.takeIf { it.isNotBlank() }
        ?.let { UiText.Res(R.string.chat_preview_poll, listOf(it.take(80))) }
        ?: UiText.Res(R.string.chat_preview_poll_empty)
    is MessageSpecialContent.Location -> UiText.Res(R.string.chat_preview_location)
    is MessageSpecialContent.LiveLocation -> UiText.Res(R.string.chat_preview_live_location)
    is MessageSpecialContent.Venue -> content.venue.title.takeIf { it.isNotBlank() }
        ?.let { UiText.Res(R.string.chat_preview_venue, listOf(it.take(80))) }
        ?: UiText.Res(R.string.chat_preview_location)
    is MessageSpecialContent.Contact -> UiText.Raw(content.contact.displayName.take(180))
    is MessageSpecialContent.Dice -> UiText.Res(R.string.chat_preview_dice, listOf(content.dice.emoji))
    is MessageSpecialContent.Unsupported -> UiText.Res(R.string.message_title)
    null -> UiText.Res(when (media?.kind) {
        MediaKind.Photo -> R.string.media_kind_photo; MediaKind.Video -> R.string.media_kind_video; MediaKind.Animation -> R.string.media_kind_animation; MediaKind.Audio -> R.string.media_kind_audio; MediaKind.Document -> R.string.media_kind_file; MediaKind.Voice -> R.string.media_kind_voice; MediaKind.VideoNote -> R.string.media_kind_video_note; MediaKind.Sticker -> R.string.media_kind_sticker; null -> R.string.message_title
    })
}

internal fun messageLinkRequest(chatId: Long, messageId: Long, inMessageThread: Boolean) =
    TdApi.GetMessageLink(chatId, messageId, 0, 0, "", false, inMessageThread)

internal fun messageThreadRequest(chatId: Long, messageId: Long) = TdApi.GetMessageThread(chatId, messageId)

/** The thread lives in the linked discussion chat; messageId is MessageThreadInfo.messageThreadId, not the channel post. */
internal fun messageThreadHistoryRequest(
    discussionChatId: Long,
    threadId: Long,
    fromMessageId: Long,
    limit: Int = 50
) = TdApi.GetMessageThreadHistory(discussionChatId, threadId, fromMessageId, 0, limit)

/**
 * A comment always belongs to its thread even when it answers another comment. Passing only replyTo would make TDLib
 * infer the topic; the explicit MessageTopicThread keeps the send in the discussion chat's correct thread.
 */
internal fun threadReplyRequest(
    discussionChatId: Long,
    threadId: Long,
    replyToMessageId: Long?,
    content: TdApi.InputMessageContent,
    options: TdApi.MessageSendOptions = TdApi.MessageSendOptions()
) = TdApi.SendMessage().apply {
    chatId = discussionChatId
    topicId = TdApi.MessageTopicThread(threadId)
    replyTo = replyToMessageId?.takeIf { it > 0L }?.let { id ->
        TdApi.InputMessageReplyToMessage().apply {
            messageId = id
            pollOptionId = ""
        }
    }
    this.options = options
    inputMessageContent = content
}

/**
 * TDLib reports disabled comments as "Message has no thread"/"Message has no comments", while an inaccessible linked
 * group says that its comments can't be accessed. Keep both explanations distinct from a retryable network failure.
 */
internal fun threadRequestFailure(error: TdRequestError): UserMessageException {
    val message = error.message.orEmpty()
    return when {
        error.code == 403 || message.contains("CHAT_FORBIDDEN") || message.contains("CHANNEL_PRIVATE") ||
            message.contains("access message comments") ->
            UserMessageException(UiText.Res(R.string.thread_comments_hidden), error)
        error.code == 404 || message.contains("MESSAGE_THREAD") || message.contains("MESSAGE_ID_INVALID") ||
            message.contains("no thread") || message.contains("no comments") ->
            UserMessageException(UiText.Res(R.string.thread_comments_closed), error)
        else -> UserMessageException(UiText.Res(R.string.thread_comments_unavailable), error)
    }
}

private fun TdApi.ChatMemberStatus?.allowsThreadComment(chatPermissions: TdApi.ChatPermissions): Boolean = when (this) {
    null -> chatPermissions.canSendBasicMessages
    is TdApi.ChatMemberStatusCreator -> true
    is TdApi.ChatMemberStatusAdministrator -> true
    is TdApi.ChatMemberStatusMember -> chatPermissions.canSendBasicMessages
    is TdApi.ChatMemberStatusRestricted -> isMember && permissions?.canSendBasicMessages == true
    else -> false
}

internal fun forwardMessagesRequest(
    toChatId: Long,
    fromChatId: Long,
    messageIds: List<Long>,
    asCopy: Boolean,
    messageThreadId: Long? = null,
    options: TdApi.MessageSendOptions = TdApi.MessageSendOptions()
) = TdApi.ForwardMessages(
    toChatId,
    messageThreadId?.takeIf { it > 0L }?.let { TdApi.MessageTopicForum(it.toInt()) },
    fromChatId,
    messageIds.toLongArray(),
    options,
    asCopy,
    false
)

/** Single-message overload kept for shared-media forwarding and focused protocol tests. */
internal fun forwardMessagesRequest(toChatId: Long, fromChatId: Long, messageId: Long, asCopy: Boolean) =
    forwardMessagesRequest(toChatId, fromChatId, listOf(messageId), asCopy)

internal fun deleteMessagesRequest(chatId: Long, messageIds: List<Long>, revoke: Boolean) =
    TdApi.DeleteMessages(chatId, messageIds.toLongArray(), revoke)

internal fun pinnedMessagesRequest(chatId: Long, fromMessageId: Long, forumTopicId: Long = 0L) = TdApi.SearchChatMessages().apply {
    this.chatId = chatId
    topicId = if (forumTopicId > 0L) TdApi.MessageTopicForum(forumTopicId.toInt()) else null
    query = ""
    this.fromMessageId = fromMessageId
    limit = 50
    filter = TdApi.SearchMessagesFilterPinned()
}

/**
 * Telegram has separate pin and unpin requests rather than a toggle. Pinning is silent for chat members and is never
 * limited to the current user. Topic chats pin the message id in the parent chat, so its topic association is preserved.
 */
internal fun pinMessageRequest(chatId: Long, messageId: Long, pinned: Boolean): TdApi.Function<TdApi.Ok> =
    if (pinned) TdApi.PinChatMessage(chatId, messageId, true, false)
    else TdApi.UnpinChatMessage(chatId, messageId)

/** TDLib's documented stop operation: a null location clears the share, not a zero live period. */
internal fun stopLiveLocationRequest(chatId: Long, messageId: Long) = TdApi.EditMessageLiveLocation().apply {
    this.chatId = chatId
    this.messageId = messageId
    replyMarkup = null
    location = null
}

/** Медиа в журнале видны и принятые, и неудачные: текстовые сообщения слишком частые, поэтому там только сбои. */
private fun recordMediaAccepted(kind: String) = recordAppEvent(
    kind = EventKind.TDLIB,
    code = "media_send_accepted",
    text = "Медиа принято к отправке: $kind",
    attrs = mapOf("kind" to kind)
)

/** Пишем каждый сбой отправки. Проверка до запроса (пустой текст, ответ недоступен) без причины TDLib получает local_check. */
private fun recordSendFailure(kind: String, error: Throwable) {
    val cause = error.cause
    val reason = when {
        cause is TdRequestError -> "tdlib_${cause.code}"
        cause == null && error is UserMessageException -> "local_check"
        else -> (cause ?: error).javaClass.simpleName
    }
    recordAppEvent(
        kind = EventKind.TDLIB,
        code = "send_request_failed",
        text = "Отправка не удалась: $kind, причина: $reason",
        severity = Severity.ERROR,
        attrs = mapOf("kind" to kind, "reason" to reason)
    )
}

/** The Telegram server can overlap pages while topic positions change; Compose list keys must be unique. */
internal fun uniqueForumTopics(topics: List<ForumTopic>): List<ForumTopic> =
    topics.distinctBy(ForumTopic::id).sortedByDescending(ForumTopic::order)

/** A blank string means a message exists but has no textual snippet (for example a photo). */
internal fun forumTopicMessagePreview(message: ChatMessage): String =
    message.text.trim().ifBlank {
        message.media?.fileName?.trim().orEmpty().ifBlank { message.serviceText?.trim().orEmpty() }
    }
