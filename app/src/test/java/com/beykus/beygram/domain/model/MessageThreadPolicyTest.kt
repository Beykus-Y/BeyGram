package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageThreadPolicyTest {
    @Test fun `comments chip is only for a channel post with a linked discussion`() {
        assertTrue(showsChannelComments(isChannel = true, isChannelPost = true, linkedDiscussionChatId = -100777L))
        assertFalse(showsChannelComments(isChannel = false, isChannelPost = true, linkedDiscussionChatId = -100777L))
        assertFalse(showsChannelComments(isChannel = true, isChannelPost = false, linkedDiscussionChatId = -100777L))
        assertFalse(showsChannelComments(isChannel = true, isChannelPost = true, linkedDiscussionChatId = 0L))
    }

    @Test fun `composer opens only for a readable joined discussion with reply info`() {
        assertTrue(
            canWriteThreadComment(
                canGetMessageThread = true,
                hasReplyInfo = true,
                isMember = true,
                canSendBasicMessages = true
            )
        )
    }

    @Test fun `closed comments and non-members are read-only`() {
        assertFalse(
            canWriteThreadComment(
                canGetMessageThread = false,
                hasReplyInfo = true,
                isMember = true,
                canSendBasicMessages = true
            )
        )
        assertFalse(
            canWriteThreadComment(
                canGetMessageThread = true,
                hasReplyInfo = false,
                isMember = true,
                canSendBasicMessages = true
            )
        )
        assertFalse(
            canWriteThreadComment(
                canGetMessageThread = true,
                hasReplyInfo = true,
                isMember = false,
                canSendBasicMessages = true
            )
        )
    }

    @Test fun `discussion group permissions disable only the composer`() {
        assertFalse(
            canWriteThreadComment(
                canGetMessageThread = true,
                hasReplyInfo = true,
                isMember = true,
                canSendBasicMessages = false
            )
        )
    }

    @Test fun `older and sent pages merge oldest first without duplicate comments`() {
        fun message(id: Long) = ChatMessage(id, -100777L, "comment $id", "12:00", false)
        val current = listOf(message(20L), message(30L))
        val older = listOf(message(10L), message(20L))
        val merged = mergeThreadMessages(current, older)
        assertEquals(listOf(10L, 20L, 30L), merged.map { it.id })
        assertEquals(3, merged.size)
    }

    private fun threadComment(id: Long, state: MessageSendState = MessageSendState.Sent, threadId: Long = 5L) =
        ChatMessage(id, -100777L, "comment $id", "12:00", true, sendState = state, messageThreadId = threadId)

    @Test fun `confirmed comment replaces its temporary bubble`() {
        val current = listOf(threadComment(10L, MessageSendState.Sending))
        val history = ChatHistory(
            messages = listOf(threadComment(100L)),
            recentReplacements = listOf(MessageIdReplacement(oldId = 10L, newId = 100L))
        )
        val merged = mergeThreadHistory(current, history, chatId = -100777L, threadId = 5L)
        assertEquals(listOf(100L), merged.map { it.id })
        assertEquals(listOf(MessageSendState.Sent), merged.map { it.sendState })
    }

    @Test fun `temporary comment stays until its confirmed copy is in the snapshot`() {
        val current = listOf(threadComment(10L, MessageSendState.Sending))
        val history = ChatHistory(recentReplacements = listOf(MessageIdReplacement(oldId = 10L, newId = 100L)))
        assertEquals(listOf(10L), mergeThreadHistory(current, history, chatId = -100777L, threadId = 5L).map { it.id })
    }

    @Test fun `comment kept outside the window carries its latest update`() {
        val current = listOf(threadComment(7L))
        val history = ChatHistory(outsideWindowMessages = listOf(threadComment(7L).copy(isEdited = true)))
        val merged = mergeThreadHistory(current, history, chatId = -100777L, threadId = 5L)
        assertEquals(listOf(7L), merged.map { it.id })
        assertTrue(merged.single().isEdited)
    }

    @Test fun `cached older comments never open a gap before the newest page`() {
        val current = listOf(threadComment(150L))
        val history = ChatHistory(outsideWindowMessages = listOf(threadComment(1L), threadComment(50L)))
        assertEquals(listOf(150L), mergeThreadHistory(current, history, chatId = -100777L, threadId = 5L).map { it.id })
    }

    @Test fun `other threads and the thread root never join the comment list`() {
        val history = ChatHistory(
            messages = listOf(threadComment(5L), threadComment(8L, threadId = 9L), threadComment(12L))
        )
        assertEquals(listOf(12L), mergeThreadHistory(emptyList(), history, chatId = -100777L, threadId = 5L).map { it.id })
    }
}
