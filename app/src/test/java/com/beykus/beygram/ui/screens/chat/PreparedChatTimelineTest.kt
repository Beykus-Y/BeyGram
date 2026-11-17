package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedChatTimelineTest {
    private fun message(id: Long, albumId: Long = 0L, senderId: Long = 0L, senderName: String = "") = ChatMessage(
        id = id, chatId = 1L, text = "message $id", time = "", isOutgoing = false, dateSeconds = id,
        mediaAlbumId = albumId, senderId = senderId, senderName = senderName,
        media = if (albumId != 0L) MessageMedia(MediaKind.Photo, fileId = id.toInt()) else null
    )

    private fun chat(isGroup: Boolean = false, isChannel: Boolean = false, isSecret: Boolean = false, isSavedMessages: Boolean = false) = ChatPreview(
        id = 1L, title = "Chat", initials = "C", lastMessage = UiText.Raw(""), lastMessageTime = "", unreadCount = 0,
        avatarColorArgb = 0L, isGroup = isGroup, isChannel = isChannel, isSecret = isSecret, isSavedMessages = isSavedMessages
    )

    @Test fun onlyPrivateChatsShowWaveCards() {
        assertTrue(isWaveChat(chat()))
        assertFalse(isWaveChat(chat(isGroup = true)))
        assertFalse(isWaveChat(chat(isChannel = true)))
        assertFalse(isWaveChat(chat(isSecret = true)))
        assertFalse(isWaveChat(chat(isSavedMessages = true)))
    }

    @Test fun preparedRowsEqualTheGrouperOnTheSameMessages() = runBlocking {
        val messages = listOf(
            message(1L), message(2L, albumId = 7L), message(3L, albumId = 7L),
            message(4L, senderId = 2L, senderName = "A"), message(5L, senderId = 2L, senderName = "A")
        )
        for (waveChat in listOf(true, false)) {
            val history = ChatHistory(messages = messages)
            val prepared = MutableStateFlow(history).preparedTimeline(waveChat).first()
            // A JVM test cannot decode a BG Wave signal, so the filter removes nothing here; the expected rows use it anyway.
            val expectedMessages = if (waveChat) messages.filterNot(::isBgWaveSignalMessage) else messages
            assertSame(history, prepared.history)
            assertEquals(waveChat, prepared.waveFiltered)
            assertEquals(ChatTimelineGrouper().group(expectedMessages).items, prepared.rows.items)
        }
    }

    @Test fun theNewestHistoryIsTheLastOneShown() = runBlocking {
        val source = MutableStateFlow(ChatHistory(messages = listOf(message(1L))))
        val shown = MutableStateFlow<PreparedChatTimeline?>(null)
        val collector = launch(Dispatchers.Default) {
            source.preparedTimeline(waveChat = false).collect { shown.value = it }
        }
        val snapshots = (2..40).map { size -> ChatHistory(messages = List(size) { message(it + 1L) }) }
        // A burst of updates while the grouping runs in the background; intermediate snapshots may be skipped.
        for (snapshot in snapshots) source.value = snapshot
        val finalHistory = snapshots.last()
        try {
            val result = withTimeout(5_000) { shown.filterNotNull().first { it.history === finalHistory } }
            assertEquals(ChatTimelineGrouper().group(finalHistory.messages).items, result.rows.items)
        } finally {
            collector.cancel()
        }
    }
}
