package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.saveable.SaverScope
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingReplyQuote
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatQuoteStateTest {
    @Test fun quoteIsAppliedOnlyToItsOwnReplyAndSurvivesARetry() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12) }
        val options = MessageSendOptions(disableNotification = true)

        assertNull(state.textFor(8L))
        assertEquals(options, state.applyTo(options, 8L))
        assertEquals("fragment", state.textFor(7L))
        assertEquals(options.copy(replyQuote = "fragment", replyQuotePosition = 12), state.applyTo(options, 7L))
        assertEquals(options.copy(replyQuote = "fragment", replyQuotePosition = 12), state.applyTo(options, 7L))
    }

    @Test fun switchingReplyTargetsPermanentlyClearsThePreviousQuote() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12) }

        state.keepOnlyFor(7L)
        assertEquals("fragment", state.textFor(7L))
        state.keepOnlyFor(8L)
        state.keepOnlyFor(7L)
        assertNull(state.textFor(7L))
    }

    @Test fun directQuoteRestoresButOnlyForTheReplyItBelongsTo() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12, restoreAfterRecreation = true) }
        val saver = ChatQuoteState.Saver
        val saved = with(saver) { SaverScope { true }.save(state) }
        val restored = requireNotNull(saver.restore(requireNotNull(saved)))
        assertNull(restored.textFor(8L))
        assertEquals("fragment", restored.textFor(7L))
        assertEquals(12, restored.applyTo(MessageSendOptions(), 7L).replyQuotePosition)
    }

    @Test fun legacyQuoteContentIsNeverWrittenIntoTheSavedState() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12) }
        val saved = with(ChatQuoteState.Saver) { SaverScope { true }.save(state) }
        assertNull(saved)
    }

    @Test fun clearForClearsMatchingReplyWithoutAffectingOtherReplies() {
        val state = ChatQuoteState().apply { select(7L, "fragment", 12, restoreAfterRecreation = true) }

        state.clearFor(8L)
        assertEquals("fragment", state.textFor(7L))

        state.clearFor(7L)
        assertNull(state.textFor(7L))
        assertEquals(MessageSendOptions(), state.applyTo(MessageSendOptions(), 7L))
    }

    @Test fun restoreRestoresQuoteWithEntitiesAndClearsWhenAbsent() {
        val entities = listOf(MessageTextEntity(0, 6, TextStyleKind.Bold))
        val payload = OutgoingReplyQuote("quoted", 4, entities)
        val state = ChatQuoteState()

        state.restore(7L, payload)
        assertEquals("quoted", state.textFor(7L))
        assertEquals(
            MessageSendOptions(replyQuote = "quoted", replyQuotePosition = 4, replyQuoteEntities = entities),
            state.applyTo(MessageSendOptions(), 7L)
        )
        val saved = with(ChatQuoteState.Saver) { SaverScope { true }.save(state) }
        val recreated = requireNotNull(ChatQuoteState.Saver.restore(requireNotNull(saved)))
        assertEquals("quoted", recreated.textFor(7L))

        state.restore(7L, null)
        assertNull(state.textFor(7L))

        state.restore(7L, payload)
        state.restore(null, payload)
        assertNull(state.textFor(7L))
    }

}
