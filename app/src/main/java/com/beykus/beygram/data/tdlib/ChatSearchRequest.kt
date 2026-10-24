package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.model.SharedMediaKind
import org.drinkless.tdlib.TdApi

internal const val SEARCH_PAGE_SIZE = 50

/**
 * Request for one page of in-chat search. Sender and attachment type are expressed through TDLib's own
 * fields; the date range is intentionally absent because SearchChatMessages has no such parameter.
 * [fromMessageId] is the cursor returned by the previous page, so client-side date filtering does not
 * disturb pagination.
 */
internal fun chatSearchRequest(
    chatId: Long,
    query: String,
    fromMessageId: Long,
    filter: ChatSearchFilter,
    forumTopicId: Long = 0L,
    offset: Int = 0
): TdApi.SearchChatMessages {
    val sender = filter.senderId?.takeIf { it > 0L }?.let { TdApi.MessageSenderUser(it) }
    val kindFilter = filter.kind?.toSearchMessagesFilter()
    return TdApi.SearchChatMessages().apply {
        this.chatId = chatId
        topicId = if (forumTopicId > 0L) TdApi.MessageTopicForum(forumTopicId.toInt()) else null
        this.query = query
        senderId = sender
        this.fromMessageId = fromMessageId
        this.offset = offset
        limit = SEARCH_PAGE_SIZE
        this.filter = kindFilter
    }
}

/** Maps the chat search attachment tabs to the same filters used by shared-media pages. */
internal fun SharedMediaKind.toSearchMessagesFilter(): TdApi.SearchMessagesFilter = when (this) {
    SharedMediaKind.Media -> TdApi.SearchMessagesFilterPhotoAndVideo()
    SharedMediaKind.Files -> TdApi.SearchMessagesFilterDocument()
    SharedMediaKind.Links -> TdApi.SearchMessagesFilterUrl()
    SharedMediaKind.Voice -> TdApi.SearchMessagesFilterVoiceNote()
    SharedMediaKind.Music -> TdApi.SearchMessagesFilterAudio()
}
