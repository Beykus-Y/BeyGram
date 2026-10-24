package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ForwardedMessages
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageCapabilities
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSelectionTest {
    private fun message(
        id: Long,
        text: String = "",
        albumId: Long = 0L,
        media: MessageMedia? = null
    ) = ChatMessage(
        id = id,
        chatId = 1L,
        text = text,
        time = "12:00",
        isOutgoing = false,
        dateSeconds = id,
        media = media,
        mediaAlbumId = albumId
    )

    private fun photo(fileId: Int = 10) = MessageMedia(kind = MediaKind.Photo, fileId = fileId)

    @Test fun `selecting one album member selects every loaded album member`() {
        val messages = listOf(
            message(1, albumId = 100L, media = photo(1)),
            message(2, albumId = 100L, media = photo(2)),
            message(3, text = "standalone"),
            message(4, albumId = 200L, media = photo(4))
        )

        assertEquals(setOf(1L, 2L), selectionGroupIds(messages, 1L))
        assertEquals(setOf(1L, 2L), selectionGroupIds(messages, 2L))
        assertEquals(setOf(3L), selectionGroupIds(messages, 3L))
        assertEquals(setOf(4L), selectionGroupIds(messages, 4L))
    }

    @Test fun `unknown message is its own selection group`() {
        assertEquals(setOf(99L), selectionGroupIds(emptyList(), 99L))
    }

    @Test fun `forward ids stay strictly increasing with albums contiguous in history`() {
        val messages = listOf(
            message(1, albumId = 100L, media = photo(1)),
            message(2, albumId = 100L, media = photo(2)),
            message(3, text = "between"),
            message(4, albumId = 200L, media = photo(4)),
            message(5, albumId = 200L, media = photo(5)),
            message(6, text = "unselected")
        )

        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L),
            forwardIdsForSelection(messages, setOf(1L, 2L, 3L, 4L, 5L))
        )
        assertEquals(listOf(2L, 3L), forwardIdsForSelection(messages, setOf(2L, 3L)))
        assertEquals(emptyList<Long>(), forwardIdsForSelection(messages, emptySet()))
    }

    @Test fun `selected text joins nonblank texts in message order`() {
        val messages = listOf(
            message(1, text = " first "),
            message(2, albumId = 100L, media = photo(2)),
            message(3, text = "third")
        )

        assertEquals("first\n\nthird", selectedText(messages, setOf(1L, 2L, 3L)))
        assertEquals("", selectedText(messages, setOf(2L)))
    }

    @Test fun `batch delete needs every message to support the chosen scope`() {
        val selfOnly = MessageCapabilities(canDeleteForSelf = true)
        val both = MessageCapabilities(canDeleteForSelf = true, canDeleteForAll = true)

        assertEquals(BatchDeleteRights(canDeleteForSelf = true, canDeleteForAll = true), batchDeleteRights(listOf(both, both)))
        assertEquals(BatchDeleteRights(canDeleteForSelf = true, canDeleteForAll = false), batchDeleteRights(listOf(both, selfOnly)))
        assertFalse(batchDeleteRights(emptyList()).canDelete)
        assertFalse(batchDeleteRights(listOf(MessageCapabilities())).canDelete)
        assertTrue(batchDeleteRights(listOf(both)).canDelete)
    }

    @Test fun `partial forward keeps only failed ids for retry`() {
        val result = ForwardedMessages(forwardedIds = listOf(1L, 2L), failedIds = listOf(3L))
        assertFalse(result.isComplete)
        assertEquals(listOf(3L), result.failedIds)
        assertTrue(ForwardedMessages(listOf(1L, 2L), emptyList()).isComplete)
    }
}
