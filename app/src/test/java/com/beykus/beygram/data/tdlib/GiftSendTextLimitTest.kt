package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.GiftSendOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GiftSendTextLimitTest {
    // TDLib is never reached for an over-limit message; the limit check runs before any request.
    private val payments = TdPayments(
        TdRequests { error("TDLib is not used by these tests") }
    )

    @Test
    fun `a gift message over the default limit is blocked before any purchase`() = runBlocking {
        val outcome = payments.sendGift(
            profileId = 1L,
            isSelf = false,
            giftId = 1L,
            text = "a".repeat(129),
            isPrivate = false
        )

        assertEquals(GiftSendOutcome.Blocked(UiText.Res(R.string.gifts_text_too_long)), outcome)
    }

    @Test
    fun `a gift message at the default limit passes the length check`() = runBlocking {
        // The purchase then stops at its first TDLib call, which these tests do not provide.
        val outcome = payments.sendGift(
            profileId = 1L,
            isSelf = false,
            giftId = 1L,
            text = "a".repeat(128),
            isPrivate = true
        )

        assertNotEquals(GiftSendOutcome.Blocked(UiText.Res(R.string.gifts_text_too_long)), outcome)
    }
}
