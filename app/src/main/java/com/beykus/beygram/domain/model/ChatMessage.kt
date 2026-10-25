package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class ChatMessage(
    val id: Long,
    val chatId: Long,
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
    val dateSeconds: Long = 0L,
    val sendState: MessageSendState = MessageSendState.Sent,
    val media: MessageMedia? = null,
    /** Telegram's mediaAlbumId; zero means this message is not part of an album. */
    val mediaAlbumId: Long = 0L,
    val senderName: String = "",
    /** Telegram user id of the sender when TDLib reports a user; zero for channel posts and system events. */
    val senderId: Long = 0L,
    val reply: MessageReply? = null,
    val entities: List<MessageTextEntity> = emptyList(),
    val inlineKeyboard: List<List<BotButton>> = emptyList(),
    val isEdited: Boolean = false,
    val reactions: List<MessageReaction> = emptyList(),
    /** Text of a Telegram service event; unlike a regular chat message it renders as a centered event chip. */
    val serviceText: String? = null,
    /** TDLib call event: render separately from ordinary service messages. */
    val isCallEvent: Boolean = false,
    /** User referred to by a service action, if available, for clickable profile navigation. */
    val serviceUserId: Long = 0L,
    /** TDLib forum topic id for opening a global search hit in its original topic. */
    val forumTopicId: Long = 0L,
    /** TDLib MessageTopicThread id for a channel-post comment; zero for ordinary/forum messages. */
    val messageThreadId: Long = 0L,
    /** The message is pinned in its chat (TDLib's Message.isPinned). */
    val isPinned: Boolean = false,
    /** TDLib's Message.isChannelPost: true only for an original post in a broadcast channel. */
    val isChannelPost: Boolean = false,
    /** TDLib's MessageInteractionInfo.replyInfo.replyCount: comments or replies under this message. */
    val replyCount: Int = 0,
    /** Web-page metadata for a text message link; never populated in secret chats. */
    val linkPreview: MessageLinkPreview? = null,
    /** Unix timestamp when a scheduled message will be sent; null for a normal message. */
    val scheduledAtSeconds: Long? = null,
    /** TDLib's per-message self-destruct type; distinct from the chat's messageAutoDeleteTime TTL. */
    val selfDestruct: MessageSelfDestruct? = null,
    /** Seconds left before this message self-destructs; null when no self-destruct timer runs. */
    val selfDestructInSeconds: Long? = null,
    /** Seconds left before the chat's TTL deletes this message; null when no chat TTL applies. */
    val autoDeleteInSeconds: Long? = null,
    /** TDLib replaced an expired self-destructing attachment with MessageExpired*; the bubble shows a placeholder. */
    val expiredMediaKind: MediaKind? = null
) {
    /** View-once media is the per-message "open once" type, not every attachment in a secret chat. */
    val isViewOnce: Boolean get() = selfDestruct is MessageSelfDestruct.Immediately
}

/**
 * The compact web-page card TDLib attaches to a text message. All content is server-supplied metadata; [photoFileId]
 * points at a small photo size already registered in the media store, not at the page's original image.
 */
data class MessageLinkPreview(
    /** The original destination TDLib resolved; may differ from the human-readable [displayUrl]. */
    val url: String,
    val displayUrl: String,
    val siteName: String = "",
    val title: String = "",
    val description: String = "",
    val photoFileId: Int? = null,
    val photoWidth: Int = 0,
    val photoHeight: Int = 0
) {
    /** A card with no text at all would render as an empty box, so it is not shown. */
    val hasContent: Boolean
        get() = url.isNotBlank() || displayUrl.isNotBlank() || siteName.isNotBlank() || title.isNotBlank() || description.isNotBlank()
}

/**
 * An emoji reaction under a message. [isChosen] marks the reaction the current user added. A custom emoji reaction has a
 * [customEmojiId] and no emoji text; its sticker is looked up by that id.
 */
data class MessageReaction(val emoji: String, val count: Int, val isChosen: Boolean = false, val customEmojiId: Long = 0L) {
    val option: ReactionOption get() = ReactionOption(emoji, customEmojiId)
}

/** A reaction the user can add: a standard [emoji], or the custom emoji with [customEmojiId], which has no emoji text. */
data class ReactionOption(val emoji: String, val customEmojiId: Long = 0L)

