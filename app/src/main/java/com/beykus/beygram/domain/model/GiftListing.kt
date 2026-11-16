package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText

data class GiftListingQuote(
    val receivedId: String,
    val collectibleName: String,
    val currentStars: Long?,
    val tonOnly: Boolean,
    val eligible: Boolean,
    val minimum: Long,
    val maximum: Long,
    val earningsPerMille: Long
)

sealed interface GiftListingOutcome {
    data object Updated : GiftListingOutcome
    data object Changed : GiftListingOutcome
    data class Failed(val reason: UiText) : GiftListingOutcome
    data object Uncertain : GiftListingOutcome
}

/** Null is explicit removal, never a malformed or zero price. */
fun giftListingAllowed(quote: GiftListingQuote, newStars: Long?): Boolean {
    if (quote.receivedId.isBlank() || quote.collectibleName.isBlank() || quote.tonOnly) return false
    if (newStars == null) return quote.currentStars != null && quote.currentStars > 0L
    return quote.eligible && quote.minimum > 0L && quote.maximum >= quote.minimum &&
        newStars in quote.minimum..quote.maximum && newStars != quote.currentStars &&
        quote.earningsPerMille in 0L..1000L
}

fun giftListingProceeds(stars: Long, earningsPerMille: Long): Long? {
    if (stars <= 0L || earningsPerMille !in 0L..1000L) return null
    return (stars / 1000L) * earningsPerMille + (stars % 1000L) * earningsPerMille / 1000L
}
