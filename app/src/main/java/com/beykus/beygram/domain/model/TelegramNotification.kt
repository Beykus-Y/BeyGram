package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

/** [topicId] is the forum topic of the message, zero outside a forum; a reply from the notice keeps it. */
data class TelegramNotice(val id: Int, val dateSeconds: Long, val sender: String, val text: UiText, val previewAllowed: Boolean, val messageId: Long = 0L, val topicId: Long = 0L)
data class TelegramNoticeGroup(
    val id: Int, val chatId: Long, val title: String = "Telegram", val isGroup: Boolean = false,
    val isSecret: Boolean = false, val totalCount: Int, val notices: List<TelegramNotice>, val alertId: Int = 0, val metadataReady: Boolean = false,
    /** Чат в архиве: уведомления приходят без звука, пока это не включено в настройках архива. */
    val archived: Boolean = false,
    /** Ответ из уведомления возможен: чат не канал и пользователь может писать в него. */
    val replyable: Boolean = false
)
/** [snapshot] is set once TDLib's notification state has arrived, from the startup snapshot or the first group update; only then may tray entries missing from it be cancelled. */
data class TelegramNotifications(val groups: List<TelegramNoticeGroup> = emptyList(), val initialized: Boolean = false, val hasPending: Boolean = false, val snapshot: Boolean = false)
data class NotificationPresentation(val foreground: Boolean = false, val openedChats: Set<Long> = emptySet())
data class NotificationOpenRequest(val token: Long, val chatId: Long? = null, val settings: Boolean = false, val proxy: Boolean = false)
