package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.BotWebAppDataError
import com.beykus.beygram.domain.model.BotWebAppTheme
import com.beykus.beygram.domain.model.validateBotWebAppData
import com.beykus.beygram.domain.model.webAppOriginRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramWebAppBridgeTest {
    @Test
    fun `lifecycle events are allowlisted and unknown events are ignored`() {
        assertEquals(TelegramWebAppEvent.Ready, parseTelegramWebAppEvent("web_app_ready", null))
        assertEquals(TelegramWebAppEvent.Close, parseTelegramWebAppEvent("web_app_close", "{}"))
        assertEquals(TelegramWebAppEvent.Expand, parseTelegramWebAppEvent("web_app_expand", "\"\""))
        assertEquals(TelegramWebAppEvent.RequestTheme, parseTelegramWebAppEvent("web_app_request_theme", null))
        assertNull(parseTelegramWebAppEvent("web_app_unknown", "{}"))
        assertNull(parseTelegramWebAppEvent("", "{}"))
        assertNull(parseTelegramWebAppEvent("web_app_ready", "x".repeat(64 * 1024 + 1)))
        assertNull(parseTelegramWebAppEvent("x".repeat(129), "{}"))
    }

    @Test
    fun `main button parses visibility state text colors and progress`() {
        val event = parseTelegramWebAppEvent(
            "web_app_setup_main_button",
            """{"is_visible":true,"is_active":true,"text":" Pay ","color":"#ff0000","text_color":"#0f0","is_progress_visible":true}"""
        ) as TelegramWebAppEvent.SetupMainButton

        assertTrue(event.button.visible)
        assertTrue(event.button.active)
        assertEquals("Pay", event.button.text)
        assertEquals(0xFFFF0000.toInt(), event.button.color)
        assertEquals(0xFF00FF00.toInt(), event.button.textColor)
        assertTrue(event.button.progressVisible)
    }

    @Test
    fun `main button cannot be visible without text and clamps oversized labels`() {
        val hidden = parseTelegramWebAppEvent(
            "web_app_setup_main_button",
            """{"is_visible":true,"is_active":true,"text":"   "}"""
        ) as TelegramWebAppEvent.SetupMainButton
        assertFalse(hidden.button.visible)
        assertFalse(hidden.button.progressVisible)

        val clamped = parseTelegramWebAppEvent(
            "web_app_setup_main_button",
            JSONObject().put("is_visible", true).put("text", "x".repeat(100)).toString()
        ) as TelegramWebAppEvent.SetupMainButton
        assertEquals(64, clamped.button.text.length)
        assertFalse(clamped.button.active)
        assertNull(clamped.button.color)

        val invalidColor = parseTelegramWebAppEvent(
            "web_app_setup_main_button",
            """{"is_visible":true,"text":"Go","color":"red","text_color":"#12345"}"""
        ) as TelegramWebAppEvent.SetupMainButton
        assertNull(invalidColor.button.color)
        assertNull(invalidColor.button.textColor)
    }

    @Test
    fun `main button rejects malformed or non-object payloads`() {
        assertNull(parseTelegramWebAppEvent("web_app_setup_main_button", "not json"))
        assertNull(parseTelegramWebAppEvent("web_app_setup_main_button", "\"text\""))
        assertNull(parseTelegramWebAppEvent("web_app_setup_main_button", """{"is_visible":true} trailing"""))
    }

    @Test
    fun `back button exposes only its visibility flag`() {
        val visible = parseTelegramWebAppEvent("web_app_setup_back_button", """{"is_visible":true}""") as TelegramWebAppEvent.SetupBackButton
        val hidden = parseTelegramWebAppEvent("web_app_setup_back_button", """{"is_visible":false}""") as TelegramWebAppEvent.SetupBackButton
        assertTrue(visible.visible)
        assertFalse(hidden.visible)
        assertNull(parseTelegramWebAppEvent("web_app_setup_back_button", "{"))
    }

    @Test
    fun `send data accepts legacy string and current object encodings`() {
        val legacy = parseTelegramWebAppEvent("web_app_send_data", JSONObject.quote("""{"ok":true}"""))
        val current = parseTelegramWebAppEvent(
            "web_app_data_send",
            JSONObject().put("data", """{"ok":true}""").toString()
        )
        val objectWithoutDataField = parseTelegramWebAppEvent(
            "web_app_send_data",
            JSONObject().put("ok", true).toString()
        )
        assertEquals(TelegramWebAppEvent.SendData("""{"ok":true}"""), legacy)
        assertEquals(TelegramWebAppEvent.SendData("""{"ok":true}"""), current)
        assertEquals(TelegramWebAppEvent.SendData("""{"ok":true}"""), objectWithoutDataField)
        assertEquals(TelegramWebAppEvent.SendData(""), parseTelegramWebAppEvent("web_app_send_data", "\"\""))
        assertNull(parseTelegramWebAppEvent("web_app_send_data", "not json"))
        assertNull(parseTelegramWebAppEvent("web_app_data_send", """{"data":123}"""))
    }

    @Test
    fun `send data validation rejects empty oversized and malformed structured json`() {
        assertNull(validateBotWebAppData("""{"ok":true}"""))
        assertNull(validateBotWebAppData("[1,2,3]"))
        assertNull(validateBotWebAppData("\"text\""))
        // Telegram sendData accepts plain text; only structured payloads are parsed as JSON.
        assertNull(validateBotWebAppData("hello"))
        assertNull(validateBotWebAppData("42"))

        assertEquals(BotWebAppDataError.Empty, validateBotWebAppData(""))
        assertNull(validateBotWebAppData("\"" + "x".repeat(4094) + "\""))
        assertEquals(BotWebAppDataError.TooLarge, validateBotWebAppData("x".repeat(4097)))
        assertEquals(BotWebAppDataError.TooLarge, validateBotWebAppData("\"" + "я".repeat(2048) + "\""))
        assertEquals(BotWebAppDataError.InvalidJson, validateBotWebAppData("""{"ok":true"""))
        assertEquals(BotWebAppDataError.InvalidJson, validateBotWebAppData("""{"ok":true} trailing"""))
        assertEquals(BotWebAppDataError.InvalidJson, validateBotWebAppData("\"unterminated"))
    }

    @Test
    fun `invoice events are parsed but only a safe slug is accepted`() {
        val event = parseTelegramWebAppEvent("web_app_open_invoice", """{"slug":"abc-123_="}""")
        val alias = parseTelegramWebAppEvent("openInvoice", """{"slug":"abc-123"}""")
        assertEquals(TelegramWebAppEvent.OpenInvoice("abc-123_="), event)
        assertEquals(TelegramWebAppEvent.OpenInvoice("abc-123"), alias)
        assertNull(parseTelegramWebAppEvent("web_app_open_invoice", """{"slug":"../../etc/passwd"}"""))
        assertNull(parseTelegramWebAppEvent("web_app_open_invoice", """{"slug":""}"""))
        assertNull(parseTelegramWebAppEvent("web_app_open_invoice", "{}"))
    }

    @Test
    fun `theme payload contains only palette hex colors`() {
        val theme = BotWebAppTheme(
            background = 0xFF112233.toInt(),
            surface = 0xFF223344.toInt(),
            text = 0xFFDDEEFF.toInt(),
            textSecondary = 0xFF99AABB.toInt(),
            accent = 0xFF00AA55.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            divider = 0xFF445566.toInt(),
            danger = 0xFFCC0000.toInt()
        )
        val params = JSONObject(theme.toWebAppThemeChangedJson()).getJSONObject("theme_params")
        assertEquals("#112233", params.getString("bg_color"))
        assertEquals("#223344", params.getString("secondary_bg_color"))
        assertEquals("#ddeeff", params.getString("text_color"))
        assertEquals("#99aabb", params.getString("hint_color"))
        assertEquals("#00aa55", params.getString("button_color"))
        assertEquals("#ffffff", params.getString("button_text_color"))
        assertEquals("#cc0000", params.getString("destructive_text_color"))
    }

    @Test
    fun `origin rule is exact and https only`() {
        assertEquals("https://mini.example", webAppOriginRule("https://Mini.Example/app?x=1"))
        assertEquals("https://mini.example:8443", webAppOriginRule("https://mini.example:8443/app"))
        assertNull(webAppOriginRule("http://mini.example/app"))
        assertNull(webAppOriginRule("javascript:alert(1)"))
        assertNull(webAppOriginRule("not a url"))
    }
}
