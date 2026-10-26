package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException

/** Stars amounts offered for one paid reaction; the dialog has no free-form amount field. */
val PAID_REACTION_PRESETS: List<Long> = listOf(1L, 10L, 50L, 100L)

/** Why [paidReactionAmountBlock] refuses an amount. */
enum class PaidReactionAmountBlock { NotPositive, BalanceUnknown, ExceedsBalance, ExceedsMaximum }

/**
 * Checks one Stars amount before anything is sent. An unknown [balance] blocks the send, because the user must see the balance
 * the Stars come from. A null [maximum] means TDLib reported no limit, so only the balance applies.
 */
fun paidReactionAmountBlock(starCount: Long, balance: Long?, maximum: Long?): PaidReactionAmountBlock? = when {
    starCount <= 0L -> PaidReactionAmountBlock.NotPositive
    balance == null -> PaidReactionAmountBlock.BalanceUnknown
    starCount > balance -> PaidReactionAmountBlock.ExceedsBalance
    maximum != null && starCount > maximum -> PaidReactionAmountBlock.ExceedsMaximum
    else -> null
}

/**
 * A Stars reaction that TDLib did not confirm after its commit was sent. The Stars may already be spent, so the user must check
 * the message before sending again.
 */
class PaidReactionUncertainException(text: UiText, cause: Throwable? = null) : UserMessageException(text, cause)
