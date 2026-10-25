package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MonetizationFormattingTest {
    @Test
    fun `prices keep the currency and its fraction digits`() {
        assertEquals("9.99 EUR", formatPriceMinorUnits(999, "EUR"))
        assertEquals("12.34 USD", formatPriceMinorUnits(1234, "usd"))
        assertEquals("1000 JPY", formatPriceMinorUnits(1000, "JPY"))
        assertEquals("1.234 KWD", formatPriceMinorUnits(1234, "KWD"))
    }

    @Test
    fun `unknown or empty currencies do not crash the plans list`() {
        assertEquals("1.00 ZZZ", formatPriceMinorUnits(100, "ZZZ"))
        assertEquals("123", formatPriceMinorUnits(123, ""))
    }

    @Test
    fun `stars amounts combine integer and nano parts exactly`() {
        assertEquals("1250", formatStarAmount(1250))
        assertEquals("1.5", formatStarAmount(1, 500_000_000))
        assertEquals("-1.5", formatStarAmount(-1, -500_000_000))
        assertEquals("0", formatStarAmount(0))
    }

    @Test
    fun `payment switches stay disabled until a provider is integrated`() {
        assertFalse(MonetizationPayments.premiumPurchaseSupported)
        assertFalse(MonetizationPayments.starTopUpSupported)
    }
}
