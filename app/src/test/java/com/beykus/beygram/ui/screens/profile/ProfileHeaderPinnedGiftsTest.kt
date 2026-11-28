package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.ReceivedGiftItem
import com.beykus.beygram.domain.model.selectPinnedHeaderGifts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileHeaderPinnedGiftsTest {

    private fun collectible(id: String, number: Int, isPinned: Boolean, upgradedId: Long = number.toLong(), refunded: Boolean = false) =
        ReceivedGiftItem(
            id = id,
            senderName = "Sender",
            dateSeconds = 1000,
            emoji = "\uD83C\uDF81",
            starCount = 100L,
            name = "Gift $number",
            text = "",
            isPrivate = false,
            isSaved = true,
            isUpgraded = true,
            wasRefunded = refunded,
            isPinned = isPinned,
            collectible = GiftCollectibleDetails(
                number = number,
                symbol = null,
                backdropCenterColor = 0x224466,
                backdropEdgeColor = 0x112233,
                backdropSymbolColor = 0x88AACC,
                backdropTextColor = 0xFFFFFF,
                modelRarityPerMille = 10,
                modelRarityTier = null,
                upgradedGiftId = upgradedId
            )
        )

    @Test
    fun selectsOnlyPinnedNonRefundedCollectiblesAndDeduplicatesEmojiStatus() {
        val regularPinned = ReceivedGiftItem(
            id = "reg", senderName = null, dateSeconds = 1, emoji = "🎁", starCount = 10,
            name = "Regular", text = "", isPrivate = false, isSaved = true, isUpgraded = false,
            wasRefunded = false, isPinned = true, collectible = null
        )
        val unpinnedCollectible = collectible("unpinned", 1, isPinned = false)
        val refundedPinned = collectible("refunded", 2, isPinned = true, refunded = true)
        val statusGift = collectible("status", 3, isPinned = true, upgradedId = 999L)
        val valid1 = collectible("v1", 4, isPinned = true, upgradedId = 101L)
        val valid2 = collectible("v2", 5, isPinned = true, upgradedId = 102L)

        val selected = selectPinnedHeaderGifts(
            gifts = listOf(regularPinned, unpinnedCollectible, refundedPinned, statusGift, valid1, valid2),
            emojiStatusUpgradedGiftId = 999L
        )
        assertEquals(listOf("v1", "v2"), selected.map { it.id })
    }

    @Test
    fun slotsRespectAvatarExclusionToolbarAndAdaptiveCaps() {
        val slotsBalanced = computePinnedGiftSlots(
            containerWidthPx = 360f,
            containerHeightPx = 320f,
            avatarCenterXPx = 180f,
            avatarCenterYPx = 124f,
            avatarRadiusPx = 56f,
            toolbarBottomPx = 56f,
            titleTopPx = 196f,
            itemSizePx = 40f,
            densityScale = 1f,
            preset = ProfileSceneDensity.Balanced,
            count = 6
        )
        assertEquals(4, slotsBalanced.size)
        slotsBalanced.forEach { slot ->
            assertTrue("Must be below toolbar", slot.yPx >= 56f)
            assertTrue("Must stay inside horizontal bounds", slot.xPx >= 12f && slot.xPx + 40f <= 348f)
            val dx = (slot.xPx + 20f) - 180f
            assertTrue("Must stay outside avatar horizontal exclusion", kotlin.math.abs(dx) >= 56f + 14f)
        }

        val slotsCalm = computePinnedGiftSlots(
            containerWidthPx = 360f, containerHeightPx = 320f,
            avatarCenterXPx = 180f, avatarCenterYPx = 124f, avatarRadiusPx = 56f,
            toolbarBottomPx = 56f, titleTopPx = 196f, itemSizePx = 40f, densityScale = 1f,
            preset = ProfileSceneDensity.Calm, count = 6
        )
        assertEquals(2, slotsCalm.size)

        val slotsTooNarrow = computePinnedGiftSlots(
            containerWidthPx = 240f, containerHeightPx = 320f,
            avatarCenterXPx = 120f, avatarCenterYPx = 124f, avatarRadiusPx = 56f,
            toolbarBottomPx = 56f, titleTopPx = 196f, itemSizePx = 40f, densityScale = 1f,
            preset = ProfileSceneDensity.Rich, count = 6
        )
        assertTrue(slotsTooNarrow.isEmpty())
    }
}
