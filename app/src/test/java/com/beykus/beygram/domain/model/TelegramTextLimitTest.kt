package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class TelegramTextLimitTest {
    @Test fun unchangedWithinLimit() {
        val text = "Привет 🙂"
        val styled = listOf(MessageTextEntity(0, 6, TextStyleKind.Bold))
        val result = boundTelegramText(text, styled, 4096)
        assertEquals(text, result.text)
        assertEquals(styled, result.entities)
        assertFalse(result.truncated)
    }

    @Test fun truncatesAtCodePointBoundaryWithoutSplittingEmoji() {
        val result = boundTelegramText("A🙂BC", emptyList(), 2)
        assertEquals("A🙂", result.text)
        assertTrue(result.truncated)
        assertEquals(2, result.text.codePointCount(0, result.text.length))
    }

    @Test fun clipsIntersectingEntityAndDropsEntitiesBeyondCut() {
        val source = "1234567"
        val result = boundTelegramText(source, listOf(
            MessageTextEntity(1, 5, TextStyleKind.Bold),
            MessageTextEntity(6, 1, TextStyleKind.Italic)
        ), 4)
        assertEquals("1234", result.text)
        assertEquals(listOf(MessageTextEntity(1, 3, TextStyleKind.Bold)), result.entities)
    }

    @Test fun respectsThousandsOfUnicodeCharacters() {
        val source = "ю".repeat(4096) + "🙂"
        val result = boundTelegramText(source, emptyList(), 4096)
        assertEquals("ю".repeat(4096), result.text)
    }

    @Test fun captionLimitAndEmptyCaptionWork() {
        assertEquals("abc", boundTelegramText("abc123", emptyList(), 3).text)
        assertEquals("", boundTelegramText("", emptyList(), 1024).text)
        assertFalse(boundTelegramText("", emptyList(), 1024).truncated)
    }

    @Test fun refusesInvalidLimits() {
        try { boundTelegramText("abc", emptyList(), 0); fail("Expected invalid limit") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun cutCannotLeaveAnUnpairedSurrogate() {
        val result = boundTelegramText("A🙂🙂🙂", emptyList(), 3)
        assertFalse(result.text.last().isHighSurrogate())
        assertEquals(3, result.text.codePointCount(0, result.text.length))
    }
}
