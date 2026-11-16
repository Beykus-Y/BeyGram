package com.beykus.beygram.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidTextMessageTest {
    @Test fun onlyPositiveAffordablePriceCanBeConfirmed() {
        assertTrue(paidTextCanConfirm(5L, StarBalance(5L)))
        assertFalse(paidTextCanConfirm(5L, StarBalance(4L, 999999999)))
        assertFalse(paidTextCanConfirm(5L, null))
        assertFalse(paidTextCanConfirm(0L, StarBalance(50L)))
        assertFalse(paidTextCanConfirm(-1L, StarBalance(50L)))
    }

    @Test fun anyChangedPriceNeedsAnotherConfirmation() {
        assertTrue(paidTextPriceMatches(5L, 5L))
        assertFalse(paidTextPriceMatches(5L, 6L))
        assertFalse(paidTextPriceMatches(5L, 4L))
        assertFalse(paidTextPriceMatches(5L, 0L))
        assertFalse(paidTextPriceMatches(0L, 0L))
    }
}
