package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageReaction
import org.drinkless.tdlib.TdApi

/** How many reactions the picker offers; also the row size asked from TDLib for them. */
internal const val PICKER_REACTION_COUNT = 8

/**
 * Emoji reactions of a message, in the order Telegram sends them. Custom emoji and paid reactions are not
 * shown yet, and a reaction without any count is dropped.
 */
internal fun messageReactions(interaction: TdApi.MessageInteractionInfo?): List<MessageReaction> =
    interaction?.reactions?.reactions.orEmpty().mapNotNull { reaction ->
        val emoji = (reaction.type as? TdApi.ReactionTypeEmoji)?.emoji ?: return@mapNotNull null
        if (reaction.totalCount <= 0) null else MessageReaction(emoji, reaction.totalCount, reaction.isChosen)
    }

/** Emoji the picker offers: top, recent, then popular, without duplicates. Premium-only ones are left out unless [premium]. */
internal fun pickerEmojis(available: TdApi.AvailableReactions, premium: Boolean): List<String> =
    (available.topReactions.orEmpty().toList() + available.recentReactions.orEmpty().toList() + available.popularReactions.orEmpty().toList())
        .filter { premium || !it.needsPremium }
        .mapNotNull { (it.type as? TdApi.ReactionTypeEmoji)?.emoji }
        .distinct()
        .take(PICKER_REACTION_COUNT)

/** Chosen reactions to remove before one more is added, so that the message keeps at most [limit] of them. */
internal fun overflowReactions(chosen: List<TdApi.ReactionType>, limit: Int): List<TdApi.ReactionType> =
    chosen.take((chosen.size - limit + 1).coerceAtLeast(0))
