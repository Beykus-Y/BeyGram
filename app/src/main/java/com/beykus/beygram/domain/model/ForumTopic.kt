package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

/** The server-side icon of a topic: TDLib's 0xRRGGBB colour and an optional custom-emoji id. */
data class ForumTopicIcon(
    val colorArgb: Int = 0,
    val customEmojiId: Long = 0L
) {
    val hasCustomEmoji: Boolean get() = customEmojiId != 0L
}

/**
 * The newest message known for a topic. Its preview uses the same rules as the main chat list, so
 * media, files, stickers, voice notes and service events are not reduced to bare text.
 */
data class ForumTopicLastMessage(
    val messageId: Long = 0L,
    val preview: UiText = UiText.Raw(""),
    /** The same preview as plain text for callers that only need a string; empty for media without a caption. */
    val plainPreview: String = "",
    val senderName: String = "",
    val isOutgoing: Boolean = false,
    val dateSeconds: Long = 0L,
    val sendState: MessageSendState = MessageSendState.Sent,
    val isService: Boolean = false
)

/**
 * A cumulative snapshot of a forum's topics as TDLib returned them. [topics] already contains every
 * page fetched so far, not just the newest one; [totalCount] is the server's approximate count and
 * may include hidden topics. [isComplete] is false while more pages may still exist.
 */
data class ForumTopicsPage(
    val topics: List<ForumTopic>,
    val totalCount: Int = 0,
    val isComplete: Boolean = false
) {
    val loadedCount: Int get() = topics.size
    /** A server count of zero is unknown, not an empty forum, while topics are present. */
    val effectiveTotalCount: Int get() = maxOf(totalCount, loadedCount)
}

/** Тема группы с темами: [id] — идентификатор, который передаётся при отправке в тему. */
data class ForumTopic(
    val id: Long, val title: String,
    val unreadCount: Int = 0, val isPinned: Boolean = false,
    val isClosed: Boolean = false, val canWrite: Boolean = true,
    val canManage: Boolean = false, val canPin: Boolean = false,
    val order: Long = 0L, val lastMessagePreview: String = "",
    val lastReadInboxMessageId: Long = 0L, val lastReadOutboxMessageId: Long = 0L,
    val icon: ForumTopicIcon? = null,
    val isGeneral: Boolean = false,
    val isOutgoing: Boolean = false,
    val isNameImplicit: Boolean = false,
    val creationDate: Int = 0,
    val creatorName: String = "",
    val unreadMentionCount: Int = 0,
    val unreadReactionCount: Int = 0,
    val unreadPollVoteCount: Int = 0,
    /** TDLib's useDefaultMuteFor: a topic inherits the parent group's mute state until overridden. */
    val useDefaultMuteFor: Boolean = true,
    /** Explicit topic mute duration in seconds; only meaningful when [useDefaultMuteFor] is false. */
    val muteFor: Int = 0,
    val hasDraft: Boolean = false,
    /** Short text of the topic draft; empty for a reply-only draft. */
    val draftPreview: String = "",
    val lastMessage: ForumTopicLastMessage? = null
) {
    /** Unread messages, mentions, reactions and poll votes all require attention in the list. */
    val hasUnread: Boolean
        get() = unreadCount > 0 || unreadMentionCount > 0 || unreadReactionCount > 0 || unreadPollVoteCount > 0

    /** The topic is muted when it either inherits a muted group or carries its own mute duration. */
    fun isMutedIn(parentChatMuted: Boolean): Boolean =
        if (useDefaultMuteFor) parentChatMuted else muteFor > 0
}
