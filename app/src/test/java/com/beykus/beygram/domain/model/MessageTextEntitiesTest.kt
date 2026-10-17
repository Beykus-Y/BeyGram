package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class MessageTextEntitiesTest {
    @Test fun `inserting emoji before styled text shifts UTF16 offsets by two`() {
        assertEquals(listOf(MessageTextEntity(2, 5, TextStyleKind.Bold)), adjustTextEntities("hello", "😀hello", listOf(MessageTextEntity(0, 5, TextStyleKind.Bold))))
    }
    @Test fun `replacement inside a span resizes it and preserves adjacent styles`() {
        val entities = listOf(MessageTextEntity(0, 5, TextStyleKind.Bold), MessageTextEntity(6, 5, TextStyleKind.Italic))
        assertEquals(listOf(MessageTextEntity(0, 3, TextStyleKind.Bold), MessageTextEntity(4, 5, TextStyleKind.Italic)), adjustTextEntities("hello world", "hey world", entities))
    }
    @Test fun `deleting all styled text removes its entities`() {
        assertTrue(adjustTextEntities("bold", "", listOf(MessageTextEntity(0, 4, TextStyleKind.Bold))).isEmpty())
    }
    @Test fun `clearing selection retains formatting on both sides`() {
        assertEquals(listOf(MessageTextEntity(0, 2, TextStyleKind.Bold), MessageTextEntity(4, 2, TextStyleKind.Bold)), applyTextFormat("abcdef", listOf(MessageTextEntity(0, 6, TextStyleKind.Bold)), 2, 4, null))
    }
    @Test fun `monospace selection removes incompatible overlapping style`() {
        val result = applyTextFormat("abcdef", listOf(MessageTextEntity(0, 6, TextStyleKind.Bold)), 2, 4, TextStyleKind.Code)
        assertTrue(result.contains(MessageTextEntity(2, 2, TextStyleKind.Code)))
        assertFalse(result.any { it.kind == TextStyleKind.Bold && it.offset < 4 && it.offset + it.length > 2 })
    }
    @Test fun `malformed ranges cannot overflow or split a surrogate pair`() {
        val text = "😀 hi"
        assertTrue(validTextEntities(text, listOf(
            MessageTextEntity(-1, 2, TextStyleKind.Bold), MessageTextEntity(1, 1, TextStyleKind.Bold),
            MessageTextEntity(0, 1, TextStyleKind.Bold), MessageTextEntity(2, Int.MAX_VALUE, TextStyleKind.Bold)
        )).isEmpty())
    }

    @Test fun `quote becomes a distinct block with nested formatting and surrounding text intact`() {
        val parts = splitMessageText("before\nquote\nafter", listOf(
            MessageTextEntity(7, 5, TextStyleKind.Quote), MessageTextEntity(7, 5, TextStyleKind.Bold)
        ))
        assertEquals(listOf("before", "quote", "after"), parts.map { it.text })
        assertEquals(TextStyleKind.Quote, parts[1].block)
        assertEquals(listOf(MessageTextEntity(0, 5, TextStyleKind.Bold)), parts[1].entities)
        assertNull(parts[0].block)
        assertNull(parts[2].block)
    }

    @Test fun `spoiler inside a quote retains offsets relative to block without exposing its style`() {
        val part = splitMessageText("plain\nhidden quote", listOf(
            MessageTextEntity(6, 12, TextStyleKind.Quote), MessageTextEntity(6, 6, TextStyleKind.Spoiler)
        ))[1]
        assertEquals(TextStyleKind.Quote, part.block)
        assertEquals(MessageTextEntity(0, 6, TextStyleKind.Spoiler), part.entities.single())
    }

    @Test fun `inline code stays inline while preformatted text becomes a block`() {
        val inline = splitMessageText("code", listOf(MessageTextEntity(0, 4, TextStyleKind.Code))).single()
        assertNull(inline.block)
        assertEquals(TextStyleKind.Code, inline.entities.single().kind)
        val block = splitMessageText("code", listOf(MessageTextEntity(0, 4, TextStyleKind.Pre))).single()
        assertEquals(TextStyleKind.Pre, block.block)
        assertTrue(block.entities.isEmpty())
    }
}
