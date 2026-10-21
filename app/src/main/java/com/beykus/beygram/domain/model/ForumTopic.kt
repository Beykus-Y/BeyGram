package com.beykus.beygram.domain.model

/** Тема группы с темами: [id] — идентификатор, который передаётся при отправке в тему. */
data class ForumTopic(
    val id: Long, val title: String,
    val unreadCount: Int = 0, val isPinned: Boolean = false,
    val isClosed: Boolean = false, val canWrite: Boolean = true,
    val order: Long = 0L, val lastMessagePreview: String = "",
    val lastReadInboxMessageId: Long = 0L, val lastReadOutboxMessageId: Long = 0L
)
