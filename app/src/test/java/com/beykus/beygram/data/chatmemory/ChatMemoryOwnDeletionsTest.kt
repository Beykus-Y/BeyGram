package com.beykus.beygram.data.chatmemory

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMemoryOwnDeletionsTest {
    private val ownDeletions = ChatMemoryOwnDeletions()

    @Test
    fun aRequestedDeletionIsOwnOnceAndALaterReportOfItIsTheOtherSidesDeletion() {
        ownDeletions.expect(CHAT, listOf(5L))

        assertEquals(setOf(5L), ownDeletions.takeOwn(CHAT, listOf(5L)))
        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(CHAT, listOf(5L)))
    }

    @Test
    fun aFailedRequestLeavesNoMarkBehind() {
        ownDeletions.expect(CHAT, listOf(5L))
        ownDeletions.forget(CHAT, listOf(5L))

        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(CHAT, listOf(5L)))
    }

    @Test
    fun aClearedHistoryCoversItsMessagesUpToTheNewestOne() {
        assertEquals(0L, ownDeletions.expectClear(CHAT, lastMessageId = 10L))

        assertEquals(setOf(3L, 10L), ownDeletions.takeOwn(CHAT, listOf(3L, 10L, 11L)))
    }

    @Test
    fun aFailedClearGivesTheCutoffBack() {
        val previous = ownDeletions.expectClear(CHAT, lastMessageId = 10L)
        ownDeletions.restoreClear(CHAT, previous)

        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(CHAT, listOf(3L)))
    }

    @Test
    fun aMarkOrAClearInOneChatDoesNotTouchAnother() {
        ownDeletions.expect(CHAT, listOf(5L))
        ownDeletions.expectClear(CHAT, lastMessageId = 10L)

        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(OTHER_CHAT, listOf(5L, 3L)))
        assertEquals(setOf(5L), ownDeletions.takeOwn(CHAT, listOf(5L)))
    }

    private companion object {
        const val CHAT = 42L
        const val OTHER_CHAT = 43L
    }
}
