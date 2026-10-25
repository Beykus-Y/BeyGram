package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiftEligibilityTest {
    private fun gift(
        starCount: Long = 50,
        premium: Boolean = false,
        remaining: Int? = null,
        soldOut: Boolean = false,
        auction: Boolean = false
    ) = GiftCatalogItem(
        id = 1L,
        emoji = "🎁",
        starCount = starCount,
        isPremiumOnly = premium,
        remainingCount = remaining,
        isSoldOut = soldOut,
        isAuctionOnly = auction
    )

    @Test
    fun `own profile can never send a gift`() {
        assertEquals(
            GiftBlockReason.OwnProfile,
            giftBlockReason(gift(), isSelf = true, isPremium = true, balanceStars = 1000, busy = false)
        )
    }

    @Test
    fun `busy state blocks a second send before any other condition`() {
        assertEquals(
            GiftBlockReason.Busy,
            giftBlockReason(gift(), isSelf = false, isPremium = true, balanceStars = 1000, busy = true)
        )
    }

    @Test
    fun `auction and sold out gifts are not directly purchasable`() {
        assertEquals(
            GiftBlockReason.AuctionOnly,
            giftBlockReason(gift(auction = true), isSelf = false, isPremium = true, balanceStars = 1000, busy = false)
        )
        assertEquals(
            GiftBlockReason.SoldOut,
            giftBlockReason(
                gift(remaining = 0, soldOut = true),
                isSelf = false, isPremium = true, balanceStars = 1000, busy = false
            )
        )
    }

    @Test
    fun `premium-only gift requires an active subscription`() {
        assertEquals(
            GiftBlockReason.PremiumRequired,
            giftBlockReason(gift(premium = true), isSelf = false, isPremium = false, balanceStars = 1000, busy = false)
        )
        assertNull(
            giftBlockReason(gift(premium = true), isSelf = false, isPremium = true, balanceStars = 1000, busy = false)
        )
    }

    @Test
    fun `known balance below the price blocks the send`() {
        assertEquals(
            GiftBlockReason.InsufficientStars,
            giftBlockReason(gift(starCount = 50), isSelf = false, isPremium = false, balanceStars = 49, busy = false)
        )
        assertNull(
            giftBlockReason(gift(starCount = 50), isSelf = false, isPremium = false, balanceStars = 50, busy = false)
        )
    }

    @Test
    fun `unknown balance blocks sending until the user can see it`() {
        assertEquals(
            GiftBlockReason.BalanceUnknown,
            giftBlockReason(gift(starCount = 50), isSelf = false, isPremium = false, balanceStars = null, busy = false)
        )
    }
}
