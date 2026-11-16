package com.beykus.beygram.ui.screens.chats

import android.content.Context

/**
 * Gesture assignments are preferences, not Telegram chat properties. Keep the two directions
 * independent and scope them to the active TDLib account; never copy one account's gestures to another.
 */
internal enum class ChatSwipeActionKind {
    Archive, ReadUnread, Pin, Mute, More;

    companion object {
        fun fromStored(value: String?, fallback: ChatSwipeActionKind): ChatSwipeActionKind =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}

internal data class ChatSwipePreferences(
    val left: ChatSwipeActionKind = ChatSwipeActionKind.Archive,
    val right: ChatSwipeActionKind = ChatSwipeActionKind.ReadUnread
)

internal class ChatSwipeSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "chat_swipe_settings", Context.MODE_PRIVATE
    )

    fun load(accountId: String): ChatSwipePreferences = ChatSwipePreferences(
        left = ChatSwipeActionKind.fromStored(
            preferences.getString("left:$accountId", null),
            ChatSwipeActionKind.Archive
        ),
        right = ChatSwipeActionKind.fromStored(
            preferences.getString("right:$accountId", null),
            ChatSwipeActionKind.ReadUnread
        )
    )

    fun save(accountId: String, value: ChatSwipePreferences) {
        preferences.edit()
            .putString("left:$accountId", value.left.name)
            .putString("right:$accountId", value.right.name)
            .apply()
    }
}
