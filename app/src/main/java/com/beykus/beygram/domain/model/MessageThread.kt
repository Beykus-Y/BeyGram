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
