package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidContentPaymentTest {
    private val balance = StarBalance(starCount = 100)
    private val method = ExternalPaymentMethod(title = "Card", url = ExternalPaymentUrl("https://pay.example.com/invoice"))

    @Test
    fun starsPriceIsConfirmedWhenTheBalanceCoversIt() {
        assertEquals(
            PaidContentPlan.ConfirmStars(40, balance),
            paidContentPlanFor(PaidContentFormKind.Stars(40), balance)
        )
    }

    @Test
    fun anEqualBalanceIsEnoughForTheStarsPrice() {
        assertEquals(
            PaidContentPlan.ConfirmStars(100, balance),
            paidContentPlanFor(PaidContentFormKind.Stars(100), balance)
        )
    }

    @Test
    fun aShortBalanceIsReportedWithBothAmounts() {
        assertEquals(
            PaidContentPlan.InsufficientStars(150, balance),
            paidContentPlanFor(PaidContentFormKind.Stars(150), balance)
        )
    }

    @Test
    fun starsPriceIsNotConfirmedWithoutAKnownBalance() {
        assertEquals(
            PaidContentPlan.BalanceUnknown,
            paidContentPlanFor(PaidContentFormKind.Stars(40), null)
        )
    }

    @Test
    fun aZeroStarsPriceIsNotPayable() {
        assertEquals(
            PaidContentPlan.Unavailable,
            paidContentPlanFor(PaidContentFormKind.Stars(0), balance)
        )
    }

    @Test
    fun realMoneyFormsOnlyOfferTheirValidatedPages() {
        assertEquals(
            PaidContentPlan.OpenExternal(listOf(method)),
            paidContentPlanFor(PaidContentFormKind.External(listOf(method)), null)
        )
        assertEquals(
            PaidContentPlan.Unavailable,
            paidContentPlanFor(PaidContentFormKind.External(emptyList()), null)
        )
    }

    @Test
    fun subscriptionsAndUnknownFormsAreNotPaidHere() {
        assertEquals(
            PaidContentPlan.SubscriptionUnsupported,
            paidContentPlanFor(PaidContentFormKind.Subscription, balance)
        )
        assertEquals(
            PaidContentPlan.Unavailable,
            paidContentPlanFor(PaidContentFormKind.Unknown, balance)
        )
    }

    @Test
    fun payGateSendsOnlyTheConfirmedStarsPrice() {
        assertEquals(PaidContentPayGate.Send, paidContentPayGate(PaidContentFormKind.Stars(40), 40))
        assertEquals(PaidContentPayGate.PriceChanged, paidContentPayGate(PaidContentFormKind.Stars(55), 40))
        assertEquals(PaidContentPayGate.PriceChanged, paidContentPayGate(PaidContentFormKind.Stars(40), 0))
    }

    @Test
    fun payGateNeverSendsAFormThatIsNotStars() {
        assertEquals(
            PaidContentPayGate.NotStars,
            paidContentPayGate(PaidContentFormKind.External(listOf(method)), 40)
        )
        assertEquals(PaidContentPayGate.NotStars, paidContentPayGate(PaidContentFormKind.Subscription, 40))
        assertEquals(PaidContentPayGate.NotStars, paidContentPayGate(PaidContentFormKind.Unknown, 40))
    }

    @Test
    fun balanceTooLowIsRecognisedInAnyCase() {
        assertTrue(isInsufficientStarsError("BALANCE_TOO_LOW"))
        assertTrue(isInsufficientStarsError("balance_too_low"))
        assertFalse(isInsufficientStarsError(null))
        assertFalse(isInsufficientStarsError("FLOOD_WAIT_3"))
    }

    @Test
    fun knownRefusalsAreClassifiedBeforeAGenericFailure() {
        assertEquals(
            PaidContentPaymentOutcome.InsufficientStars,
            paidContentErrorOutcome(400, "BALANCE_TOO_LOW")
        )
        assertEquals(PaidContentPaymentOutcome.FloodWait, paidContentErrorOutcome(null, "FLOOD_WAIT_30"))
        assertEquals(PaidContentPaymentOutcome.FloodWait, paidContentErrorOutcome(429, "Too Many Requests"))
        assertNull(paidContentErrorOutcome(400, "BOT_PRECHECKOUT_FAILED"))
    }
}
