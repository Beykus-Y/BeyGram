package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageReaction
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageReactionsTest {
    private fun reaction(type: TdApi.ReactionType, count: Int, chosen: Boolean = false) =
        TdApi.MessageReaction(type, count, chosen, null, arrayOf())

    private fun interaction(vararg reactions: TdApi.MessageReaction) =
        TdApi.MessageInteractionInfo(0, 0, null, TdApi.MessageReactions(arrayOf(*reactions), false, arrayOf(), false))

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
}
