package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageReaction
import com.beykus.beygram.domain.model.ReactionOption
import com.beykus.beygram.domain.model.reactionMessage
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageReactionsTest {
    private fun reaction(type: TdApi.ReactionType, count: Int, chosen: Boolean = false) =
        TdApi.MessageReaction(type, count, chosen, null, arrayOf())

    private fun interaction(vararg reactions: TdApi.MessageReaction) =
        TdApi.MessageInteractionInfo(0, 0, null, TdApi.MessageReactions(arrayOf(*reactions), false, arrayOf(), false))

    private fun offer(emoji: String, premium: Boolean = false) = TdApi.AvailableReaction(TdApi.ReactionTypeEmoji(emoji), premium)

    private fun available(
        top: List<TdApi.AvailableReaction> = emptyList(),
        recent: List<TdApi.AvailableReaction> = emptyList(),
        popular: List<TdApi.AvailableReaction> = emptyList()
    ) = TdApi.AvailableReactions(top.toTypedArray(), recent.toTypedArray(), popular.toTypedArray(), false, false, null)

    private fun message(id: Long, date: Long, vararg reactions: MessageReaction) =
        ChatMessage(id = id, chatId = 1L, text = "", time = "", isOutgoing = false, dateSeconds = date, reactions = reactions.toList())

    private fun emojisOf(options: List<ReactionOption>) = options.map { it.emoji }

    @Test fun `missing interaction info or reactions give no chips`() {
        assertEquals(emptyList<MessageReaction>(), messageReactions(null))
        assertEquals(emptyList<MessageReaction>(), messageReactions(TdApi.MessageInteractionInfo(0, 0, null, null)))
    }

    @Test fun `emoji reactions keep the server order and the chosen flag`() {
        val chips = messageReactions(interaction(
            reaction(TdApi.ReactionTypeEmoji("🔥"), 4),
            reaction(TdApi.ReactionTypeEmoji("👍"), 1, chosen = true)
        ))
        assertEquals(listOf(MessageReaction("🔥", 4, false), MessageReaction("👍", 1, true)), chips)
    }

    @Test fun `custom emoji keep their id, while paid and zero-count reactions are left out`() {
        val chips = messageReactions(interaction(
            reaction(TdApi.ReactionTypeCustomEmoji(42L), 3),
            reaction(TdApi.ReactionTypePaid(), 5),
            reaction(TdApi.ReactionTypeEmoji("❤"), 0),
            reaction(TdApi.ReactionTypeEmoji("😂"), 2)
        ))
        assertEquals(listOf(MessageReaction("", 3, false, 42L), MessageReaction("😂", 2, false)), chips)
    }

    @Test fun `a chosen custom emoji has the same option as its picker entry`() {
        val chosen = messageReactions(interaction(reaction(TdApi.ReactionTypeCustomEmoji(42L), 1, chosen = true))).single()
        assertEquals(ReactionOption("", 42L), chosen.option)
        assertEquals(ReactionOption("", 42L), optionOf(TdApi.ReactionTypeCustomEmoji(42L)))
    }

    @Test fun `an option sends the TDLib type it stands for`() {
        assertEquals(42L, (reactionTypeOf(ReactionOption("", 42L)) as TdApi.ReactionTypeCustomEmoji).customEmojiId)
        assertEquals("🔥", (reactionTypeOf(ReactionOption("🔥")) as TdApi.ReactionTypeEmoji).emoji)
    }

    @Test fun `picker lists top, recent and popular emoji once each`() {
        val emojis = emojisOf(pickerOptions(
            available(top = listOf(offer("🔥"), offer("👍")), recent = listOf(offer("👍"), offer("❤")), popular = listOf(offer("😂"), offer("🔥"))),
            chat = null, activeEmojis = emptyList(), premium = false
        ))
        assertEquals(listOf("🔥", "👍", "❤", "😂"), emojis)
    }

    @Test fun `premium-only emoji are left out unless the user has Premium`() {
        val reactions = available(top = listOf(offer("🔥"), offer("💎", premium = true)))
        assertEquals(listOf("🔥"), emojisOf(pickerOptions(reactions, chat = null, activeEmojis = emptyList(), premium = false)))
        assertEquals(listOf("🔥", "💎"), emojisOf(pickerOptions(reactions, chat = null, activeEmojis = emptyList(), premium = true)))
    }

    @Test fun `custom emoji are offered to Premium users only, and the picker is not capped`() {
        val custom = TdApi.AvailableReaction(TdApi.ReactionTypeCustomEmoji(7L), false)
        val reactions = available(top = listOf(custom) + (1..12).map { offer("e$it") })
        assertEquals(listOf(ReactionOption("", 7L)) + (1..12).map { ReactionOption("e$it") }, pickerOptions(reactions, chat = null, activeEmojis = emptyList(), premium = true))
        assertEquals((1..12).map { ReactionOption("e$it") }, pickerOptions(reactions, chat = null, activeEmojis = emptyList(), premium = false))
    }

    @Test fun `a chat with its own list adds those reactions after TDLib's for Premium users`() {
        val chat = TdApi.ChatAvailableReactionsSome(arrayOf(TdApi.ReactionTypeEmoji("🎉"), TdApi.ReactionTypeCustomEmoji(9L), TdApi.ReactionTypeEmoji("🔥")), 3)
        val reactions = available(top = listOf(offer("🔥")))
        assertEquals(listOf(ReactionOption("🔥"), ReactionOption("🎉"), ReactionOption("", 9L)), pickerOptions(reactions, chat, activeEmojis = listOf("👍"), premium = true))
        assertEquals(listOf(ReactionOption("🔥")), pickerOptions(reactions, chat, activeEmojis = listOf("👍"), premium = false))
    }

    @Test fun `a chat that allows every reaction offers every active emoji to Premium users only`() {
        val reactions = available(top = listOf(offer("🔥")))
        assertEquals(listOf("🔥", "👍", "❤"), emojisOf(pickerOptions(reactions, TdApi.ChatAvailableReactionsAll(3), activeEmojis = listOf("👍", "🔥", "❤"), premium = true)))
        assertEquals(listOf("🔥"), emojisOf(pickerOptions(reactions, TdApi.ChatAvailableReactionsAll(3), activeEmojis = listOf("👍", "🔥", "❤"), premium = false)))
    }

    @Test fun `chat list and active emoji that may need Premium are not added without Premium`() {
        val chat = TdApi.ChatAvailableReactionsSome(arrayOf(TdApi.ReactionTypeEmoji("💎"), TdApi.ReactionTypeEmoji("🎉")), 3)
        val reactions = available(top = listOf(offer("💎", premium = true)))
        assertEquals(emptyList<String>(), emojisOf(pickerOptions(reactions, chat, activeEmojis = emptyList(), premium = false)))
        assertEquals(listOf("💎", "🎉"), emojisOf(pickerOptions(reactions, chat, activeEmojis = emptyList(), premium = true)))
    }

    @Test fun `emoji that differ only by the U+FE0F selector are listed once`() {
        val emojis = emojisOf(pickerOptions(available(top = listOf(offer("❤"))), TdApi.ChatAvailableReactionsAll(3), activeEmojis = listOf("❤️", "👍"), premium = true))
        assertEquals(listOf("❤", "👍"), emojis)
    }

    @Test fun `a full reaction limit makes room for one more`() {
        val fire = TdApi.ReactionTypeEmoji("🔥")
        val thumb = TdApi.ReactionTypeEmoji("👍")
        val heart = TdApi.ReactionTypeEmoji("❤")
        assertEquals(emptyList<TdApi.ReactionType>(), overflowReactions(emptyList(), 1))
        assertEquals(listOf(fire), overflowReactions(listOf(fire), 1))
        assertEquals(emptyList<TdApi.ReactionType>(), overflowReactions(listOf(fire), 3))
        assertEquals(listOf(fire), overflowReactions(listOf(fire, thumb, heart), 3))
        assertEquals(listOf(fire, thumb), overflowReactions(listOf(fire, thumb), 1))
    }

    @Test fun `album reactions come from the first message in time order that has any`() {
        val album = listOf(message(3, 30), message(1, 10, MessageReaction("🔥", 2)), message(2, 20, MessageReaction("👍", 1)))
        assertEquals(1L, reactionMessage(album).id)
    }

    @Test fun `album without reactions is carried by its latest message`() {
        assertEquals(3L, reactionMessage(listOf(message(2, 20), message(3, 30), message(1, 10))).id)
    }

    @Test fun `messages with the same date are ordered by id`() {
        assertEquals(5L, reactionMessage(listOf(message(5, 10), message(4, 10))).id)
    }
}
