package com.beykus.beygram.ui.screens.profile

import java.text.DecimalFormatSymbols
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsFormattingTest {
    // Short counts follow the locale's decimal separator, so the expectations are built with the same symbol.
    private val decimal = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

    @Test
    fun `small counts stay unchanged`() {
        assertEquals("0", formatCompactCount(0L))
        assertEquals("999", formatCompactCount(999L))
        assertEquals("-999", formatCompactCount(-999L))
    }

    @Test
    fun `thousands millions and billions use short suffixes`() {
        assertEquals("1K", formatCompactCount(1_000L))
        assertEquals("1${decimal}5K", formatCompactCount(1_500L))
        assertEquals("12K", formatCompactCount(12_000L))
        assertEquals("100K", formatCompactCount(100_000L))
        assertEquals("250K", formatCompactCount(250_000L))
        assertEquals("999K", formatCompactCount(999_499L))
        assertEquals("1M", formatCompactCount(999_500L))
        assertEquals("1${decimal}2M", formatCompactCount(1_200_000L))
        assertEquals("100M", formatCompactCount(100_000_000L))
        assertEquals("2B", formatCompactCount(2_000_000_000L))
    }

    @Test
    fun `negative large values keep the sign`() {
        assertEquals("-1${decimal}5K", formatCompactCount(-1_500L))
        assertEquals("-2M", formatCompactCount(-2_000_000L))
    }

    @Test
    fun `growth shows an explicit sign and the locale decimal`() {
        // The expected text is built with the same locale formatter, so the test holds on any device locale.
        assertEquals("+${formatDecimal(2.0)}%", formatSignedPercent(2.0))
        assertEquals("-${formatDecimal(0.4)}%", formatSignedPercent(-0.4))
        assertEquals("${formatDecimal(0.0)}%", formatSignedPercent(0.0))
        assertEquals("${formatDecimal(0.01)}%", formatSignedPercent(0.01))
    }

    @Test
    fun `growth without a finite value is not shown`() {
        assertNull(formatSignedPercent(Double.NaN))
        assertNull(formatSignedPercent(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `growth direction follows the sign and ignores rounding noise`() {
        assertEquals(1, growthTrend(3.5))
        assertEquals(-1, growthTrend(-0.2))
        assertEquals(0, growthTrend(0.01))
        assertEquals(0, growthTrend(Double.NaN))
    }
}
