package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotFeaturesTest {
    @Test
    fun `inline mention is parsed only at the start of the draft`() {
        assertEquals(ParsedInlineQuery("inline_bot", "cats"), parseInlineQuery("@inline_bot cats"))
        assertEquals(ParsedInlineQuery("inline_bot", ""), parseInlineQuery("@inline_bot"))
        assertEquals(ParsedInlineQuery("inline_bot", "two words"), parseInlineQuery("@inline_bot   two words"))
        assertEquals(ParsedInlineQuery("gif", "cats"), parseInlineQuery("@gif cats"))
        assertNull(parseInlineQuery("hello @inline_bot cats"))
        assertNull(parseInlineQuery("@ab cats"))
        assertNull(parseInlineQuery("@inline.bot cats"))
        assertNull(parseInlineQuery("@inline_bot, cats"))
    }

    @Test
    fun `inline query keeps multiline text and trims outer spaces`() {
        assertEquals(ParsedInlineQuery("inline_bot", "first\nsecond"), parseInlineQuery("@inline_bot  first\nsecond  "))
    }

    @Test
    fun `inline pages accumulate in order and duplicate ids are dropped`() {
        val first = listOf(inlineResult("a"), inlineResult("b"), inlineResult("a"))
        val second = listOf(inlineResult("b"), inlineResult("c"), inlineResult("d"), inlineResult("c"))
        assertEquals(listOf("a", "b", "c", "d"), mergeInlineBotResults(first, second).map { it.id })
        assertEquals(listOf("a", "b"), mergeInlineBotResults(first, emptyList()).map { it.id })
        assertEquals(listOf("c"), mergeInlineBotResults(emptyList(), listOf(inlineResult("c"))).map { it.id })
    }

    @Test
    fun `pagination stops on a blank or repeated cursor`() {
        assertEquals("next-page", nextInlineQueryOffset("", "next-page"))
        assertEquals("", nextInlineQueryOffset("same", "same"))
        assertEquals("", nextInlineQueryOffset("previous", ""))
    }

    @Test
    fun `web app accepts only https without credentials`() {
        assertEquals("https://mini.example/app?x=1", normalizedWebAppUrl("https://mini.example/app?x=1"))
        assertNull(normalizedWebAppUrl("http://mini.example/app"))
        assertNull(normalizedWebAppUrl("javascript:alert(1)"))
        assertNull(normalizedWebAppUrl("file:///data/local/tmp/x"))
        assertNull(normalizedWebAppUrl("content://com.example/x"))
        assertNull(normalizedWebAppUrl("intent://mini.example#Intent;end"))
        assertNull(normalizedWebAppUrl("tg://resolve?domain=bot"))
        assertNull(normalizedWebAppUrl("https://user:pass@mini.example/app"))
        assertNull(normalizedWebAppUrl("https://mini.example:0/app"))
        assertNull(normalizedWebAppUrl("not a url"))
    }

    @Test
    fun `same origin compares scheme host and effective port`() {
        assertTrue(isAllowedWebAppNavigation("https://Mini.Example/app", "https://mini.example:443/next", requireSameOrigin = true))
        assertFalse(isAllowedWebAppNavigation("https://mini.example/app", "https://other.example/app", requireSameOrigin = true))
        assertFalse(isAllowedWebAppNavigation("https://mini.example/app", "https://sub.mini.example/app", requireSameOrigin = true))
        assertFalse(isAllowedWebAppNavigation("https://mini.example/app", "https://mini.example:8443/app", requireSameOrigin = true))
        assertFalse(isAllowedWebAppNavigation("https://mini.example/app", "http://mini.example/app", requireSameOrigin = true))
    }

    @Test
    fun `unrestricted web app may follow https but never another scheme`() {
        assertTrue(isAllowedWebAppNavigation("https://mini.example/app", "https://auth.example/login", requireSameOrigin = false))
        assertFalse(isAllowedWebAppNavigation("https://mini.example/app", "http://auth.example/login", requireSameOrigin = false))
        assertFalse(isAllowedWebAppNavigation("https://mini.example/app", "intent://auth.example#Intent;end", requireSameOrigin = false))
    }

    private fun inlineResult(id: String) = InlineBotResult(id, id, "", InlineBotResultKind.Article)
}
