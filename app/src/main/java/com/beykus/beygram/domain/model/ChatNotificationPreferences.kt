package com.beykus.beygram.domain.model

/** Per-chat notification overrides, mapped from TDLib's ChatNotificationSettings. */
data class ChatNotificationPreferences(
    val useDefaultSound: Boolean = true,
    /** 0 means no custom file; with [useDefaultSound] false it is a silent notification. */
    val soundId: Long = 0L,
    val showPreview: Boolean = true,
    val disableMentionNotifications: Boolean = false
)

/** A notification sound already saved in the Telegram account, available for per-chat selection. */
data class NotificationSoundOption(val id: Long, val title: String)
