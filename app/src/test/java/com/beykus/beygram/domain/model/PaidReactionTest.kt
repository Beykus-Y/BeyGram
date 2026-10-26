package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaidReactionTest {
    @Test fun `the dialog offers the four presets`() {
        assertEquals(listOf(1L, 10L, 50L, 100L), PAID_REACTION_PRESETS)
    }

    @Test fun `an amount that is not positive is refused whatever the balance`() {
        assertEquals(PaidReactionAmountBlock.NotPositive, paidReactionAmountBlock(0L, balance = 100L, maximum = null))
        assertEquals(PaidReactionAmountBlock.NotPositive, paidReactionAmountBlock(-1L, balance = null, maximum = null))
    }

    @Test fun `an unknown balance blocks the send`() {
        assertEquals(PaidReactionAmountBlock.BalanceUnknown, paidReactionAmountBlock(1L, balance = null, maximum = 100L))
    }

    @Test fun `an amount above the balance is refused, and the whole balance may be sent`() {
        assertEquals(PaidReactionAmountBlock.ExceedsBalance, paidReactionAmountBlock(11L, balance = 10L, maximum = null))
        assertNull(paidReactionAmountBlock(10L, balance = 10L, maximum = null))
    }

    @Test fun `an amount above the TDLib maximum is refused, and no maximum leaves only the balance`() {
        assertEquals(PaidReactionAmountBlock.ExceedsMaximum, paidReactionAmountBlock(50L, balance = 1_000L, maximum = 10L))
        assertNull(paidReactionAmountBlock(10L, balance = 1_000L, maximum = 10L))
        assertNull(paidReactionAmountBlock(100L, balance = 1_000L, maximum = null))
    }

    @Test fun `only the presets that the balance and the maximum allow are offered`() {
        val allowed = PAID_REACTION_PRESETS.filter { paidReactionAmountBlock(it, balance = 60L, maximum = 100L) == null }
        assertEquals(listOf(1L, 10L, 50L), allowed)
    }
}
