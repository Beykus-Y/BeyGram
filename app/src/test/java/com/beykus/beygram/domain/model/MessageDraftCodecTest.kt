package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDraftCodecTest {
    @Test fun preservesUnicodeFormattingReplyAndDelimiters() {
        val draft = MessageDraft(
            text = "Привет 🌍\nhello|world",
            entities = listOf(MessageTextEntity(0, 6, TextStyleKind.Bold), MessageTextEntity(10, 5, TextStyleKind.Url, "https://a.example/?x=1;z=2")),
            replyToMessageId = 123456L
        )
        val source = StoredMessageDraft(draft, 1720000000000L)
        assertEquals(source, MessageDraftCodec.decode(MessageDraftCodec.encode(source)))
    }

    @Test fun anEmptyDraftIsStillAnAuthoritativeTombstone() {
        val source = StoredMessageDraft(MessageDraft(), 1720000000000L)
        assertEquals(source, MessageDraftCodec.decode(MessageDraftCodec.encode(source)))
    }

    @Test fun unreadableOrUnknownFormatsDoNotCrash() {
        assertNull(MessageDraftCodec.decode(""))
        assertNull(MessageDraftCodec.decode("v2|0|0||"))
        assertNull(MessageDraftCodec.decode("v1|wrong|0||"))
    }

    @Test fun invalidEntityRangesAreDiscarded() {
        val source = StoredMessageDraft(
            MessageDraft("a", listOf(MessageTextEntity(9, 2, TextStyleKind.Italic))), 2L
        )
        assertEquals(emptyList<MessageTextEntity>(), MessageDraftCodec.decode(MessageDraftCodec.encode(source))?.value?.entities)
    }
}
