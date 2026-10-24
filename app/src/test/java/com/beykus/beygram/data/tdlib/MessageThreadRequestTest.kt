package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageThreadRequestTest {
    @Test fun `thread is resolved from the channel post`() {
        val request = messageThreadRequest(-100123L, 42L)
        assertEquals(-100123L, request.chatId)
        assertEquals(42L, request.messageId)
    }

    @Test fun `history pages the linked discussion chat, not the channel`() {
        val request = messageThreadHistoryRequest(-100777L, 42L, fromMessageId = 900L)
        assertEquals(-100777L, request.chatId)
        assertEquals(42L, request.messageId)
        assertEquals(900L, request.fromMessageId)
        assertEquals(0, request.offset)
        assertEquals(50, request.limit)
    }

    @Test fun `new top-level comment is sent into MessageTopicThread`() {
        val content = TdApi.InputMessageText().apply { text = TdApi.FormattedText("hello", emptyArray<TdApi.TextEntity>()) }
        val request = threadReplyRequest(-100777L, 42L, replyToMessageId = null, content = content)
        assertEquals(-100777L, request.chatId)
        assertEquals(42L, (request.topicId as TdApi.MessageTopicThread).messageThreadId)
        assertNull(request.replyTo)
        assertSame(content, request.inputMessageContent)
    }

    @Test fun `reply to a comment keeps both thread and target`() {
        val content = TdApi.InputMessageText().apply { text = TdApi.FormattedText("reply", emptyArray<TdApi.TextEntity>()) }
        val request = threadReplyRequest(-100777L, 42L, replyToMessageId = 900L, content = content)
        assertEquals(42L, (request.topicId as TdApi.MessageTopicThread).messageThreadId)
        val reply = request.replyTo as TdApi.InputMessageReplyToMessage
        assertEquals(900L, reply.messageId)
        assertEquals("", reply.pollOptionId)
    }

    @Test fun `closed and hidden thread errors have different explanations`() {
        val closed = threadRequestFailure(TdRequestError(400, "Message has no thread"))
        val hidden = threadRequestFailure(TdRequestError(400, "Can't access message comments"))
        assertEquals(UiText.Res(R.string.thread_comments_closed), closed.text)
        assertEquals(UiText.Res(R.string.thread_comments_hidden), hidden.text)
    }

    @Test fun `unrecognized thread failure stays retryable text`() {
        val failure = threadRequestFailure(TdRequestError(500, "Internal Server Error"))
        assertEquals(UiText.Res(R.string.thread_comments_unavailable), failure.text)
        assertTrue(failure.cause is TdRequestError)
    }
}
