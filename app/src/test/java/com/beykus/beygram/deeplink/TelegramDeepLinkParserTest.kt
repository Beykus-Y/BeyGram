package com.beykus.beygram.deeplink

import com.beykus.beygram.domain.model.TelegramProxyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramDeepLinkParserTest {
    @Test
    fun publicUsernamesAreParsedFromBothHostsAndTheTgScheme() {
        assertEquals(
            TelegramDeepLink.Username("durov"),
            TelegramDeepLinkParser.parse("https://t.me/durov")
        )
        assertEquals(
            TelegramDeepLink.Username("durov"),
            TelegramDeepLinkParser.parse("https://telegram.me/durov/")
        )
        assertEquals(
            TelegramDeepLink.Username("Durov_Support"),
            TelegramDeepLinkParser.parse("tg://resolve?domain=Durov_Support")
        )
        assertEquals(
            TelegramDeepLink.Username("durov"),
            TelegramDeepLinkParser.parse("https://T.ME/durov")
        )
    }

    @Test
    fun botStartPayloadIsKeptButTheRouterOnlyOpensTheChat() {
        val link = TelegramDeepLinkParser.parse("https://t.me/BeyGram_Bot?start=diagnostics")
        assertEquals(TelegramDeepLink.Bot("BeyGram_Bot", "diagnostics"), link)
        assertEquals(
            TelegramDeepLinkRoute.Chat("BeyGram_Bot"),
            TelegramDeepLinkRouter.route(link)
        )
        assertEquals(
            TelegramDeepLink.Bot("BeyGram_Bot", "diagnostics"),
            TelegramDeepLinkParser.parse("tg://resolve?domain=BeyGram_Bot&start=diagnostics")
        )
    }

    @Test
    fun tgUserLinksOpenThePrivateChatWithoutASecondLookupService() {
        val link = TelegramDeepLinkParser.parse("tg://user?id=4242")
        assertEquals(TelegramDeepLink.UserId(4242L), link)
        assertEquals(TelegramDeepLinkRoute.UserId(4242L), TelegramDeepLinkRouter.route(link))
        assertNull(TelegramDeepLinkParser.parse("tg://user"))
        assertNull(TelegramDeepLinkParser.parse("tg://user?id=0"))
        assertNull(TelegramDeepLinkParser.parse("tg://user?id=not-a-number"))
    }

    @Test
    fun inviteFormsShareOneCanonicalUrl() {
        val expected = TelegramDeepLink.Invite("https://t.me/+AbCdEf_123")
        assertEquals(expected, TelegramDeepLinkParser.parse("https://t.me/+AbCdEf_123"))
        assertEquals(expected, TelegramDeepLinkParser.parse("https://t.me/joinchat/AbCdEf_123"))
        assertEquals(expected, TelegramDeepLinkParser.parse("tg://join?invite=AbCdEf_123"))
        assertEquals(
            TelegramDeepLinkRoute.Invite(expected.url),
            TelegramDeepLinkRouter.route(expected)
        )
    }

    @Test
    fun phoneLinksAreNotMistakenForInvites() {
        assertEquals(
            TelegramDeepLink.Phone("+79991234567"),
            TelegramDeepLinkParser.parse("https://t.me/+79991234567")
        )
        assertEquals(
            TelegramDeepLink.Phone("+79991234567"),
            TelegramDeepLinkParser.parse("tg://resolve?phone=79991234567")
        )
        assertEquals(
            TelegramDeepLinkRoute.Phone("+79991234567"),
            TelegramDeepLinkRouter.route(TelegramDeepLinkParser.parse("tg://resolve?phone=%2B79991234567"))
        )
    }

    @Test
    fun publicMessageLinksKeepTheMessageIdAndCanonicalUrl() {
        assertEquals(
            TelegramDeepLink.Message("https://t.me/durov/123", 123),
            TelegramDeepLinkParser.parse("https://t.me/durov/123")
        )
        assertEquals(
            TelegramDeepLink.Message("https://t.me/durov/123?thread=77", 123),
            TelegramDeepLinkParser.parse("https://t.me/durov/123?thread=77&single")
        )
        assertEquals(
            TelegramDeepLink.Message("https://t.me/durov/77/123", 123),
            TelegramDeepLinkParser.parse("https://t.me/durov/77/123")
        )
        assertEquals(
            TelegramDeepLink.Message("https://t.me/durov/123", 123),
            TelegramDeepLinkParser.parse("https://t.me/durov?post=123")
        )
    }

    @Test
    fun privateAndTopicMessageLinksAreParsedWithoutTrustingTheNumericPath() {
        assertEquals(
            TelegramDeepLink.Message("https://t.me/c/1234567890/456", 456),
            TelegramDeepLinkParser.parse("https://t.me/c/1234567890/456")
        )
        assertEquals(
            TelegramDeepLink.Message("https://t.me/c/1234567890/77/456", 456),
            TelegramDeepLinkParser.parse("https://t.me/c/1234567890/77/456")
        )
        assertEquals(
            TelegramDeepLink.Message("https://t.me/durov/123", 123),
            TelegramDeepLinkParser.parse("tg://resolve?domain=durov&post=123")
        )
    }

    @Test
    fun proxyLinksRequireCompleteValidConnectionDetails() {
        val mtproto = TelegramDeepLinkParser.parse(
            "tg://proxy?server=proxy.example&port=443&secret=dd0123456789abcdef"
        )
        assertEquals(TelegramDeepLink.Proxy::class.java, mtproto?.javaClass)
        mtproto as TelegramDeepLink.Proxy
        assertEquals(TelegramProxyKind.MTPROTO, mtproto.draft.kind)
        assertEquals("proxy.example", mtproto.draft.server)
        assertEquals(443, mtproto.draft.port)
        assertEquals("dd0123456789abcdef", mtproto.draft.secret)
        assertEquals(
            TelegramDeepLinkRoute.Proxy(mtproto.draft),
            TelegramDeepLinkRouter.route(mtproto)
        )

        val socks = TelegramDeepLinkParser.parse(
            "tg://socks?server=127.0.0.1&port=1080&user=name&pass=secret"
        ) as TelegramDeepLink.Proxy
        assertEquals(TelegramProxyKind.SOCKS5, socks.draft.kind)
        assertEquals("name", socks.draft.username)
        assertEquals("secret", socks.draft.password)

        val utf8 = TelegramDeepLinkParser.parse(
            "tg://socks?server=127.0.0.1&port=1080&user=test&pass=%C3%A9"
        ) as TelegramDeepLink.Proxy
        assertEquals("é", utf8.draft.password)
    }

    @Test
    fun loginLinksAreRecognizedButNeverBecomeAQuery() {
        val login = TelegramDeepLinkParser.parse("tg://login?token=superSecretToken")
        assertEquals(TelegramDeepLink.Login, login)
        assertEquals(TelegramDeepLinkRoute.Ignore, TelegramDeepLinkRouter.route(login))
        assertFalse(login.toString().contains("superSecretToken"))
        assertFalse(TelegramDeepLinkParser.parse("https://t.me/login?token=1").let {
            it is TelegramDeepLink.Username
        })
    }

    @Test
    fun malformedAndArbitraryUrisReturnNullWithoutThrowing() {
        val rejected = listOf(
            null,
            "",
            " ",
            "http://t.me/durov",
            "https://example.com/durov",
            "https://t.me.evil.com/durov",
            "https://evil.com/t.me/durov",
            "https://t.me@evil.com/durov",
            "https://t.me:443/durov",
            "https://t.me/",
            "https://t.me//durov",
            "https://t.me/durov//123",
            "https://t.me/joinchat",
            "https://t.me/joinchat/",
            "https://t.me/+",
            "https://t.me/login",
            "https://t.me/share/url?url=https://evil.com",
            "https://t.me/addstickers/abc",
            "https://t.me/durov/0",
            "https://t.me/durov/-1",
            "https://t.me/durov/not-a-number",
            "https://t.me/c/abc/1",
            "https://t.me/c/1/abc",
            "https://t.me/durov%2F123",
            "https://t.me/durov?start=%00",
            "https://t.me/durov?start=%ZZ",
            "https://t.me/durov?start=a&start=b",
            "tg://unknown?x=1",
            "tg://resolve",
            "tg://resolve?domain=",
            "tg://resolve?domain=bad%20name",
            "tg://resolve?domain=durov&post=0",
            "tg://resolve?domain=durov&phone=79991234567",
            "tg://join",
            "tg://join?invite=",
            "tg://join?invite=bad%2Fhash",
            "tg://proxy?server=proxy.example&port=0&secret=dd11",
            "tg://proxy?server=proxy.example&port=443",
            "tg://proxy?server=proxy.example&port=443&secret=",
            "tg://proxy?server=bad%20host&port=443&secret=dd11",
            "tg://login?token=one&token=two",
            "javascript:alert(1)",
            "intent://example/#Intent;scheme=https;end",
            "https://t.me/durov\nhttps://evil.com",
            "https://t.me/" + "a".repeat(300),
            "https://t.me/" + "a".repeat(65) + "/1"
        )
        rejected.forEach { raw ->
            assertNull("Accepted invalid link: ${raw?.replace("\n", "\\n")}", TelegramDeepLinkParser.parse(raw))
        }
    }

    @Test
    fun arbitraryValuesNeverCrashTheParser() {
        val values = listOf(
            "https://t.me/%", "https://t.me/%0", "https://t.me/%GG",
            "tg://proxy?server=%&port=%&secret=%",
            "tg://resolve?domain=a&post=999999999999999999999999999",
            "https://t.me/c/999999999999999999999999/1",
            "https://t.me/+%2B%2B%2B"
        )
        values.forEach { raw ->
            assertNull(TelegramDeepLinkParser.parse(raw))
        }
    }

    @Test
    fun parserNeverKeepsProxySecretsInDiagnosticToString() {
        val proxy = TelegramDeepLinkParser.parse(
            "tg://proxy?server=proxy.example&port=443&secret=verySecretProxyValue"
        ) as TelegramDeepLink.Proxy
        assertFalse(proxy.toString().contains("verySecretProxyValue"))
        assertTrue(proxy.toString().contains("proxy.example"))
    }
}
