package com.beykus.beygram.domain.model

import com.beykus.beygram.domain.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFlowTest {
    private fun plan(
        months: Int = 12,
        amount: Long = 4999,
        currency: String = "EUR",
        isCurrent: Boolean = false,
        isUpgrade: Boolean = false,
        hasLink: Boolean = true
    ) = PremiumPlan(
        monthCount = months,
        amountMinor = amount,
        currency = currency,
        discountPercent = 0,
        isCurrent = isCurrent,
        isUpgrade = isUpgrade,
        hasExternalPaymentLink = hasLink
    )

    private fun option(
        stars: Long = 500,
        amount: Long = 999,
        currency: String = "EUR"
    ) = StarTopUpOption(
        starCount = stars,
        amountMinor = amount,
        currency = currency,
        isAdditional = false
    )

    private fun method(title: String = "Card", url: String = "https://pay.example.com/checkout") =
        ExternalPaymentMethod(title, validateExternalPaymentUrl(url) ?: error("test URL must be valid"))

    @Test
    fun `payment url policy accepts only absolute https pages`() {
        assertEquals(
            "https://fragment.com/premium?plan=12",
            validateExternalPaymentUrl("https://fragment.com/premium?plan=12")?.value
        )
        assertEquals(
            "HTTPS://t.me/invoice/abc",
            validateExternalPaymentUrl("HTTPS://t.me/invoice/abc")?.value
        )
        assertNotNull(validateExternalPaymentUrl("https://pay.example.com:443/checkout"))
        assertNull(validateExternalPaymentUrl(null))
        assertNull(validateExternalPaymentUrl(""))
        assertNull(validateExternalPaymentUrl("http://pay.example.com/checkout"))
        assertNull(validateExternalPaymentUrl("tg://invoice/abc"))
        assertNull(validateExternalPaymentUrl("javascript:alert(1)"))
        assertNull(validateExternalPaymentUrl("file:///sdcard/pay.html"))
        assertNull(validateExternalPaymentUrl("https://user:pass@pay.example.com/checkout"))
        assertNull(validateExternalPaymentUrl("https://pay.example.com:8443/checkout"))
        assertNull(validateExternalPaymentUrl("https://localhost/checkout"))
        assertNull(validateExternalPaymentUrl("https:///checkout"))
        assertNull(validateExternalPaymentUrl(" https://pay.example.com/checkout"))
        assertNull(validateExternalPaymentUrl("https://pay.example.com/check\nout"))
        assertNull(validateExternalPaymentUrl("https://pay.example.com/%0d%0aLocation:https://evil.example"))
        assertNull(validateExternalPaymentUrl("https://pay.example.com/" + "a".repeat(4096)))
    }

    @Test
    fun `payment method titles are bounded plain text`() {
        assertEquals("Card payment", sanitizePaymentMethodTitle("  Card\u0000  payment  "))
        assertEquals("", sanitizePaymentMethodTitle(null))
        assertEquals(80, sanitizePaymentMethodTitle("a".repeat(120)).length)
        val withEmoji = sanitizePaymentMethodTitle("a".repeat(79) + "😀".repeat(10))
        assertEquals(80, withEmoji.codePointCount(0, withEmoji.length))
    }

    @Test
    fun `premium plan gate blocks current plan missing link and busy checkout`() {
        val purchasable = plan()
        val current = plan(isCurrent = true)
        val noLink = plan(hasLink = false)

        assertNull(premiumPlanBlock(purchasable, PremiumCheckoutStep.Idle))
        assertNull(premiumPlanBlock(plan(isCurrent = true, isUpgrade = true), PremiumCheckoutStep.Idle))
        assertEquals(
            PremiumPlanBlock.CurrentPlan,
            premiumPlanBlock(current, PremiumCheckoutStep.Idle)
        )
        assertEquals(
            PremiumPlanBlock.NoExternalCheckout,
            premiumPlanBlock(noLink, PremiumCheckoutStep.Idle)
        )
        assertEquals(
            PremiumPlanBlock.Busy,
            premiumPlanBlock(purchasable, PremiumCheckoutStep.Confirming(purchasable))
        )
        assertEquals(
            PremiumPlanBlock.Busy,
            premiumPlanBlock(purchasable, PremiumCheckoutStep.AwaitingReturn(purchasable))
        )
        assertNull(
            premiumPlanBlock(
                purchasable,
                PremiumCheckoutStep.Failed(purchasable, UiText.Raw("failed"))
            )
        )
    }

    @Test
    fun `premium checkout never starts a second payment`() {
        val selected = plan()
        val url = validateExternalPaymentUrl("https://fragment.com/premium") ?: error("test URL must be valid")
        var step: PremiumCheckoutStep = PremiumCheckoutStep.Idle

        assertEquals(PremiumCheckoutStep.Idle, step.next(PremiumCheckoutEvent.Confirm))
        assertEquals(PremiumCheckoutStep.Idle, step.next(PremiumCheckoutEvent.Ready(url)))

        step = step.next(PremiumCheckoutEvent.Select(selected))
        assertEquals(PremiumCheckoutStep.Confirming(selected), step)
        step = step.next(PremiumCheckoutEvent.Confirm)
        assertEquals(PremiumCheckoutStep.Preparing(selected), step)
        // A second tap or a different plan cannot reset an in-flight preparation.
        assertEquals(PremiumCheckoutStep.Preparing(selected), step.next(PremiumCheckoutEvent.Confirm))
        assertEquals(PremiumCheckoutStep.Preparing(selected), step.next(PremiumCheckoutEvent.Select(plan(months = 6))))

        step = step.next(PremiumCheckoutEvent.Ready(url))
        assertEquals(PremiumCheckoutStep.LinkReady(selected, url), step)
        assertEquals(
            PremiumCheckoutStep.LinkReady(selected, url),
            step.next(PremiumCheckoutEvent.Select(plan(months = 6)))
        )
        step = step.next(PremiumCheckoutEvent.Opened)
        assertEquals(PremiumCheckoutStep.AwaitingReturn(selected), step)
        // While the browser may still be open, no second checkout can be prepared.
        assertEquals(step, step.next(PremiumCheckoutEvent.Confirm))
        assertEquals(step, step.next(PremiumCheckoutEvent.Select(selected)))
        assertEquals(step, step.next(PremiumCheckoutEvent.Ready(url)))

        assertEquals(PremiumCheckoutStep.Idle, step.next(PremiumCheckoutEvent.Returned))
    }

    @Test
    fun `preparing a checkout can be cancelled without opening a page`() {
        val selected = plan()
        val preparing = PremiumCheckoutStep.Preparing(selected)
        assertEquals(PremiumCheckoutStep.Idle, preparing.next(PremiumCheckoutEvent.Dismiss))
        // A late response cannot revive a cancelled checkout from Idle.
        val url = validateExternalPaymentUrl("https://fragment.com/premium") ?: error("test URL must be valid")
        assertEquals(PremiumCheckoutStep.Idle, PremiumCheckoutStep.Idle.next(PremiumCheckoutEvent.Ready(url)))
    }

    @Test
    fun `stars top up opens only a method returned by the payment form`() {
        val selected = option()
        val methods = listOf(method(), method("Wallet", "https://pay.example.org/wallet"))
        val foreign = method("Other", "https://evil.example.net/checkout")
        var step: StarsTopUpStep = StarsTopUpStep.Idle

        assertEquals(StarsTopUpStep.Idle, step.next(StarsTopUpEvent.Confirm))
        assertEquals(StarsTopUpStep.Idle, step.next(StarsTopUpEvent.Ready(methods)))

        step = step.next(StarsTopUpEvent.Select(selected))
        assertEquals(StarsTopUpStep.Confirming(selected), step)
        step = step.next(StarsTopUpEvent.Confirm)
        assertEquals(StarsTopUpStep.Preparing(selected), step)
        assertEquals(StarsTopUpStep.Preparing(selected), step.next(StarsTopUpEvent.Confirm))
        assertEquals(StarsTopUpStep.Idle, step.next(StarsTopUpEvent.Dismiss))
        step = StarsTopUpStep.Preparing(selected)

        step = step.next(StarsTopUpEvent.Ready(methods))
        assertEquals(StarsTopUpStep.Ready(selected, methods), step)
        // A URL that TDLib did not return cannot be injected into the form.
        assertEquals(step, step.next(StarsTopUpEvent.Open(foreign)))

        step = step.next(StarsTopUpEvent.Open(methods.first()))
        assertEquals(StarsTopUpStep.Opening(selected, methods.first()), step)
        step = step.next(StarsTopUpEvent.Opened)
        assertEquals(StarsTopUpStep.AwaitingReturn(selected), step)
        // A second method or a second package is ignored until the browser returns.
        assertEquals(step, step.next(StarsTopUpEvent.Open(methods.last())))
        assertEquals(step, step.next(StarsTopUpEvent.Select(selected)))

        assertEquals(StarsTopUpStep.Idle, step.next(StarsTopUpEvent.Returned))
    }

    @Test
    fun `plan and package snapshots must match TDLib exactly`() {
        assertTrue(plan().isSameOfferAs(plan(currency = "eur")))
        assertFalse(plan().isSameOfferAs(plan(amount = 5000)))
        assertFalse(plan().isSameOfferAs(plan(isCurrent = true)))
        assertFalse(plan().isSameOfferAs(plan(isUpgrade = true)))

        assertTrue(option().isSameOfferAs(option(currency = "eur")))
        assertFalse(option().isSameOfferAs(option(stars = 501)))
        assertFalse(option().isSameOfferAs(option(amount = 1000)))
    }
}
