package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.PAID_REACTION_EMOJI
import com.beykus.beygram.domain.model.ReactionOption
import org.drinkless.tdlib.TdApi

/** Reactions per row asked from TDLib for the reactions a message can take. TDLib accepts 5 to 25, and the largest is asked for. */
internal const val PICKER_ROW_SIZE = 25

/**
 * Reactions of a message, in the order Telegram sends them. A custom emoji reaction keeps its id and has no emoji text here,
 * since its sticker is looked up separately. A paid reaction keeps its Stars count and is marked as paid so that a tap can
 * never add Stars: the UI shows it as a display-only chip. A reaction without any count is dropped.
 */
internal fun messageReactions(interaction: TdApi.MessageInteractionInfo?): List<MessageReaction> =
    interaction?.reactions?.let { messageReactionsFrom(it.reactions.orEmpty(), it.paidReactors) }.orEmpty()

/** Reactions from a raw TDLib list, as in [TdApi.UpdateMessageReactions], which carries no interaction info. */
internal fun messageReactionsFrom(
    reactions: Array<out TdApi.MessageReaction>,
    paidReactors: Array<out TdApi.PaidReactor>? = null
): List<MessageReaction> =
    reactions.mapNotNull { reaction ->
        val option = optionOf(reaction.type) ?: return@mapNotNull null
        val count = if (reaction.type is TdApi.ReactionTypePaid) paidReactionCount(reaction, paidReactors)
            else reaction.totalCount
        if (count <= 0) null else MessageReaction(option.emoji, count, reaction.isChosen, option.customEmojiId, option.isPaid)
    }

/**
 * Stars of a paid reaction. TDLib's total count is the number of Stars, because every Star increments the reaction counter by
 * one. [paidReactors] lists only the top reactors, so it is only a fallback for a missing total count.
 */
private fun paidReactionCount(reaction: TdApi.MessageReaction, paidReactors: Array<out TdApi.PaidReactor>?): Int {
    val fromReactors = paidReactors.orEmpty().sumOf { it.starCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return maxOf(reaction.totalCount, fromReactors)
}

/**
 * Reactions the picker offers: TDLib's top, recent and popular reactions, then the chat's own list, or every active emoji when
 * the chat allows all of them. Premium-only reactions are left out for users without Premium, and so are custom emoji for every
 * user without it, since adding one takes Premium. Paid reactions are never offered: adding them spends Stars and needs an
 * explicit confirmation flow, so they stay display-only. The chat's list and the active set carry no Premium flags, so they are
 * added for Premium users only. Emoji that differ only by U+FE0F count as one, so a reaction is never listed twice.
 */
internal fun pickerOptions(
    available: TdApi.AvailableReactions,
    chat: TdApi.ChatAvailableReactions?,
    activeEmojis: List<String>,
    premium: Boolean
): List<ReactionOption> {
    val offers = (available.topReactions.orEmpty().toList() + available.recentReactions.orEmpty().toList() + available.popularReactions.orEmpty().toList())
        .filter { premium || !it.needsPremium }
        .mapNotNull { optionOf(it.type) }
        .filter { !it.isPaid }
        .filter { premium || it.customEmojiId == 0L }
    val extra: List<ReactionOption> = if (!premium) emptyList() else when (chat) {
        is TdApi.ChatAvailableReactionsSome -> chat.reactions.orEmpty().mapNotNull(::optionOf).filter { !it.isPaid }
        is TdApi.ChatAvailableReactionsAll -> activeEmojis.map { ReactionOption(it) }
        else -> emptyList()
    }
    return (offers + extra).distinctBy { option -> option.customEmojiId to option.emoji.filterNot { it == '️' } }
}

/** The reaction a TDLib type stands for; null only for a future type this app does not know yet. */
internal fun optionOf(type: TdApi.ReactionType): ReactionOption? = when (type) {
    is TdApi.ReactionTypeEmoji -> ReactionOption(type.emoji)
    is TdApi.ReactionTypeCustomEmoji -> ReactionOption("", type.customEmojiId)
    is TdApi.ReactionTypePaid -> ReactionOption(PAID_REACTION_EMOJI, isPaid = true)
    else -> null
}

/** The TDLib type that adds or removes [option]. A paid option must never reach an add request; it is mapped only for completeness. */
internal fun reactionTypeOf(option: ReactionOption): TdApi.ReactionType = when {
    option.isPaid -> TdApi.ReactionTypePaid()
    option.customEmojiId != 0L -> TdApi.ReactionTypeCustomEmoji(option.customEmojiId)
    else -> TdApi.ReactionTypeEmoji(option.emoji)
}

/** Chosen reactions to remove before one more is added, so that the message keeps at most [limit] of them. */
internal fun overflowReactions(chosen: List<TdApi.ReactionType>, limit: Int): List<TdApi.ReactionType> =
    chosen.take((chosen.size - limit + 1).coerceAtLeast(0))
