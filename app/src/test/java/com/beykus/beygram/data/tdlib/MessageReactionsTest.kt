package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageReaction
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

    @Test fun `custom emoji, paid and zero-count reactions are left out`() {
        val chips = messageReactions(interaction(
            reaction(TdApi.ReactionTypeCustomEmoji(42L), 3),
            reaction(TdApi.ReactionTypePaid(), 5),
            reaction(TdApi.ReactionTypeEmoji("❤"), 0),
            reaction(TdApi.ReactionTypeEmoji("😂"), 2)
        ))
        assertEquals(listOf(MessageReaction("😂", 2, false)), chips)
    }

    @Test fun `picker lists top, recent and popular emoji once each`() {
        val emojis = pickerEmojis(
            available(top = listOf(offer("🔥"), offer("👍")), recent = listOf(offer("👍"), offer("❤")), popular = listOf(offer("😂"), offer("🔥"))),
            premium = false
        )
        assertEquals(listOf("🔥", "👍", "❤", "😂"), emojis)
    }

    @Test fun `premium-only emoji are left out unless the user has Premium`() {
        val reactions = available(top = listOf(offer("🔥"), offer("💎", premium = true)))
        assertEquals(listOf("🔥"), pickerEmojis(reactions, premium = false))
        assertEquals(listOf("🔥", "💎"), pickerEmojis(reactions, premium = true))
    }

    @Test fun `custom emoji are not offered and the picker holds eight at most`() {
        val custom = TdApi.AvailableReaction(TdApi.ReactionTypeCustomEmoji(7L), false)
        val emojis = pickerEmojis(available(top = listOf(custom) + (1..10).map { offer("e$it") }), premium = true)
        assertEquals((1..8).map { "e$it" }, emojis)
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
