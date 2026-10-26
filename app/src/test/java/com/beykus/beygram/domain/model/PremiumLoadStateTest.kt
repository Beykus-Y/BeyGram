package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumLoadStateTest {
    private val now = 1_000_000L
    private val timeout = UiText.Raw("Telegram did not respond in time")

    private fun plan(amount: Long = 4999, currency: String = "EUR") = PremiumPlan(
        monthCount = 12,
        amountMinor = amount,
        currency = currency,
        discountPercent = 0,
        isCurrent = false,
        isUpgrade = false,
        hasExternalPaymentLink = true
    )

    private fun info(
        isPremium: Boolean = false,
        plans: List<PremiumPlan> = listOf(plan())
    ) = PremiumInfo(
        isPremium = isPremium,
        statusText = "",
        features = emptyList(),
        plans = plans
    )

    @Test
    fun `initial loading state has no content and cannot start checkout`() {
        val state = PremiumLoadState.Loading

        assertFalse(state.canCheckout("account-a", now))
        assertFalse(state.needsRefresh("account-a", now))
        assertEquals(PremiumLoadState.Loading, state.startingRefresh())
    }

    @Test
    fun `successful read binds the snapshot to the account`() {
        val state = premiumLoadSucceeded("account-a", info(), now)

        assertTrue(state is PremiumLoadState.Ready)
        val ready = state as PremiumLoadState.Ready
        assertEquals("account-a", ready.snapshot.accountId)
        assertFalse(ready.refreshing)
        assertNull(ready.refreshError)
        assertTrue(state.canCheckout("account-a", now))
        assertFalse(state.canCheckout("account-b", now))
    }

    @Test
    fun `refresh keeps a fresh snapshot but blocks checkout and reports the error`() {
        val fresh = premiumLoadSucceeded("account-a", info(), now)
        val failed = fresh.failed("account-a", timeout)

        assertTrue(failed is PremiumLoadState.Ready)
        val ready = failed as PremiumLoadState.Ready
        assertEquals(timeout, ready.refreshError)
        assertFalse(ready.refreshing)
        assertFalse(failed.canCheckout("account-a", now))
        assertFalse(failed.needsRefresh("account-a", now))
    }

    @Test
    fun `failure without cache is a retryable error state`() {
        val state = PremiumLoadState.Loading.failed("account-a", timeout)

        assertEquals(PremiumLoadState.Failed(timeout), state)
        assertFalse(state.canCheckout("account-a", now))
        assertEquals(PremiumLoadState.Loading, state.startingRefresh())
    }

    @Test
    fun `failure keeps the same account snapshot for the gift workflow but hides stale prices`() {
        val fresh = premiumLoadSucceeded("account-a", info(), now)
        val staleAt = now + PREMIUM_INFO_MAX_AGE_MS + 1L
        val failed = fresh.failed("account-a", timeout) as PremiumLoadState.Ready

        // The gift-code section owns an in-flight operation; keeping the snapshot lets it finish.
        assertEquals(fresh.snapshot, failed.snapshot)
        assertEquals(timeout, failed.refreshError)
        // No automatic retry loop after a failure; the user or connectivity change retries.
        assertFalse(failed.needsRefresh("account-a", staleAt))
        assertFalse(failed.canCheckout("account-a", staleAt))
        assertFalse(failed.snapshot.isUsableFor("account-a", staleAt))
        assertFalse(failed.showsPremiumDetails("account-a", staleAt))
    }

    @Test
    fun `failure for another account never exposes the previous account snapshot`() {
        val accountA = premiumLoadSucceeded("account-a", info(isPremium = true), now)
        val failedForB = accountA.failed("account-b", timeout)

        assertEquals(PremiumLoadState.Failed(timeout), failedForB)
        assertFalse(accountA.canCheckout("account-b", now))
    }

    @Test
    fun `freshness is strict at the boundary and rejects clock rollback`() {
        val snapshot = premiumLoadSucceeded("account-a", info(), now).snapshot

        assertTrue(snapshot.isFreshAt(now + PREMIUM_INFO_MAX_AGE_MS))
        assertFalse(snapshot.isFreshAt(now + PREMIUM_INFO_MAX_AGE_MS + 1L))
        assertFalse(snapshot.isFreshAt(now - 1L))
        assertFalse(snapshot.isFreshAt(0L))
        assertFalse(snapshot.isUsableFor("", now))
    }

    @Test
    fun `timeout reason stays visible when there is no confirmed data`() {
        val state = PremiumLoadState.Loading.failed("account-a", timeout)

        assertEquals(PremiumLoadState.Failed(timeout), state)
    }

    @Test
    fun `refresh keeps cached content while loading but blocks checkout`() {
        val fresh = premiumLoadSucceeded("account-a", info(), now)
        val refreshing = fresh.startingRefresh()

        assertTrue(refreshing is PremiumLoadState.Ready)
        val ready = refreshing as PremiumLoadState.Ready
        assertTrue(ready.refreshing)
        assertNull(ready.refreshError)
        assertFalse(refreshing.canCheckout("account-a", now))
        assertFalse(refreshing.needsRefresh("account-a", now))
    }

    @Test
    fun `expired snapshot asks for a refresh and is never actionable`() {
        val fresh = premiumLoadSucceeded("account-a", info(), now)
        val staleAt = now + PREMIUM_INFO_MAX_AGE_MS + 1L

        assertTrue(fresh.needsRefresh("account-a", staleAt))
        assertFalse(fresh.canCheckout("account-a", staleAt))
        assertFalse(fresh.snapshot.isUsableFor("account-a", staleAt))
    }

    @Test
    fun `details are shown only for a fresh snapshot of the same account`() {
        val fresh = premiumLoadSucceeded("account-a", info(), now)
        val staleAt = now + PREMIUM_INFO_MAX_AGE_MS + 1L

        assertTrue(fresh.showsPremiumDetails("account-a", now))
        assertFalse(fresh.showsPremiumDetails("account-b", now))
        assertFalse(fresh.showsPremiumDetails("account-a", staleAt))
        assertFalse(PremiumLoadState.Loading.showsPremiumDetails("account-a", now))
        assertFalse(PremiumLoadState.Failed(timeout).showsPremiumDetails("account-a", now))
    }

    @Test
    fun `unsupported state never reuses cached data`() {
        val fresh = premiumLoadSucceeded("account-a", info(), now)

        assertEquals(
            PremiumLoadState.Unsupported,
            fresh.failed("account-a", timeout, unsupported = true)
        )
    }
}
