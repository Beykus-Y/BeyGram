package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveLocationStopTest {
    @Test
    fun `stopping a live location clears the request location`() {
        val request = stopLiveLocationRequest(chatId = 10L, messageId = 20L)

        assertEquals(10L, request.chatId)
        assertEquals(20L, request.messageId)
        assertNull(request.replyMarkup)
        // TDLib stops sharing only when the location field is null, not when livePeriod is set to zero.
        assertNull(request.location)
    }
}
