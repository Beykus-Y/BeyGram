package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumInfoTimeoutTest {
    private val payments = TdPayments(
        TdRequests { error("TDLib is not used by these tests") }
    )

    @Test
    fun `slow premium read ends with a retryable timeout`() = runBlocking {
        val result = payments.premiumInfoResult(timeoutMillis = 10L) {
            delay(5_000L)
            "late"
        }

        assertTrue(result.isFailure)
        val failure = result.exceptionOrNull()
        assertTrue(failure is UserMessageException)
        assertEquals(UiText.Res(R.string.err_monetization_timeout), (failure as UserMessageException).text)
    }

    @Test
    fun `premium read that answers in time completes`() = runBlocking {
        val result = payments.premiumInfoResult { "ok" }

        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun `premium read failure stays a retryable result`() = runBlocking {
        val result = payments.premiumInfoResult<String> { error("boom") }

        assertTrue(result.isFailure)
        assertEquals(
            UiText.Res(R.string.err_monetization_failed),
            (result.exceptionOrNull() as UserMessageException).text
        )
    }

    @Test
    fun `caller cancellation is not reported as a load failure`() {
        var cancelled = false
        try {
            runBlocking {
                payments.premiumInfoResult<Unit> {
                    throw CancellationException("cancelled by caller")
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }
}
