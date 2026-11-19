package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollAnchorTest {
    private fun message(id: Long, outgoing: Boolean = false) = ChatMessage(id = id, chatId = 1L, text = "", time = "", isOutgoing = outgoing)
    private fun row(id: Long) = ChatMessageListItem("message:$id", listOf(message(id)))
    private fun anchor(key: String, messageId: Long, offset: Int = 0, atBottom: Boolean = false, savedAtMs: Long = 1_000L) =
        ChatScrollAnchor(key, messageId, offset, atBottom, savedAtMs)

    // Newest first: index 0 is the bottom.
    private val items = listOf(row(50), row(40), row(30), row(20))

    @Test fun sameKeyWinsOverMessageId() {
        assertEquals(2, resolveAnchorIndex(items, anchor("message:30", 30)))
    }

    @Test fun goneKeyResolvesToItemContainingMessageId() {
        val album = ChatMessageListItem("album:9", listOf(message(35), message(36)), 9L)
        val list = listOf(row(50), album, row(20))
        assertEquals(1, resolveAnchorIndex(list, anchor("message:36", 36)))
    }

    @Test fun deletedAnchorFallsBackToNearestOlderNeighbour() {
        // Message 35 was deleted: the nearest older row is message 30, at index 2.
        assertEquals(2, resolveAnchorIndex(items, anchor("message:35", 35)))
    }

    @Test fun nothingOlderFallsBackToLastIndex() {
        // Anchor 5 is older than every row, so the oldest row is the closest one.
        assertEquals(items.lastIndex, resolveAnchorIndex(items, anchor("message:5", 5)))
    }

    @Test fun atBottomAnchorReturnsNull() {
        assertNull(resolveAnchorIndex(items, anchor("message:50", 50, atBottom = true)))
    }

    @Test fun emptyItemsReturnNull() {
        assertNull(resolveAnchorIndex(emptyList(), anchor("message:50", 50)))
    }

    @Test fun keyWithoutMessageIdReturnsNullInsteadOfOldest() {
        val separatorAnchor = anchor(UNREAD_SEPARATOR_KEY, 0L)
        assertNull(resolveAnchorIndex(items, separatorAnchor))
    }

    @Test fun pendingRowsWithNegativeIdsAreNotTakenAsOlderNeighbours() {
        val pending = ChatMessageListItem("pending-text:1", listOf(message(-1)))
        val list = listOf(pending, row(40), row(30))
        assertEquals(2, resolveAnchorIndex(list, anchor("message:35", 35)))
    }

    @Test fun freshnessBoundaries() {
        val saved = anchor("message:1", 1, savedAtMs = 1_000L)
        assertTrue(saved.isFresh(1_000L))
        assertTrue(saved.isFresh(1_000L + CHAT_SCROLL_ANCHOR_TTL_MS))
        assertFalse(saved.isFresh(1_000L + CHAT_SCROLL_ANCHOR_TTL_MS + 1))
        // A clock that moved backwards is not trusted.
        assertFalse(saved.isFresh(999L))
    }

    @Test fun anchorOfFirstRowAtBottomThreshold() {
        val atEdge = chatScrollAnchorOf(items, 0, 48, bottomThresholdPx = 48, nowMs = 7L)
        assertTrue(atEdge!!.atBottom)
        assertEquals("message:50", atEdge.key)
        assertEquals(50L, atEdge.messageId)
        assertEquals(7L, atEdge.savedAtMs)

        assertFalse(chatScrollAnchorOf(items, 0, 49, bottomThresholdPx = 48, nowMs = 7L)!!.atBottom)
        assertFalse(chatScrollAnchorOf(items, 1, 0, bottomThresholdPx = 48, nowMs = 7L)!!.atBottom)
    }

    @Test fun anchorOfAlbumUsesNewestMemberId() {
        val album = ChatMessageListItem("album:9", listOf(message(35), message(36)), 9L)
        val anchor = chatScrollAnchorOf(listOf(album), 0, 120, bottomThresholdPx = 48, nowMs = 0L)!!
        assertEquals(36L, anchor.messageId)
        assertFalse(anchor.atBottom)
    }

    @Test fun anchorOfMissingRowIsNull() {
        assertNull(chatScrollAnchorOf(emptyList(), 0, 0, bottomThresholdPx = 48, nowMs = 0L))
        assertNull(chatScrollAnchorOf(items, 9, 0, bottomThresholdPx = 48, nowMs = 0L))
    }

    @Test fun incomingWhileReadingHistoryDoesNotFollow() {
        assertFalse(shouldFollowNewest(wasAtBottom = false, userScrolling = false, newestOutgoing = false, localSendPending = false, hasNewerMessages = false))
    }

    @Test fun outgoingFromAnotherDeviceWhileReadingHistoryDoesNotFollow() {
        assertFalse(shouldFollowNewest(wasAtBottom = false, userScrolling = false, newestOutgoing = true, localSendPending = false, hasNewerMessages = false))
    }

    @Test fun localSendFollowsEvenWhenReadingHistory() {
        assertTrue(shouldFollowNewest(wasAtBottom = false, userScrolling = false, newestOutgoing = true, localSendPending = true, hasNewerMessages = false))
    }

    @Test fun incomingAtBottomFollows() {
        assertTrue(shouldFollowNewest(wasAtBottom = true, userScrolling = false, newestOutgoing = false, localSendPending = false, hasNewerMessages = false))
    }

    @Test fun atBottomButUserScrollingDoesNotFollow() {
        assertFalse(shouldFollowNewest(wasAtBottom = true, userScrolling = true, newestOutgoing = false, localSendPending = false, hasNewerMessages = false))
    }

    @Test fun newerMessagesNeverFollow() {
        assertFalse(shouldFollowNewest(wasAtBottom = true, userScrolling = false, newestOutgoing = true, localSendPending = true, hasNewerMessages = true))
    }
}
