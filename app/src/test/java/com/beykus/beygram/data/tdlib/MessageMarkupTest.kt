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

    @Test fun `reply keyboard hide removes layout and force reply retains target and placeholder`() {
        val message = TdApi.Message().apply { id = 123; replyMarkup = TdApi.ReplyMarkupForceReply(false, "Answer") }
        assertEquals(BotKeyboard(123, emptyList(), placeholder = "Answer", forceReply = true), botKeyboard(message))
        message.replyMarkup = TdApi.ReplyMarkupRemoveKeyboard()
        assertNull(botKeyboard(message))
        assertNull(botKeyboard(null))
    }

    @Test fun `web app buttons keep their URL for the TDLib launch path`() {
        val markup = TdApi.ReplyMarkupInlineKeyboard(arrayOf(
            arrayOf(TdApi.InlineKeyboardButton().apply {
                text = "Mini App"
                type = TdApi.InlineKeyboardButtonTypeWebApp("https://mini.example/app")
            })
        ))
        val button = inlineKeyboard(markup).single().single()
        assertEquals(BotButtonKind.WebApp, button.kind)
        assertEquals("https://mini.example/app", button.value)

        val reply = TdApi.Message().apply {
            id = 789
            replyMarkup = TdApi.ReplyMarkupShowKeyboard().apply {
                rows = arrayOf(arrayOf(TdApi.KeyboardButton().apply {
                    text = "Open"
                    type = TdApi.KeyboardButtonTypeWebApp("https://mini.example/reply")
                }))
            }
        }
        val keyboardButton = botKeyboard(reply)!!.rows.single().single()
        assertEquals(BotButtonKind.WebApp, keyboardButton.kind)
        assertEquals("https://mini.example/reply", keyboardButton.value)
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
