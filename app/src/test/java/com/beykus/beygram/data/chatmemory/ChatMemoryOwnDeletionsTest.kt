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
        ownDeletions.endClear(CHAT)

        assertEquals(setOf(3L, 10L), ownDeletions.takeOwn(CHAT, listOf(3L, 10L, 11L)))
    }

    @Test
    fun aMessageThatArrivesWhileTheClearRunsIsRemovedWithIt() {
        // The newest message was read before the request, so a message that came in after that is removed by the same clear.
        ownDeletions.expectClear(CHAT, lastMessageId = 10L)
        assertEquals(setOf(12L), ownDeletions.takeOwn(CHAT, listOf(12L)))

        ownDeletions.endClear(CHAT)
        // Once the request has finished, a later message is the other side's to delete.
        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(CHAT, listOf(13L)))
    }

    @Test
    fun aFailedClearGivesTheCutoffBack() {
        val previous = ownDeletions.expectClear(CHAT, lastMessageId = 10L)
        ownDeletions.restoreClear(CHAT, previous)
        ownDeletions.endClear(CHAT)

        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(CHAT, listOf(3L)))
    }

    @Test
    fun aMarkOrAClearInOneChatDoesNotTouchAnother() {
        ownDeletions.expect(CHAT, listOf(5L))
        ownDeletions.expectClear(CHAT, lastMessageId = 10L)

        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(OTHER_CHAT, listOf(5L, 3L)))
        assertEquals(setOf(5L), ownDeletions.takeOwn(CHAT, listOf(5L)))
    }

    @Test
    fun forgetAllDropsEveryMarkAndCutoff() {
        // The account that made these deletions has signed out: the next account's chats must not inherit them.
        ownDeletions.expect(CHAT, listOf(5L))
        ownDeletions.expectClear(CHAT, lastMessageId = 10L)
        ownDeletions.endClear(CHAT)

        ownDeletions.forgetAll()

        assertEquals(emptySet<Long>(), ownDeletions.takeOwn(CHAT, listOf(5L, 3L)))
    }

    private companion object {
        const val CHAT = 42L
        const val OTHER_CHAT = 43L
    }
}
