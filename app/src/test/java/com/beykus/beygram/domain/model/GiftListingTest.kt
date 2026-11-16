package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class GiftListingTest {
    private val quote = GiftListingQuote("received", "Gift-1", null, false, true, 100, 1000, 800)

    @Test fun rejectsInvalidPricesAndUnknownTerms() {
        assertTrue(giftListingAllowed(quote, 100))
        assertFalse(giftListingAllowed(quote, 99))
        assertFalse(giftListingAllowed(quote, 1001))
        assertFalse(giftListingAllowed(quote.copy(eligible = false), 200))
        assertFalse(giftListingAllowed(quote.copy(earningsPerMille = -1), 200))
        assertFalse(giftListingAllowed(quote.copy(tonOnly = true), 200))
        assertFalse(giftListingAllowed(quote.copy(currentStars = 200), 200))
    }

    @Test fun removalRequiresAnExistingStarsListing() {
        assertFalse(giftListingAllowed(quote, null))
        assertTrue(giftListingAllowed(quote.copy(currentStars = 200), null))
        assertFalse(giftListingAllowed(quote.copy(currentStars = 200, tonOnly = true), null))
        assertFalse(giftListingAllowed(quote, 0))
    }

    @Test fun proceedsUseServerShareWithoutOverflow() {
        assertEquals(800L, giftListingProceeds(1000, 800))
        assertEquals(80L, giftListingProceeds(101, 800))
        assertEquals(Long.MAX_VALUE, giftListingProceeds(Long.MAX_VALUE, 1000))
        assertNull(giftListingProceeds(1000, -1))
    }
}
