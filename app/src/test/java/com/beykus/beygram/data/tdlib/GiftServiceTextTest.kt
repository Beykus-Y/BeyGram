package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Test

class GiftServiceTextTest {
    @Test
    fun `a refunded gift wins over conversion and direction`() {
        assertEquals(GiftServiceVariant.Refunded, giftServiceVariant(wasRefunded = true, wasConverted = true, isOutgoing = true))
        assertEquals(GiftServiceVariant.Refunded, giftServiceVariant(wasRefunded = true, wasConverted = false, isOutgoing = false))
    }

    @Test
    fun `a converted gift keeps its conversion line over direction`() {
        assertEquals(GiftServiceVariant.Converted, giftServiceVariant(wasRefunded = false, wasConverted = true, isOutgoing = false))
    }

    @Test
    fun `direction picks sent or received and an unknown direction stays neutral`() {
        assertEquals(GiftServiceVariant.Sent, giftServiceVariant(wasRefunded = false, wasConverted = false, isOutgoing = true))
        assertEquals(GiftServiceVariant.Received, giftServiceVariant(wasRefunded = false, wasConverted = false, isOutgoing = false))
        assertEquals(GiftServiceVariant.Unspecified, giftServiceVariant(wasRefunded = false, wasConverted = false, isOutgoing = null))
    }

    @Test
    fun `premium duration uses months first and never shows an empty duration`() {
        assertEquals(ServiceDurationUnit.Months, serviceDurationUnit(monthCount = 3, dayCount = 0))
        assertEquals(ServiceDurationUnit.Months, serviceDurationUnit(monthCount = 1, dayCount = 30))
        assertEquals(ServiceDurationUnit.Days, serviceDurationUnit(monthCount = 0, dayCount = 7))
        assertEquals(ServiceDurationUnit.None, serviceDurationUnit(monthCount = 0, dayCount = 0))
        assertEquals(ServiceDurationUnit.None, serviceDurationUnit(monthCount = -1, dayCount = -1))
    }

    @Test
    fun `ton amounts are exact decimals of nanotons`() {
        assertEquals("0", tonAmountText(0L))
        assertEquals("0.000000001", tonAmountText(1L))
        assertEquals("1.5", tonAmountText(1_500_000_000L))
        assertEquals("2", tonAmountText(2_000_000_000L))
        assertEquals("100", tonAmountText(100_000_000_000L))
    }
}
