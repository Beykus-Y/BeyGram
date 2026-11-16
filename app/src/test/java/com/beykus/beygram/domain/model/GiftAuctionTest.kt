package com.beykus.beygram.domain.model

import org.junit.Assert.*
import org.junit.Test

class GiftAuctionTest {
    private val quote = GiftAuctionQuote("auction", 7, 42, 200, 100, 500, 1, 3, 300,
        10, 0, 100, null, emptyList(), StarBalance(1000), null, false)
    @Test fun newBidAndIncreaseCharge() {
        assertEquals(100L, auctionBidDebit(quote, 100))
        assertNull(auctionBidDebit(quote, 99))
        val raised = quote.copy(ownBid = GiftAuctionOwnBid(100, 190, 150, 42, false), balance = StarBalance(50))
        assertEquals(50L, auctionBidDebit(raised, 150))
        assertNull(auctionBidDebit(raised, 151))
    }

    @Test fun refusesWrongRecipientReturnedBidAndUnavailableAuction() {
        assertNull(auctionBidDebit(quote.copy(ownBid = GiftAuctionOwnBid(100, 190, 150, 99, false)), 150))
        assertNull(auctionBidDebit(quote.copy(ownBid = GiftAuctionOwnBid(100, 190, 150, 42, true)), 150))
        assertNull(auctionBidDebit(quote.copy(finished = true), 100))
        assertNull(auctionBidDebit(quote.copy(serverTime = 300), 100))
        assertNull(auctionBidDebit(quote.copy(serverTime = 99), 100))
        assertNull(auctionBidDebit(quote.copy(balance = null), 100))
    }

    @Test fun changedTermsInvalidateConfirmation() {
        assertTrue(auctionQuoteMatches(quote, quote.copy(serverTime = 201)))
        assertFalse(auctionQuoteMatches(quote, quote.copy(minimum = 101)))
        assertFalse(auctionQuoteMatches(quote, quote.copy(round = 2)))
        assertFalse(auctionQuoteMatches(quote, quote.copy(recipientId = 99)))
        assertFalse(auctionQuoteMatches(quote, quote.copy(acquired = 1)))
        assertFalse(auctionQuoteMatches(quote, quote.copy(ownBid = GiftAuctionOwnBid(100, 190, 150, 42, false))))
    }
    @Test fun historyPreservesSeparateWinsAndSortsNewestFirst() {
        val first = GiftAuctionWin("A", 100, 100, 1, 2, null, "", false)
        val second = first.copy(receiver = "B", date = 200, round = 2, position = 3)
        val third = second.copy(position = 1, number = 42)
        assertEquals(listOf(third, second, first), sortAuctionWins(listOf(first, second, third)))
    }
}
