package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubbleAppearanceTest {
    @Test
    fun storedLineSpacingReadsBack() {
        MessageLineSpacing.entries.forEach { assertEquals(it, messageLineSpacingFromStored(it.name)) }
    }

    @Test
    fun missingOrUnknownLineSpacingIsNormal() {
        assertEquals(MessageLineSpacing.Normal, messageLineSpacingFromStored(null))
        assertEquals(MessageLineSpacing.Normal, messageLineSpacingFromStored("Huge"))
    }

    @Test
    fun normalKeepsTheLineHeightMessagesAlwaysHad() {
        assertEquals(1.375, MessageLineSpacing.Normal.multiplier.toDouble(), 0.0)
    }

    @Test
    fun cornerRadiusStaysInsideTheAllowedRange() {
        assertNull(validBubbleCornerRadius(null))
        assertEquals(MIN_BUBBLE_CORNER_RADIUS_DP, validBubbleCornerRadius(1))
        assertEquals(MAX_BUBBLE_CORNER_RADIUS_DP, validBubbleCornerRadius(99))
        assertEquals(12, validBubbleCornerRadius(12))
    }
}
