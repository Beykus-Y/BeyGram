package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.*
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class MessageMarkupTest {
    @Test fun `UTF16 styles and hidden links survive an outgoing incoming round trip`() {
        val text = "😀 hello link"
        val entities = listOf(MessageTextEntity(3, 5, TextStyleKind.Bold), MessageTextEntity(9, 4, TextStyleKind.Url, "https://example.com"))
        assertEquals(entities, formattedText(text, entities).domainEntities())
    }

    @Test fun `captions retain nested styles and spoiler`() {
        val caption = TdApi.FormattedText("secret photo", arrayOf(
            TdApi.TextEntity(0, 6, TdApi.TextEntityTypeSpoiler()),
            TdApi.TextEntity(0, 6, TdApi.TextEntityTypeItalic())
        ))
        val photo = TdApi.MessagePhoto().apply { this.caption = caption }
        assertEquals(listOf(TextStyleKind.Spoiler, TextStyleKind.Italic), messageFormattedText(photo)!!.domainEntities().map { it.kind })
    }

    @Test fun `binary callback data and keyboard rows are preserved without decoding as text`() {
        val data = byteArrayOf(0, -1, -128, 42)
        val markup = TdApi.ReplyMarkupInlineKeyboard(arrayOf(
            arrayOf(TdApi.InlineKeyboardButton().apply { text = "Go"; type = TdApi.InlineKeyboardButtonTypeCallback(data) }),
            arrayOf(TdApi.InlineKeyboardButton().apply { text = "Copy"; type = TdApi.InlineKeyboardButtonTypeCopyText("value") })
        ))
        val keyboard = inlineKeyboard(markup)
        assertEquals(2, keyboard.size)
        assertArrayEquals(data, keyboard[0][0].data.toByteArray())
        assertEquals(BotButtonKind.Copy, keyboard[1][0].kind)
        assertEquals("value", keyboard[1][0].value)
        assertTrue(inlineKeyboard(null).isEmpty())
    }

    @Test fun `inline custom emoji keeps the original alternate text and offsets`() {
        val text = "hi 😀 wow 🎉"
        val happy = text.indexOf("😀")
        val party = text.indexOf("🎉")
        val entities = listOf(
            MessageTextEntity(0, text.length, TextStyleKind.Bold),
            MessageTextEntity(happy, 2, TextStyleKind.CustomEmoji, "7"),
            MessageTextEntity(party, 2, TextStyleKind.CustomEmoji, "8")
        )

        val runs = inlineCustomEmojiRuns(text, entities, enabled = true)

        assertEquals(listOf(happy, party), runs.map { it.offset })
        assertEquals(listOf("😀", "🎉"), runs.map { it.alternate })
        assertEquals(2, runs.first().length)
        // The alternate text is the original substring, so replacing each run keeps every annotated offset stable.
        val rebuilt = StringBuilder(text)
        runs.asReversed().forEach { rebuilt.replace(it.offset, it.end, it.alternate) }
        assertEquals(text, rebuilt.toString())
    }

    @Test fun `inline custom emoji respects the cap and the global switch`() {
        val text = "😀😀😀"
        val entities = text.indices.filter { it % 2 == 0 }.map {
            MessageTextEntity(it, 2, TextStyleKind.CustomEmoji, "${it + 1}")
        }

        assertEquals(2, inlineCustomEmojiRuns(text, entities, enabled = true, limit = 2).size)
        assertTrue(inlineCustomEmojiRuns(text, entities, enabled = false).isEmpty())
        assertTrue(inlineCustomEmojiRuns(text, entities, enabled = true, limit = 0).isEmpty())
    }

    @Test fun `malformed custom emoji entities are ignored`() {
        val text = "plain"
        val entities = listOf(
            MessageTextEntity(0, 99, TextStyleKind.CustomEmoji, "7"),
            MessageTextEntity(2, 1, TextStyleKind.CustomEmoji, "not-a-number"),
            MessageTextEntity(3, 1, TextStyleKind.Bold)
        )
        assertTrue(inlineCustomEmojiRuns(text, entities, enabled = true).isEmpty())
    }

    @Test fun `reply keyboard hide removes layout and force reply retains target and placeholder`() {
        val message = TdApi.Message().apply { id = 123; replyMarkup = TdApi.ReplyMarkupForceReply(false, "Answer") }
        assertEquals(BotKeyboard(123, emptyList(), placeholder = "Answer", forceReply = true), botKeyboard(message))
        message.replyMarkup = TdApi.ReplyMarkupRemoveKeyboard()
        assertNull(botKeyboard(message))
        assertNull(botKeyboard(null))
    }

    @Test fun `one time reply keyboard retains text buttons and unsupported actions distinctly`() {
        val message = TdApi.Message().apply {
            id = 456
            replyMarkup = TdApi.ReplyMarkupShowKeyboard().apply {
                oneTime = true
                isPersistent = true
                inputFieldPlaceholder = "Choose"
                rows = arrayOf(arrayOf(
                    TdApi.KeyboardButton().apply { text = "Yes"; type = TdApi.KeyboardButtonTypeText() },
                    TdApi.KeyboardButton().apply { text = "Contact"; type = TdApi.KeyboardButtonTypeRequestPhoneNumber() }
                ))
            }
        }
        val keyboard = botKeyboard(message)!!
        assertTrue(keyboard.oneTime)
        assertTrue(keyboard.persistent)
        assertEquals("Choose", keyboard.placeholder)
        assertEquals(listOf(BotButtonKind.Text, BotButtonKind.Unsupported), keyboard.rows.single().map { it.kind })
    }
}
