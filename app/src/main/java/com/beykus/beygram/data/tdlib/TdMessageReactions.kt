package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageReaction
import org.drinkless.tdlib.TdApi

/** Reactions per row asked from TDLib for the reactions a message can take. TDLib accepts 5 to 25, and the largest is asked for. */
internal const val PICKER_ROW_SIZE = 25

/**
 * Emoji reactions of a message, in the order Telegram sends them. Custom emoji and paid reactions are not
 * shown yet, and a reaction without any count is dropped.
 */
internal fun messageReactions(interaction: TdApi.MessageInteractionInfo?): List<MessageReaction> =
    interaction?.reactions?.reactions.orEmpty().mapNotNull { reaction ->
        val emoji = emojiOf(reaction.type) ?: return@mapNotNull null
        if (reaction.totalCount <= 0) null else MessageReaction(emoji, reaction.totalCount, reaction.isChosen)
    }

/**
 * Emoji the picker offers: TDLib's top, recent and popular reactions, then what the chat adds. A chat with its own list adds
 * that list, and a chat that allows every reaction adds the active emoji. Premium-only ones are left out unless [premium].
 * Custom emoji are not offered yet. Emoji that differ only by U+FE0F count as one, so a reaction is never listed twice.
 */
internal fun pickerEmojis(
    available: TdApi.AvailableReactions,
    chat: TdApi.ChatAvailableReactions?,
    activeEmojis: List<String>,
    premium: Boolean
): List<String> {
    val offers = available.topReactions.orEmpty().toList() + available.recentReactions.orEmpty().toList() + available.popularReactions.orEmpty().toList()
    val premiumOnly = offers.filter { it.needsPremium }.mapNotNullTo(hashSetOf()) { emojiOf(it.type) }
    val chatEmojis = when (chat) {
        is TdApi.ChatAvailableReactionsSome -> chat.reactions.orEmpty().mapNotNull(::emojiOf)
        is TdApi.ChatAvailableReactionsAll -> activeEmojis
        else -> emptyList()
    }
    return (offers.filter { premium || !it.needsPremium }.mapNotNull { emojiOf(it.type) } + chatEmojis.filter { premium || it !in premiumOnly })
        .distinctBy { emoji -> emoji.filterNot { it == '\uFE0F' } }
}

private fun emojiOf(type: TdApi.ReactionType): String? = (type as? TdApi.ReactionTypeEmoji)?.emoji

/** Chosen reactions to remove before one more is added, so that the message keeps at most [limit] of them. */
internal fun overflowReactions(chosen: List<TdApi.ReactionType>, limit: Int): List<TdApi.ReactionType> =
    chosen.take((chosen.size - limit + 1).coerceAtLeast(0))
