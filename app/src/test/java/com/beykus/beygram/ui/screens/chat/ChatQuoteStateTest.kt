package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageSendOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatQuoteStateTest {
    @Test fun quoteIsConsumedOnlyForItsOwnReply() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12) }
        val options = MessageSendOptions(disableNotification = true)

        assertNull(state.textFor(8L))
        assertEquals(options, state.consumeInto(options, 8L))
        assertNull(state.textFor(7L))

        state.select(7L, "fragment", 12)
        assertEquals("fragment", state.textFor(7L))
        assertEquals(options.copy(replyQuote = "fragment", replyQuotePosition = 12), state.consumeInto(options, 7L))
        assertEquals(options, state.consumeInto(options, 7L))
    }

    @Test fun switchingReplyTargetsPermanentlyClearsThePreviousQuote() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12) }

        state.keepOnlyFor(7L)
        assertEquals("fragment", state.textFor(7L))
        state.keepOnlyFor(8L)
        state.keepOnlyFor(7L)
        assertNull(state.textFor(7L))
    }
}
