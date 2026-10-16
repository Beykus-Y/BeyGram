package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface NotificationRepository {
    val notifications: StateFlow<TelegramNotifications>
    val notificationPresentation: StateFlow<NotificationPresentation>
    /** Removing a tray notification must never mark its message read. */
    suspend fun dismissNotifications(groupId: Int, maxNotificationId: Int): Result<Unit>
    fun observeChat(chatId: Long): StateFlow<ChatPreview?>
    suspend fun loadChat(chatId: Long): Result<Unit>
}
