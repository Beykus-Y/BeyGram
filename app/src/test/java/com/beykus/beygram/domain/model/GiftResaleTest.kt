package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class GiftResaleTest {
    private val listing = ResaleGiftItem(1L, "Gift-1", "Gift", 1, "", 50L, false)

    @Test fun onlyAffordableStarsListingsFromOtherOwnersCanBeBought() {
        assertTrue(resaleCanBuy(listing, StarBalance(50L)))
        assertFalse(resaleCanBuy(listing, StarBalance(49L)))
        assertFalse(resaleCanBuy(listing, null))
        assertFalse(resaleCanBuy(listing.copy(isOwned = true), StarBalance(50L)))
        assertFalse(resaleCanBuy(listing.copy(starCount = null), StarBalance(50L)))
        assertFalse(resaleCanBuy(listing.copy(starCount = 0L), StarBalance(50L)))
    }

    @Test fun changedPriceAndCurrencyRequireNewConsent() {
        assertTrue(resalePriceMatches(50L, 50L, false))
        assertFalse(resalePriceMatches(50L, 51L, false))
        assertFalse(resalePriceMatches(50L, 49L, false))
        assertFalse(resalePriceMatches(50L, null, false))
        assertFalse(resalePriceMatches(50L, 50L, true))
    }

    @Test fun pagesDeduplicateByCollectibleName() {
        val other = listing.copy(id = 2L, name = "Gift-2", number = 2)
        assertEquals(listOf(listing, other), mergeResaleGifts(listOf(listing), listOf(listing, other)))
    }
}