/** A custom emoji sticker: its file, the sticker's [mimeType] as for other stickers, and the standard [emoji] it stands for. */
data class CustomEmoji(val customEmojiId: Long, val fileId: Int, val emoji: String, val mimeType: String)

/** The message that carries an album's reactions: the first one that has any, otherwise the latest. A single message is its own. */
fun reactionMessage(album: List<ChatMessage>): ChatMessage {
    val chronological = album.sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.id })
    return chronological.firstOrNull { it.reactions.isNotEmpty() } ?: chronological.last()
}

enum class MessageSendState { Sending, Sent, Failed }

data class ChatHistory(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val hasOlderMessages: Boolean = true,
    /** True while the messages are a window around a jumped-to message, so newer messages are not loaded yet. */
    val hasNewerMessages: Boolean = false,
    val error: UiText? = null,
    val lastReadInboxMessageId: Long = 0L,
    val lastReadOutboxMessageId: Long = 0L,
    /**
     * Messages inserted by live TDLib updates, oldest first and bounded by the history store. Loading and refreshing
     * history never adds entries, so the UI can animate only genuinely new messages and never a page of old ones.
     */
    val liveArrivals: List<LiveMessageArrival> = emptyList(),
    val liveArrivalSequence: Long = 0L,
    /** Recent temporary-to-server ID replacements, bounded by the history store. */
    val recentReplacements: List<MessageIdReplacement> = emptyList(),
    /**
     * Messages loaded outside the window, such as older comments of a channel post's thread. Updates and deletions reach
     * them like [messages], but they are never part of [messages], so the chat timeline and its paging do not show them.
     */
    val outsideWindowMessages: List<ChatMessage> = emptyList()
)

/** One live insertion, ordered by [sequence] within a history store. A temporary-to-server replacement is not an arrival. */
data class LiveMessageArrival(val sequence: Long, val messageId: Long)

/** A server confirmation moved a visible temporary message to [newId]. */
data class MessageIdReplacement(val oldId: Long, val newId: Long)

/**
 * A TDLib translation of a message's text. [languageCode] is the requested target language, not a guess based on the
 * translated content. Formatting stays in domain entities so the renderer can keep links, quotes and styles.
 */
data class MessageTranslation(
    val text: String,
    val languageCode: String,
    val entities: List<MessageTextEntity> = emptyList()
)

data class MessageReply(val chatId: Long, val messageId: Long, val text: UiText, val senderName: String = "", val isUnavailable: Boolean = false)
data class MessageCapabilities(
    val canReply: Boolean = false,
    val canCopy: Boolean = false,
    val canEdit: Boolean = false,
    val canDeleteForSelf: Boolean = false,
    val canDeleteForAll: Boolean = false,
    val canForward: Boolean = false,
    val canSave: Boolean = false,
    /** TDLib can produce a Telegram link for this message; private or protected messages may refuse one. */
    val canGetLink: Boolean = false,
    /** TDLib's canBePinned: both pinning and unpinning need it. */
    val canPin: Boolean = false
)

/**
 * TDLib's canBePinned covers both pinning and unpinning. An existing pin must not be treated as standing permission:
 * a channel subscriber or group member without pin-management rights cannot unpin either.
 */
fun canTogglePin(canPin: Boolean): Boolean = canPin
enum class DeleteMessageScope { ForSelf, ForEveryone }
data class MessageContext(val chatId: Long, val title: String, val targetId: Long, val messages: List<ChatMessage>)
/**
 * One page of search results. [totalCount] is TDLib's count for the server-side text/sender/attachment query;
 * a client-side date range can hide some of those rows, so callers must not present it as an exact in-range count.
 * [hasMore] stays true even when every row on this page was hidden by the local date filter.
 */
data class MessageSearchPage(val messages: List<ChatMessage>, val nextFromMessageId: Long, val totalCount: Int = 0) {
    val hasMore: Boolean get() = nextFromMessageId != 0L
}

/** Страница общего поиска по всем чатам аккаунта. [nextOffset] пустой, когда страницы кончились. */
data class GlobalMessagePage(
    val messages: List<ChatMessage>,
    val nextOffset: String,
    /** Cached or lazily resolved chat details, keyed by TDLib chatId. */
    val chats: Map<Long, ChatPreview> = emptyMap()
)
data class MessageLimits(val text: Int = 4096, val caption: Int = 1024)

data class PinnedMessagePage(val messages: List<ChatMessage>, val totalCount: Int, val nextFromMessageId: Long)
