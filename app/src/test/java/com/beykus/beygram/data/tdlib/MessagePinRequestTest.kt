package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePinRequestTest {
    @Test fun `pin is silent and for everyone`() {
        val request = pinMessageRequest(-100123L, 42L, pinned = true) as TdApi.PinChatMessage
        assertEquals(-100123L, request.chatId)
        assertEquals(42L, request.messageId)
        assertTrue(request.disableNotification)
        assertFalse(request.onlyForSelf)
    }

    @Test fun `unpin uses the same chat and message ids`() {
        val request = pinMessageRequest(-100123L, 42L, pinned = false) as TdApi.UnpinChatMessage
        assertEquals(-100123L, request.chatId)
        assertEquals(42L, request.messageId)
    }

    @Test fun `forum topic pin keeps the parent chat and the message id`() {
        // PinChatMessage has no topic parameter: TDLib derives the topic from the message id in the parent chat.
        val pin = pinMessageRequest(-100777L, 987654L, pinned = true) as TdApi.PinChatMessage
        assertEquals(-100777L, pin.chatId)
        assertEquals(987654L, pin.messageId)

        val unpin = pinMessageRequest(-100777L, 987654L, pinned = false) as TdApi.UnpinChatMessage
        assertEquals(-100777L, unpin.chatId)
        assertEquals(987654L, unpin.messageId)
    }
}
