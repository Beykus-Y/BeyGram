package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.deeplink.MessageEntityLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEntityTapTest {
    private val mention = MessageEntityAction(0, 6, MessageEntityLink.PublicChat("durov"))
    private val url = MessageEntityAction(7, 26, MessageEntityLink.ExternalUrl("https://example.com"))

    @Test fun `a link owns its exact range`() {
        assertEquals(MessageEntityTap.Link(mention), messageEntityTapAt(0, listOf(mention), emptyList()))
        assertEquals(MessageEntityTap.Link(mention), messageEntityTapAt(5, listOf(mention), emptyList()))
        assertNull(messageEntityTapAt(6, listOf(mention), emptyList()))
        assertEquals(MessageEntityTap.Link(url), messageEntityTapAt(7, listOf(mention, url), emptyList()))
    }

    @Test fun `plain text stays with the message bubble`() {
        assertNull(messageEntityTapAt(100, listOf(mention), emptyList()))
        assertNull(messageEntityTapAt(100, emptyList(), emptyList()))
    }

    @Test fun `a visible link wins over an overlapping spoiler range`() {
        assertEquals(
            MessageEntityTap.Link(mention),
            messageEntityTapAt(2, listOf(mention), listOf(0 until 6))
        )
        assertTrue(messageEntityTapAt(2, emptyList(), listOf(0 until 6)) is MessageEntityTap.Spoiler)
    }

    @Test fun `spoiler boundaries are half open`() {
        assertNull(messageEntityTapAt(6, emptyList(), listOf(0 until 6)))
        assertTrue(messageEntityTapAt(5, emptyList(), listOf(0 until 6)) is MessageEntityTap.Spoiler)
    }
}
