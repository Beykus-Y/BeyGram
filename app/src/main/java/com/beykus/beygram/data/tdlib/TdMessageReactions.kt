package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageReaction
import org.drinkless.tdlib.TdApi

/**
 * Emoji reactions of a message, in the order Telegram sends them. Custom emoji and paid reactions are not
 * shown yet, and a reaction without any count is dropped.
 */
internal fun messageReactions(interaction: TdApi.MessageInteractionInfo?): List<MessageReaction> =
    interaction?.reactions?.reactions.orEmpty().mapNotNull { reaction ->
        val emoji = (reaction.type as? TdApi.ReactionTypeEmoji)?.emoji ?: return@mapNotNull null
        if (reaction.totalCount <= 0) null else MessageReaction(emoji, reaction.totalCount, reaction.isChosen)
    }
