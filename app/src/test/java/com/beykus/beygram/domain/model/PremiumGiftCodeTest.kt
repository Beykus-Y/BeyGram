package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PremiumGiftCodeTest {
    @Test
    fun `blank codes are rejected before any server request`() {
        assertEquals(PremiumGiftCodeValidation.Empty, validatePremiumGiftCode(""))
        assertEquals(PremiumGiftCodeValidation.Empty, validatePremiumGiftCode("   "))
        assertNull(normalizePremiumGiftCode("   "))
    }

    @Test
    fun `bare codes and t me links normalize to the same code`() {
        assertEquals("ABC_def-123", normalizePremiumGiftCode("  ABC_def-123  "))
        assertEquals("ABC_def-123", normalizePremiumGiftCode("https://t.me/giftcode/ABC_def-123"))
        assertEquals("ABC_def-123", normalizePremiumGiftCode("https://T.ME/GIFTCODE/ABC_def-123/?ref=1"))
        assertEquals("ABC_def-123", normalizePremiumGiftCode("t.me/giftcode/ABC_def-123"))
    }

    @Test
    fun `internal whitespace and control characters are rejected`() {
        assertEquals(PremiumGiftCodeValidation.InvalidCharacters, validatePremiumGiftCode("ABC DEF"))
        assertEquals(PremiumGiftCodeValidation.InvalidCharacters, validatePremiumGiftCode("ABC\nDEF"))
        assertEquals(PremiumGiftCodeValidation.InvalidCharacters, validatePremiumGiftCode("ABC\u0000DEF"))
    }

    @Test
    fun `overlong codes are rejected`() {
        assertEquals(
            PremiumGiftCodeValidation.TooLong,
            validatePremiumGiftCode("A".repeat(129))
        )
    }

    @Test
    fun `server error classification separates invalid used expired and other user`() {
        assertEquals(PremiumGiftCodeError.Invalid, classifyPremiumGiftCodeError("PREMIUM_GIFT_CODE_INVALID"))
        assertEquals(PremiumGiftCodeError.Used, classifyPremiumGiftCodeError("GIFT_CODE_USED"))
        assertEquals(PremiumGiftCodeError.Expired, classifyPremiumGiftCodeError("GIFT_CODE_EXPIRED"))
        assertEquals(PremiumGiftCodeError.OtherUser, classifyPremiumGiftCodeError("GIFT_CODE_OTHER_USER"))
        assertEquals(PremiumGiftCodeError.Other, classifyPremiumGiftCodeError("FLOOD_WAIT_42"))
        assertEquals(PremiumGiftCodeError.Other, classifyPremiumGiftCodeError(null))
    }

    @Test
    fun `duration keeps months and days separately`() {
        assertEquals(PremiumGiftDuration(3, 0), premiumGiftDuration(3, 0))
        assertEquals(PremiumGiftDuration(0, 30), premiumGiftDuration(0, 30))
        assertEquals(PremiumGiftDuration(1, 5), premiumGiftDuration(1, 5))
        assertNull(premiumGiftDuration(0, 0).takeIf { it.isKnown })
        assertEquals(PremiumGiftDuration(0, 0), premiumGiftDuration(-2, -3))
    }

    @Test
    fun `used codes cannot be activated but re-gifted codes can`() {
        assertEquals(false, info(isUsed = true).canActivate)
        assertEquals(true, info(wasOriginallyForAnotherUser = true).canActivate)
        assertEquals(true, info().canActivate)
    }

    @Test
    fun `code must be checked and confirmed before applying`() {
        var step: PremiumGiftCodeStep = PremiumGiftCodeStep.Idle
        step = step.next(PremiumGiftCodeEvent.Apply)
        assertEquals(PremiumGiftCodeStep.Idle, step)
        step = step.next(PremiumGiftCodeEvent.Check)
        assertEquals(PremiumGiftCodeStep.Checking, step)
        step = step.next(PremiumGiftCodeEvent.Apply)
        assertEquals(PremiumGiftCodeStep.Checking, step)
        step = step.next(PremiumGiftCodeEvent.Checked(info()))
        assertEquals(PremiumGiftCodeStep.Confirming(info()), step)
        step = step.next(PremiumGiftCodeEvent.Apply)
        assertEquals(PremiumGiftCodeStep.Applying(info()), step)
    }

    @Test
    fun `a second apply is ignored while the first activation is running`() {
        val applying = PremiumGiftCodeStep.Applying(info())
        assertEquals(applying, applying.next(PremiumGiftCodeEvent.Apply))
        assertEquals(applying, applying.next(PremiumGiftCodeEvent.Reset))
        assertEquals(applying, applying.next(PremiumGiftCodeEvent.Check))
    }

    @Test
    fun `activation records success and uncertainty without retrying`() {
        val applying = PremiumGiftCodeStep.Applying(info())
        assertEquals(PremiumGiftCodeStep.Activated(info()), applying.next(PremiumGiftCodeEvent.Applied))
        assertEquals(PremiumGiftCodeStep.Failed(uncertain = true), applying.next(PremiumGiftCodeEvent.Uncertain))
        assertEquals(
            PremiumGiftCodeStep.Failed(uncertain = true),
            PremiumGiftCodeStep.Failed(uncertain = true).next(PremiumGiftCodeEvent.Apply)
        )
    }

    @Test
    fun `reset only leaves a non-mutating confirmation state`() {
        assertEquals(
            PremiumGiftCodeStep.Idle,
            PremiumGiftCodeStep.Confirming(info()).next(PremiumGiftCodeEvent.Reset)
        )
        assertEquals(
            PremiumGiftCodeStep.Idle,
            PremiumGiftCodeStep.Activated(info()).next(PremiumGiftCodeEvent.Reset)
        )
    }

    private fun info(
        isUsed: Boolean = false,
        wasOriginallyForAnotherUser: Boolean = false
    ) = PremiumGiftCodeInfo(
        creatorName = "Sender",
        creationDateSeconds = 1,
        isFromGiveaway = false,
        monthCount = 3,
        dayCount = 0,
        isUsed = isUsed,
        useDateSeconds = if (isUsed) 2 else 0,
        wasOriginallyForAnotherUser = wasOriginallyForAnotherUser
    )
}
