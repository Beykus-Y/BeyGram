package com.beykus.beygram.domain.model

/** Never authorize a paid message with an unknown/insufficient whole-Star balance. */
fun paidTextCanConfirm(price: Long, balance: StarBalance?): Boolean =
    price > 0L && balance != null && balance.starCount >= price

/** A changed quote requires a new confirmation even if it became cheaper. */
fun paidTextPriceMatches(confirmedPrice: Long, currentPrice: Long): Boolean =
    confirmedPrice > 0L && confirmedPrice == currentPrice

/** Snapshot of the exact draft that was priced. This is intentionally not serializable or persisted. */
data class PaidTextConfirmation(
    val draft: MessageDraft,
    val price: Long,
    val balance: StarBalance,
    val disableNotification: Boolean
)
