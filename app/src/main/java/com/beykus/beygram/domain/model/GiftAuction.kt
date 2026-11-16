package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class GiftAuctionOwnBid(
    val stars: Long, val date: Int, val nextMinimum: Long,
    val recipientId: Long?, val returned: Boolean
)
data class GiftAuctionRank(val position: Int, val stars: Long)
data class GiftAuctionQuote(
    val auctionId: String, val giftId: Long, val recipientId: Long, val serverTime: Long,
    val start: Int, val end: Int, val round: Int, val roundCount: Int, val roundEnd: Int,
    val remaining: Int, val acquired: Int, val minimum: Long, val ownBid: GiftAuctionOwnBid?,
    val ranks: List<GiftAuctionRank>, val balance: StarBalance?, val averagePrice: Long?, val finished: Boolean
) {
    val requiredMinimum: Long get() = maxOf(minimum, ownBid?.nextMinimum ?: 0L)
}

sealed interface GiftAuctionOutcome {
    data object Submitted : GiftAuctionOutcome
    data object Changed : GiftAuctionOutcome
    data class Failed(val reason: UiText) : GiftAuctionOutcome
    data object Uncertain : GiftAuctionOutcome
}

/** int53 is the TDLib protocol limit; do not authorize returned bids or a different receiver. */
fun auctionBidDebit(quote: GiftAuctionQuote, total: Long): Long? {
    if (quote.finished || quote.auctionId.isBlank() || quote.giftId <= 0 || quote.recipientId <= 0 ||
        quote.serverTime <= 0 || quote.serverTime < quote.start || quote.serverTime >= quote.end ||
        quote.roundEnd <= quote.serverTime || quote.round <= 0 || quote.remaining <= 0 ||
        quote.requiredMinimum <= 0 || total !in quote.requiredMinimum..9_007_199_254_740_991L) return null
    val bid = quote.ownBid
    if (bid != null && (bid.returned || bid.recipientId != quote.recipientId || bid.stars <= 0L)) return null
    val old = bid?.stars ?: 0L
    if (total <= old) return null
    val debit = total - old
    return debit.takeIf { quote.balance != null && quote.balance.starCount >= debit }
}

/** A fresh quote can have new rankings/time/balance, but not new authorization terms. */
fun auctionQuoteMatches(old: GiftAuctionQuote, now: GiftAuctionQuote): Boolean =
    old.auctionId == now.auctionId && old.giftId == now.giftId && old.recipientId == now.recipientId &&
        old.round == now.round && old.requiredMinimum == now.requiredMinimum && old.ownBid == now.ownBid &&
        old.acquired == now.acquired && !now.finished
