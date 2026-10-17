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
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    /** TDLib main-list position; zero means the chat is outside the main list. */
    val order: Long = 0L,
    /** TDLib archive-list position; zero means the chat is not archived. */
    val archiveOrder: Long = 0L,
    /** Id of the last message, used to mark the chat as read; zero when unknown. */
    val lastMessageId: Long = 0L,
    val photo: AvatarPhoto? = null
)

fun orderedMainChats(chats: Collection<ChatPreview>): List<ChatPreview> =
    chats.filter { it.order > 0L }.sortedWith(
        compareByDescending<ChatPreview> { it.isPinned }.thenByDescending { it.order }.thenByDescending { it.id }
    )

fun orderedArchivedChats(chats: Collection<ChatPreview>): List<ChatPreview> =
    chats.filter { it.archiveOrder > 0L }.sortedWith(
        compareByDescending<ChatPreview> { it.archiveOrder }.thenByDescending { it.id }
    )
