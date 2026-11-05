package com.beykus.beygram.data.chatmemory

import com.beykus.beygram.data.settings.ChatMemorySettings
import com.beykus.beygram.data.settings.savesChat

/**
 * Facts about a chat that decide whether Chat Memory may keep its messages at all. These limits hold whatever the
 * settings say: secret chats and chats with protected content are never kept, and neither are chats whose messages
 * expire by the chat's own timer, because keeping them would defeat that timer.
 */
internal data class ChatMemoryChatFacts(
    val isSecret: Boolean,
    val hasProtectedContent: Boolean,
    val hasAutoDeleteTimer: Boolean
)

/** True when Chat Memory may keep messages of [chatId] right now. The feature is off until the user turns it on. */
internal fun chatMemorySaves(settings: ChatMemorySettings, chatId: Long, facts: ChatMemoryChatFacts): Boolean {
    if (!settings.enabled) return false
    if (facts.isSecret || facts.hasProtectedContent || facts.hasAutoDeleteTimer) return false
    return settings.savesChat(chatId)
}
