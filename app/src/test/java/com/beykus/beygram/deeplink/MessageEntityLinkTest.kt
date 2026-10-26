package com.beykus.beygram.deeplink

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import com.beykus.beygram.domain.model.normalizedPublicUsername
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEntityLinkTest {
    private fun linkOf(text: String, value: String, kind: TextStyleKind, argument: String = ""): MessageEntityLink? =
        messageEntityLink(MessageTextEntity(text.indexOf(value), value.length, kind, argument), text)

    @Test fun `mentions resolve users bots and public chats without a browser`() {
        assertEquals(MessageEntityLink.PublicChat("durov"), linkOf("@durov", "@durov", TextStyleKind.Mention))
        assertEquals(MessageEntityLink.PublicChat("my_bot"), linkOf("@my_bot", "@my_bot", TextStyleKind.Mention))
        assertEquals(MessageEntityLink.PublicChat("gif"), linkOf("@gif", "@gif", TextStyleKind.Mention))
    }

    @Test fun `invalid mentions and ordinary text are never looked up`() {
        assertNull(linkOf("@1bot", "@1bot", TextStyleKind.Mention))
        assertNull(linkOf("hello", "hello", TextStyleKind.Bold))
        assertNull(linkOf("hello", "hello", TextStyleKind.Hashtag))
        assertNull(linkOf("hello", "hello", TextStyleKind.Command))
    }

    @Test fun `mention-name entities use the numeric telegram user id`() {
        assertEquals(MessageEntityLink.User(42L), linkOf("Ann", "Ann", TextStyleKind.MentionName, "42"))
        assertNull(linkOf("Ann", "Ann", TextStyleKind.MentionName, "not-a-number"))
    }

    @Test fun `telegram usernames and bots become public chats`() {
        assertEquals(
            MessageEntityLink.PublicChat("durov"),
            linkOf("t.me/durov", "t.me/durov", TextStyleKind.Url)
        )
        assertEquals(
            MessageEntityLink.PublicChat("my_bot"),
            linkOf("https://t.me/my_bot", "https://t.me/my_bot", TextStyleKind.Url)
        )
        assertEquals(
            MessageEntityLink.PublicChat("my_bot"),
            linkOf("site", "site", TextStyleKind.Url, "https://t.me/my_bot?start=one")
        )
        assertEquals(
            MessageEntityLink.PublicChat("durov"),
            linkOf("durov", "durov", TextStyleKind.Url, "tg://resolve?domain=durov")
        )
        assertEquals(
            MessageEntityLink.PublicChat("durov"),
            linkOf("@durov", "@durov", TextStyleKind.Url)
        )
    }

    @Test fun `telegram message invite and user links keep their action`() {
        assertEquals(
            MessageEntityLink.TelegramMessage("https://t.me/durov/123"),
            linkOf("post", "post", TextStyleKind.Url, "https://t.me/durov/123")
        )
        assertEquals(
            MessageEntityLink.TelegramInvite("https://t.me/+AbCdEf"),
            linkOf("invite", "invite", TextStyleKind.Url, "t.me/+AbCdEf")
        )
        assertEquals(
            MessageEntityLink.TelegramPhone("+79991234567"),
            linkOf("phone", "phone", TextStyleKind.Url, "t.me/+79991234567")
        )
        assertEquals(
            MessageEntityLink.User(77L),
            linkOf("user", "user", TextStyleKind.Url, "tg://user?id=77")
        )
    }

    @Test fun `ordinary web email and phone links stay external`() {
        assertEquals(
            MessageEntityLink.ExternalUrl("https://example.com/path"),
            linkOf("https://example.com/path", "https://example.com/path", TextStyleKind.Url)
        )
        assertEquals(
            MessageEntityLink.ExternalUrl("https://example.com/path"),
            linkOf("example.com/path", "example.com/path", TextStyleKind.Url)
        )
        assertEquals(
            MessageEntityLink.Email("a@example.com"),
            linkOf("a@example.com", "a@example.com", TextStyleKind.Email)
        )
        assertEquals(
            MessageEntityLink.Email("a@example.com"),
            linkOf("mail", "mail", TextStyleKind.Url, "mailto:a@example.com")
        )
        assertEquals(
            MessageEntityLink.Phone("+123456"),
            linkOf("+123456", "+123456", TextStyleKind.Phone)
        )
    }

    @Test fun `unsafe schemes never become links`() {
        listOf("javascript:alert(1)", "file:///data/data/private", "intent://foo#Intent;end").forEach { value ->
            assertNull(linkOf(value, value, TextStyleKind.Url))
        }
    }

    @Test fun `copy values are the concrete entity not the whole message`() {
        val text = "Hi @durov and https://t.me/my_bot/5 and https://example.com"
        val entities = listOf(
            MessageTextEntity(text.indexOf("@durov"), "@durov".length, TextStyleKind.Mention),
            MessageTextEntity(text.indexOf("https://t.me/my_bot/5"), "https://t.me/my_bot/5".length, TextStyleKind.Url),
            MessageTextEntity(text.indexOf("https://example.com"), "https://example.com".length, TextStyleKind.Url)
        )
        assertEquals(
            listOf("@durov", "https://t.me/my_bot/5", "https://example.com"),
            copyableMessageLinks(text, entities)
        )
    }

    @Test fun `copy uses a text link destination and removes duplicates`() {
        val text = "site site"
        val entities = listOf(
            MessageTextEntity(0, 4, TextStyleKind.Url, "https://example.com"),
            MessageTextEntity(5, 4, TextStyleKind.Url, "https://example.com")
        )
        assertEquals(listOf("https://example.com"), copyableMessageLinks(text, entities))
    }

    @Test fun `spoiler-hidden links are neither opened nor copied`() {
        val text = "secret"
        val entities = listOf(
            MessageTextEntity(0, 6, TextStyleKind.Spoiler),
            MessageTextEntity(0, 6, TextStyleKind.Mention)
        )
        assertTrue(messageEntityLinks(text, entities).isEmpty())
        assertTrue(copyableMessageLinks(text, entities).isEmpty())
    }

    @Test fun `public username normalization accepts legacy short and bot names`() {
        assertEquals("durov", normalizedPublicUsername("@durov"))
        assertEquals("gif", normalizedPublicUsername("gif"))
        assertEquals("my_bot", normalizedPublicUsername(" my_bot "))
        assertNull(normalizedPublicUsername("@1bot"))
        assertNull(normalizedPublicUsername("two words"))
        assertNull(normalizedPublicUsername(""))
    }
}
