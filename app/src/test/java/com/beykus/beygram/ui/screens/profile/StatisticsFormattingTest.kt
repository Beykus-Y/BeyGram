package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsFormattingTest {
    @Test
    fun `small counts stay unchanged`() {
        assertEquals("0", formatCompactCount(0L))
        assertEquals("999", formatCompactCount(999L))
        assertEquals("-999", formatCompactCount(-999L))
    }

    @Test
    fun `thousands millions and billions use short suffixes`() {
        assertEquals("1K", formatCompactCount(1_000L))
        assertEquals("1.5K", formatCompactCount(1_500L))
        assertEquals("12K", formatCompactCount(12_000L))
        assertEquals("999K", formatCompactCount(999_499L))
        assertEquals("1M", formatCompactCount(999_500L))
        assertEquals("1.2M", formatCompactCount(1_200_000L))
        assertEquals("2B", formatCompactCount(2_000_000_000L))
    }

    @Test
    fun `negative large values keep the sign`() {
        assertEquals("-1.5K", formatCompactCount(-1_500L))
        assertEquals("-2M", formatCompactCount(-2_000_000L))
    }
}
