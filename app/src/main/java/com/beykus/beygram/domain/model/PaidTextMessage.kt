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
    val disableNotification: Boolean,
    val destination: PaidTextDestination,
    val disableLinkPreview: Boolean
)

/** The exact account, chat and topic for which a fee was approved. */
data class PaidTextDestination(val accountId: String, val chatId: Long, val forumTopicId: Long)

/** Channels, secret chats and Saved Messages do not use the ordinary paid-text flow. */
fun supportsPaidTextQuote(chat: ChatPreview): Boolean =
    !chat.isSecret && !chat.isChannel && !chat.isSavedMessages

/** Never allow a paid reply to redirect the authorized send into a different topic. */
fun paidTextReplyTopicMatches(expected: Long, actualForumTopic: Long?, hasTopic: Boolean): Boolean =
    if (expected == 0L) !hasTopic
    else expected in 1L..Int.MAX_VALUE.toLong() && actualForumTopic == expected
