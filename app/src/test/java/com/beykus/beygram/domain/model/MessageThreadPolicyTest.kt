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
}
