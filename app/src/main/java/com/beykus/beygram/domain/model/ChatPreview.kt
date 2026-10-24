package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class ChatPreview(
    val id: Long,
    val title: String,
    val initials: String,
    val lastMessage: UiText,
    val lastMessageTime: String,
    val unreadCount: Int,
    val avatarColorArgb: Long,
    val isGroup: Boolean = false,
    /** Broadcast channels are supergroups in TDLib but need a distinct search label. */
    val isChannel: Boolean = false,
    /** TDLib ChatTypeSecret: no translation, scheduling or server-side link-preview metadata. */
    val isSecret: Boolean = false,
    /** Чат «Избранное»: личный чат аккаунта с самим собой. Его название и значок задаёт интерфейс. */
    val isSavedMessages: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    /** The user explicitly marked a read chat as unread; it shows a dot even when Telegram's unread count is zero. */
    val isMarkedAsUnread: Boolean = false,
    /** False when the user may not write here: a channel they do not administer, or a group that restricts them. */
    val canPost: Boolean = true,
    /** True for a channel owner or administrator, independently of the separate permission to post messages. */
    val isChannelAdmin: Boolean = false,
    /** TDLib main-list position; zero means the chat is outside the main list. */
    val order: Long = 0L,
    /** TDLib archive-list position; zero means the chat is not archived. */
    val archiveOrder: Long = 0L,
    /** Id of the last message, used to mark the chat as read; zero when unknown. */
    val lastMessageId: Long = 0L,
    val lastMessageOutgoing: Boolean = false,
    /** Имя отправителя последнего сообщения; пусто, если TDLib его не знает. */
    val lastMessageSender: String = "",
    val lastMessageSendState: MessageSendState = MessageSendState.Sent,
    /** Last outgoing message the other side has read; zero when unknown. */
    val lastReadOutboxMessageId: Long = 0L,
    val photo: AvatarPhoto? = null,
    /** TDLib ChatListFolder positions, keyed by server folder ID; independent of Main/Archive. */
    val folderPositions: Map<Int, FolderChatPosition> = emptyMap()
)

fun orderedMainChats(chats: Collection<ChatPreview>): List<ChatPreview> =
    chats.filter { it.order > 0L }.sortedWith(
        compareByDescending<ChatPreview> { it.isPinned }.thenByDescending { it.order }.thenByDescending { it.id }
    )

fun orderedArchivedChats(chats: Collection<ChatPreview>): List<ChatPreview> =
    chats.filter { it.archiveOrder > 0L }.sortedWith(
        compareByDescending<ChatPreview> { it.archiveOrder }.thenByDescending { it.id }
    )
