package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

private const val SEARCH_PAGE_SIZE = 50

internal class TdConversationActions(
    client: () -> Client, private val history: MessageHistoryStore,
    private val map: (TdApi.Message) -> ChatMessage
) {
    private val request = TdRequests(client)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replyRequests = ConcurrentHashMap.newKeySet<Pair<Long, Long>>()
    private val _limits = MutableStateFlow(MessageLimits())
    val limits = _limits.asStateFlow()
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
            canForward = properties.canBeForwarded, canSave = properties.canBeSaved)
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

    suspend fun sendText(chatId: Long, text: String, replyId: Long?): Result<Unit> = conversationResult {
        if (text.isBlank()) throw UserMessageException(UiText.Res(R.string.err_message_empty))
        if (text.codePointCount(0, text.length) > limits.value.text) throw UserMessageException(UiText.Res(R.string.err_message_too_long))
        send(chatId, TdApi.InputMessageText().apply { this.text = TdApi.FormattedText(text, emptyArray()); clearDraft = true }, replyId)
    }
    suspend fun sendMedia(chatId: Long, media: OutgoingMedia, caption: String, replyId: Long?): Result<Unit> = conversationResult {
        if (caption.codePointCount(0, caption.length) > limits.value.caption) throw UserMessageException(UiText.Res(R.string.err_caption_too_long))
        send(chatId, media.toInputContent(caption), replyId)
    }
    private suspend fun send(chatId: Long, content: TdApi.InputMessageContent, replyId: Long?) {
        val replyTarget = replyId?.let {
            if (!request(TdApi.GetMessageProperties(chatId, it)).canBeReplied) throw UserMessageException(UiText.Res(R.string.err_message_cannot_reply))
            request(TdApi.GetMessage(chatId, it))
        }
        val message = request(TdApi.SendMessage().apply {
            this.chatId = chatId; inputMessageContent = content
            if (replyTarget != null) {
                topicId = replyTarget.topicId
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyTarget.id; pollOptionId = "" }
            }
        })
        history.put(map(message)); hydrateReply(message)
    }

    suspend fun edit(chatId: Long, messageId: Long, text: String): Result<Unit> = conversationResult {
        val properties = request(TdApi.GetMessageProperties(chatId, messageId))
        val original = request(TdApi.GetMessage(chatId, messageId))
        if (!(properties.canBeEdited && original.isOutgoing && editable(original.content))) throw UserMessageException(UiText.Res(R.string.err_message_cannot_edit))
        val message = if (original.content is TdApi.MessageText) {
            if (text.isBlank()) throw UserMessageException(UiText.Res(R.string.err_edit_text_empty))
            if (text.codePointCount(0, text.length) > limits.value.text) throw UserMessageException(UiText.Res(R.string.err_edit_text_too_long))
            request(TdApi.EditMessageText(chatId, messageId, null, TdApi.InputMessageText().apply { this.text = TdApi.FormattedText(text, emptyArray()) }))
        } else {
            if (text.codePointCount(0, text.length) > limits.value.caption) throw UserMessageException(UiText.Res(R.string.err_caption_too_long))
            val above = when (val c = original.content) {
                is TdApi.MessagePhoto -> c.showCaptionAboveMedia
                is TdApi.MessageVideo -> c.showCaptionAboveMedia
                is TdApi.MessageAnimation -> c.showCaptionAboveMedia
                else -> false
            }
            request(TdApi.EditMessageCaption(chatId, messageId, null, TdApi.FormattedText(text, emptyArray()), above))
        }
        history.put(map(message)); hydrateReply(message)
    }
    suspend fun delete(chatId: Long, messageId: Long, deleteScope: DeleteMessageScope): Result<Unit> = conversationResult {
        val properties = request(TdApi.GetMessageProperties(chatId, messageId))
        val allowed = if (deleteScope == DeleteMessageScope.ForEveryone) properties.canBeDeletedForAllUsers else properties.canBeDeletedOnlyForSelf
        if (!allowed) throw UserMessageException(UiText.Res(R.string.err_delete_option_unavailable))
        request(TdApi.DeleteMessages(chatId, longArrayOf(messageId), deleteScope == DeleteMessageScope.ForEveryone))
        history.delete(chatId, listOf(messageId))
    }

    /** Пересылка без копии: TDLib сам отказывает, если сообщение нельзя переслать или содержимое защищено. */
    suspend fun forward(fromChatId: Long, messageId: Long, toChatId: Long): Result<Unit> = conversationResult {
        if (!request(TdApi.GetMessageProperties(fromChatId, messageId)).canBeForwarded) throw UserMessageException(UiText.Res(R.string.err_message_cannot_forward))
        val forwarded = request(TdApi.ForwardMessages(toChatId, null, fromChatId, longArrayOf(messageId), TdApi.MessageSendOptions(), false, false))
        if (!forwarded.messages.orEmpty().any { it != null }) throw UserMessageException(UiText.Res(R.string.chat_forward_failed))
    }

    suspend fun context(chatId: Long, messageId: Long): Result<MessageContext> = conversationResult {
        if (chatId == 0L || messageId <= 0) throw UserMessageException(UiText.Res(R.string.err_message_original_unavailable))
        val target = request(TdApi.GetMessage(chatId, messageId))
        val chat = request(TdApi.GetChat(chatId))
        val page = conversationResult { request(TdApi.GetChatHistory(chatId, messageId, -15, 40, false)) }.getOrNull()
        MessageContext(chatId, chat.title, messageId, (page?.messages.orEmpty().toList() + target).distinctBy { it.id }.sortedBy { it.id }.map(map))
    }

    /** Поиск по тексту внутри одного чата: TDLib отдаёт новые сообщения первыми, дальше страницы идут через nextFromMessageId. */
    suspend fun search(chatId: Long, query: String, fromMessageId: Long): Result<MessageSearchPage> = requestResult {
        val found = request(TdApi.SearchChatMessages(chatId, null, query, null, fromMessageId, 0, SEARCH_PAGE_SIZE, null))
        MessageSearchPage(found.messages.orEmpty().map(map), found.nextFromMessageId)
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

internal fun ChatMessage.preview(): UiText = if (text.isNotBlank()) UiText.Raw(text.take(180)) else UiText.Res(when (media?.kind) {
    MediaKind.Photo -> R.string.media_kind_photo; MediaKind.Video -> R.string.media_kind_video; MediaKind.Animation -> R.string.media_kind_animation; MediaKind.Audio -> R.string.media_kind_audio; MediaKind.Document -> R.string.media_kind_file; MediaKind.Voice -> R.string.media_kind_voice; MediaKind.VideoNote -> R.string.media_kind_video_note; MediaKind.Sticker -> R.string.media_kind_sticker; null -> R.string.message_title
})
