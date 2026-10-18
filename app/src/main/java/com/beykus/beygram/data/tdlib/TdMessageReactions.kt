package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.ReactionOption
import org.drinkless.tdlib.TdApi

/** Reactions per row asked from TDLib for the reactions a message can take. TDLib accepts 5 to 25, and the largest is asked for. */
internal const val PICKER_ROW_SIZE = 25

/**
 * Reactions of a message, in the order Telegram sends them. A custom emoji reaction keeps its id and has no emoji text here,
 * since its sticker is looked up separately. Paid reactions are not shown yet, and a reaction without any count is dropped.
 */
internal fun messageReactions(interaction: TdApi.MessageInteractionInfo?): List<MessageReaction> =
    interaction?.reactions?.reactions.orEmpty().mapNotNull { reaction ->
        val option = optionOf(reaction.type) ?: return@mapNotNull null
        if (reaction.totalCount <= 0) null else MessageReaction(option.emoji, reaction.totalCount, reaction.isChosen, option.customEmojiId)
    }

/**
 * Reactions the picker offers: TDLib's top, recent and popular reactions, then the chat's own list, or every active emoji when
 * the chat allows all of them. Premium-only reactions are left out for users without Premium, and so are custom emoji for every
 * user without it, since adding one takes Premium. The chat's list and the active set carry no Premium flags, so they are added
 * for Premium users only. Emoji that differ only by U+FE0F count as one, so a reaction is never listed twice.
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
        .filter { premium || it.customEmojiId == 0L }
    val extra: List<ReactionOption> = if (!premium) emptyList() else when (chat) {
        is TdApi.ChatAvailableReactionsSome -> chat.reactions.orEmpty().mapNotNull(::optionOf)
        is TdApi.ChatAvailableReactionsAll -> activeEmojis.map { ReactionOption(it) }
        else -> emptyList()
    }
    return (offers + extra).distinctBy { option -> option.customEmojiId to option.emoji.filterNot { it == '️' } }
}

/** The reaction a TDLib type stands for; null for paid reactions, which are not shown yet. */
internal fun optionOf(type: TdApi.ReactionType): ReactionOption? = when (type) {
    is TdApi.ReactionTypeEmoji -> ReactionOption(type.emoji)
    is TdApi.ReactionTypeCustomEmoji -> ReactionOption("", type.customEmojiId)
    else -> null
}

/** The TDLib type that adds or removes [option]. */
internal fun reactionTypeOf(option: ReactionOption): TdApi.ReactionType =
    if (option.customEmojiId != 0L) TdApi.ReactionTypeCustomEmoji(option.customEmojiId) else TdApi.ReactionTypeEmoji(option.emoji)

/** Chosen reactions to remove before one more is added, so that the message keeps at most [limit] of them. */
internal fun overflowReactions(chosen: List<TdApi.ReactionType>, limit: Int): List<TdApi.ReactionType> =
    chosen.take((chosen.size - limit + 1).coerceAtLeast(0))
