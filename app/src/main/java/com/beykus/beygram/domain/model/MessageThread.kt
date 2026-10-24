package com.beykus.beygram.domain.model

/**
 * A channel post's discussion thread. TDLib keeps the comments in the channel's linked supergroup, so the thread has
 * its own chat and thread id independent of the channel post that opened it.
 */
data class MessageThread(
    val channelChatId: Long,
    val channelMessageId: Long,
    val discussionChatId: Long,
    val threadId: Long,
    val discussionTitle: String = "",
    /** Telegram's reply count for the post; it includes replies to comments as well. */
    val replyCount: Int = 0,
    val unreadCount: Int = 0,
    /** False when the discussion group is read-only for this account or comments are closed. */
    val canReply: Boolean = false,
    /** The newest comments, oldest first. Older pages are loaded with [MessageThreadPage.nextFromMessageId]. */
    val messages: List<ChatMessage> = emptyList(),
    val hasOlderMessages: Boolean = false
)

/** One older page of comments, also returned oldest first so the renderer can prepend it without reordering. */
data class MessageThreadPage(
    val messages: List<ChatMessage>,
    /** Oldest id on this page; pass it as the next cursor. Zero when no older page exists. */
    val nextFromMessageId: Long,
    val hasMore: Boolean
)

/**
 * The comments affordance belongs only to original channel posts whose channel has a linked discussion group.
 * A linked group can still reject a specific post, which is explained when the thread is requested.
 */
fun showsChannelComments(isChannel: Boolean, isChannelPost: Boolean, linkedDiscussionChatId: Long): Boolean =
    isChannel && isChannelPost && linkedDiscussionChatId != 0L

/**
 * Composers are hidden when TDLib says the thread cannot be opened, when the post has no reply info (comments closed),
 * when the account is no longer a member, or when the linked group restricts basic messages.
 */
fun canWriteThreadComment(
    canGetMessageThread: Boolean,
    hasReplyInfo: Boolean,
    isMember: Boolean,
    canSendBasicMessages: Boolean
): Boolean = canGetMessageThread && hasReplyInfo && isMember && canSendBasicMessages

/**
 * Merges an older or newly sent comment page into the thread list. TDLib can repeat a boundary message, so ids are
 * unique and the list always stays oldest-first for the reverse-layout thread list.
 */
fun mergeThreadMessages(current: List<ChatMessage>, page: List<ChatMessage>): List<ChatMessage> =
    (page + current).distinctBy { it.id }.sortedBy { it.id }

/**
 * Applies one snapshot of the discussion chat's history to a thread list. The comments of [threadId] from the window and
 * from the loaded pages are merged in. A temporary comment is dropped once TDLib confirmed it under a server id that is
 * present in the snapshot, so the list shows one bubble per comment instead of the temporary one next to the confirmed one.
 */
fun mergeThreadHistory(current: List<ChatMessage>, history: ChatHistory, chatId: Long, threadId: Long): List<ChatMessage> {
    // The thread root is the group's automatic copy of the post: it is already shown at the top.
    val fresh = (history.messages + history.outsideWindowMessages).filter {
        it.messageThreadId == threadId && !it.isChannelPost && it.id != threadId
    }
    val freshIds = fresh.mapTo(HashSet<Long>()) { it.id }
    val confirmed = history.recentReplacements.filter { it.newId in freshIds }.mapTo(HashSet<Long>()) { it.oldId }
    val kept = if (confirmed.isEmpty()) current else current.filterNot { it.chatId == chatId && it.id in confirmed }
    return mergeThreadMessages(kept, fresh)
}
