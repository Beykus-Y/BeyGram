package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatOrderingTest {
    private fun chat(id: Long, order: Long, pinned: Boolean = false) =
        ChatPreview(id, "chat", "C", "message", "12:00", 0, 0L, isPinned = pinned, order = order)

    @Test fun serverPositionOverridesChatId() {
        assertEquals(listOf(1L, 99L), orderedMainChats(listOf(chat(99L, 1L), chat(1L, 10L))).map { it.id })
    }

    @Test fun pinnedChatsPreserveTheirTelegramOrder() {
        val result = orderedMainChats(listOf(chat(9L, 90L), chat(1L, 10L, true), chat(2L, 20L, true)))
        assertEquals(listOf(2L, 1L, 9L), result.map { it.id })
    }

    @Test fun chatsOutsideTheMainListAreHidden() {
        assertEquals(listOf(1L), orderedMainChats(listOf(chat(2L, 0L, true), chat(1L, 5L))).map { it.id })
    }

    @Test fun newPositionMovesAnExistingChatToTheTop() {
        val old = listOf(chat(1L, 10L), chat(2L, 5L))
        val updated = old.map { if (it.id == 2L) it.copy(order = 20L) else it }
        assertEquals(listOf(2L, 1L), orderedMainChats(updated).map { it.id })
    }

    @Test fun archivedChatsAreOrderedByTheirArchivePosition() {
        val chats = listOf(chat(1L, 0L).copy(archiveOrder = 5L), chat(2L, 0L).copy(archiveOrder = 9L), chat(3L, 7L))
        assertEquals(listOf(2L, 1L), orderedArchivedChats(chats).map { it.id })
    }

    @Test fun archivedChatsAreHiddenFromTheMainList() {
        val archived = chat(1L, 0L).copy(archiveOrder = 5L)
        assertEquals(emptyList<Long>(), orderedMainChats(listOf(archived)).map { it.id })
    }
}
