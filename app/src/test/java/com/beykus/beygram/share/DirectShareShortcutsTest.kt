package com.beykus.beygram.share

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectShareShortcutsTest {
    private fun chat(id: Long, order: Long, post: Boolean = true) =
        ChatPreview(id = id, title = "Chat $id", initials = "C", lastMessage = UiText.Raw(""),
            lastMessageTime = "", unreadCount = 0, avatarColorArgb = 0L, order = order, canPost = post)

    @Test fun directSharePicksFourRecentWritableChats() {
        val chats = (1L..6L).map { chat(it, it * 100L, post = it != 5L) } + chat(7, 0)
        assertEquals(listOf(6L, 4L, 3L, 2L), DirectShareShortcuts.pickTargets(chats).map { it.id })
    }

    @Test fun shortcutIdMustUseOurPrefixAndValidNonzeroChatId() {
        assertEquals(123L, DirectShareShortcuts.chatIdForShortcut(DirectShareShortcuts.idForChat(123)))
        assertEquals(0L, DirectShareShortcuts.chatIdForShortcut("other-123"))
        assertEquals(0L, DirectShareShortcuts.chatIdForShortcut("beygram-share-chat-0"))
        assertEquals(0L, DirectShareShortcuts.chatIdForShortcut("beygram-share-chat-abc"))
    }
}
