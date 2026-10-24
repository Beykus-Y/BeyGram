package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrLoginTest {
    private val token = "AQADtE7Yk2s4s5vW_2x-9Q"
    private val link = "tg://login?token=$token"

    @Test
    fun validTdLibLinkIsParsedAndReconstructedWithoutChanges() {
        val parsed = QrLoginToken.parse(link)
        assertEquals(token, parsed?.value)
        assertEquals(link, parsed?.link)
        assertEquals(parsed, QrLoginLinks.token(link))
    }

    @Test
    fun malformedLinksAreRejected() {
        val tooLong = "tg://login?token=" + "a".repeat(QrLoginLinks.MAX_TOKEN_LENGTH + 1)
        val malformed = listOf(
            null,
            "",
            " ",
            "tg://login?token=",
            "tg://login?token=abc&extra=1",
            "tg://login?token=abc=def",
            "tg://login?token=abc def",
            "tg://login?token=abc%2Ddef",
            "tg://login?token=abc.def",
            "https://login?token=abc",
            "tg://login?token=$token ",
            " $link",
            tooLong
        )
        malformed.forEach { raw -> assertNull("Accepted malformed link: $raw", QrLoginToken.parse(raw)) }
    }

    @Test
    fun tokenDoesNotLeakItsValueThroughToString() {
        val parsed = checkNotNull(QrLoginToken.parse(link))
        assertFalse(parsed.toString().contains(token))
        assertFalse(QrLoginLinkUpdate.Refreshed(parsed).toString().contains(token))
        assertFalse(AuthState.WaitingForQr(link).toString().contains(token))
    }

    @Test
    fun trackerReplacesLinksAndKeepsTheNewestValidToken() {
        val tracker = QrLoginLinkTracker()
        val first = checkNotNull(QrLoginToken.parse(link))
        assertEquals(QrLoginLinkUpdate.Refreshed(first), tracker.accept(link, nowMillis = 1_000))
        assertEquals(first, tracker.current())
        assertEquals(QrLoginLinkUpdate.Unchanged, tracker.accept(link, nowMillis = 2_000))

        val secondLink = "tg://login?token=next_TOKEN-456"
        val second = checkNotNull(QrLoginToken.parse(secondLink))
        assertEquals(QrLoginLinkUpdate.Refreshed(second), tracker.accept(secondLink, nowMillis = 3_000))
        assertEquals(second, tracker.current())
    }

    @Test
    fun invalidUpdateDoesNotEraseAUsableCode() {
        val tracker = QrLoginLinkTracker()
        val first = checkNotNull(QrLoginToken.parse(link))
        tracker.accept(link, nowMillis = 1_000)

        assertEquals(QrLoginLinkUpdate.Invalid, tracker.accept("not a telegram link", nowMillis = 2_000))
        assertEquals(first, tracker.current())
        // An invalid update is not a token refresh, so the last valid link keeps ageing.
        assertTrue(tracker.isRefreshOverdue(nowMillis = 1_000 + QR_LOGIN_REFRESH_TIMEOUT_MS))
    }

    @Test
    fun onlyAStaleLinkBecomesOverdue() {
        val tracker = QrLoginLinkTracker()
        assertFalse(tracker.isRefreshOverdue(nowMillis = Long.MAX_VALUE))

        tracker.accept(link, nowMillis = 10_000)
        assertFalse(tracker.isRefreshOverdue(nowMillis = 10_000 + QR_LOGIN_REFRESH_TIMEOUT_MS - 1))
        assertTrue(tracker.isRefreshOverdue(nowMillis = 10_000 + QR_LOGIN_REFRESH_TIMEOUT_MS))

        // A repeated update still means TDLib refreshed the link, even when the token text is unchanged.
        tracker.accept(link, nowMillis = 20_000)
        assertFalse(tracker.isRefreshOverdue(nowMillis = 20_000 + QR_LOGIN_REFRESH_TIMEOUT_MS - 1))
        assertTrue(tracker.isRefreshOverdue(nowMillis = 20_000 + QR_LOGIN_REFRESH_TIMEOUT_MS))
    }

    @Test
    fun resetForgetsTheLinkAndOverdueState() {
        val tracker = QrLoginLinkTracker()
        tracker.accept(link, nowMillis = 1_000)
        tracker.reset()
        assertNull(tracker.current())
        assertFalse(tracker.isRefreshOverdue(nowMillis = Long.MAX_VALUE))
    }
}
