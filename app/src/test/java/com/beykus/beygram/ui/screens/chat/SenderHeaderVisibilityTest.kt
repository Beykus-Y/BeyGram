package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class SenderHeaderVisibilityTest {
    private val group = ChatPreview(
        id = 1L, title = "Group", initials = "G", lastMessage = UiText.Raw(""), lastMessageTime = "",
        unreadCount = 0, avatarColorArgb = 0L, isGroup = true
    )

    private fun message(id: Long, senderId: Long, senderName: String, albumId: Long = 0L) = ChatMessage(
        id = id, chatId = 1L, text = "", time = "", isOutgoing = false, dateSeconds = id,
        senderId = senderId, senderName = senderName, mediaAlbumId = albumId
    )

    @Test
    fun consecutiveAuthorsAndAlbumsShareOneHeaderInTheNewestFirstFeed() {
        val bobOld = message(1L, 43L, "Bob")
        val aliceFirst = message(2L, 42L, "Alice")
        val album = listOf(message(3L, 42L, "Alice", albumId = 7L), message(4L, 42L, "Alice", albumId = 7L))
        val aliceLast = message(5L, 42L, "Alice")
        val bobNew = message(6L, 43L, "Bob")
        val items = listOf(
            ChatMessageListItem("message:6", listOf(bobNew)),
            ChatMessageListItem("message:5", listOf(aliceLast)),
            ChatMessageListItem("album:7", album, mediaAlbumId = 7L),
            ChatMessageListItem("message:2", listOf(aliceFirst)),
            ChatMessageListItem(UNREAD_SEPARATOR_KEY, emptyList(), unreadSeparator = true),
            ChatMessageListItem("message:1", listOf(bobOld))
        )

        val headers = senderHeaderVisibility(group, items)

        assertEquals(true, headers["message:1"])
        assertEquals(true, headers["message:2"])
        assertEquals(false, headers["album:7"])
        assertEquals(false, headers["message:5"])
        assertEquals(true, headers["message:6"])
    }

    @Test
    fun privateFeedNeverShowsAHeaderEvenAcrossRows() {
        val private = group.copy(isGroup = false)
        val items = listOf(ChatMessageListItem("message:1", listOf(message(1L, 42L, "Alice"))))
        assertEquals(false, senderHeaderVisibility(private, items)["message:1"])
    }
}
