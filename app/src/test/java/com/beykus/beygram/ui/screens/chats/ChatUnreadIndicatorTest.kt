package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUnreadIndicatorTest {
    private fun chat(unreadCount: Int, marked: Boolean = false) = ChatPreview(
        id = 1L, title = "Chat", initials = "C", lastMessage = UiText.Raw(""), lastMessageTime = "",
        unreadCount = unreadCount, avatarColorArgb = 0L, isMarkedAsUnread = marked
    )

    @Test fun serverUnreadCountShowsTheUsualBadge() {
        assertTrue(showsUnreadIndicator(chat(unreadCount = 3)))
    }

    @Test fun manualMarkerShowsAnUnreadDotWithoutMessages() {
        assertTrue(showsUnreadIndicator(chat(unreadCount = 0, marked = true)))
    }

    @Test fun readChatWithoutMarkerLooksRead() {
        assertFalse(showsUnreadIndicator(chat(unreadCount = 0)))
    }
}
