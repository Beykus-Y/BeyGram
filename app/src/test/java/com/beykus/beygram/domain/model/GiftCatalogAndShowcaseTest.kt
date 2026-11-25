package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GiftCatalogAndShowcaseTest {
    private fun catalogItem(
        id: Long,
        title: String = "",
        stars: Long = 100L,
        remaining: Int? = null,
        soldOut: Boolean = false,
        auctionId: String? = null,
        resaleCount: Int = 0
    ) = GiftCatalogItem(
        id = id,
        emoji = "\uD83C\uDF81",
        starCount = stars,
        isPremiumOnly = false,
        remainingCount = remaining,
        isSoldOut = soldOut,
        isAuctionOnly = auctionId != null,
        auctionId = auctionId,
        resaleCount = resaleCount,
        resaleTitle = title
    )

    private fun received(id: String, pinned: Boolean = false, refunded: Boolean = false) = ReceivedGiftItem(
        id = id,
        senderName = "Sender",
        dateSeconds = 1000,
        emoji = "\uD83C\uDF81",
        starCount = 50L,
        name = "Gift $id",
        text = "",
        isPrivate = false,
        isSaved = true,
        isUpgraded = true,
        wasRefunded = refunded,
        isPinned = pinned
    )

    @Test
    fun filterGiftCatalogMatchesCategoryAndQuery() {
        val items = listOf(
            catalogItem(1L, title = "Plush Pepe", stars = 500L, resaleCount = 10),
            catalogItem(2L, title = "Astral Shard", stars = 200L, remaining = 5),
            catalogItem(3L, title = "Loot Bag", stars = 1000L, auctionId = "auc-1"),
            catalogItem(4L, title = "Sold Bear", stars = 50L, soldOut = true)
        )
        assertEquals(4, filterGiftCatalog(items, GiftCatalogFilter.All, "").size)
        assertEquals(listOf(1L, 2L), filterGiftCatalog(items, GiftCatalogFilter.Available, "").map { it.id })
        assertEquals(listOf(1L), filterGiftCatalog(items, GiftCatalogFilter.Resale, "").map { it.id })
        assertEquals(listOf(3L), filterGiftCatalog(items, GiftCatalogFilter.Auction, "").map { it.id })
        assertEquals(listOf(2L, 4L), filterGiftCatalog(items, GiftCatalogFilter.Limited, "").map { it.id })
        assertEquals(listOf(2L), filterGiftCatalog(items, GiftCatalogFilter.All, "shard").map { it.id })
    }

    @Test
    fun sortProfileShowcaseGiftsPlacesPinnedFirstAndExcludesRefunded() {
        val gifts = listOf(
            received("1", pinned = false),
            received("2", pinned = true),
            received("3", pinned = true, refunded = true),
            received("4", pinned = true),
            received("5", pinned = false)
        )
        assertEquals(listOf("2", "4", "1", "5"), sortProfileShowcaseGifts(gifts, limit = 6).map { it.id })
        assertEquals(listOf("2", "4"), sortProfileShowcaseGifts(gifts, limit = 2).map { it.id })
    }

    @Test
    fun togglePinnedGiftIdsAddsRemovesAndCapsAtMax() {
        val gifts = listOf(
            received("a", pinned = true),
            received("b", pinned = true),
            received("c", pinned = false)
        )
        assertEquals(listOf("b"), togglePinnedGiftIds(gifts, "a", maxPinned = 6))
        assertEquals(listOf("c", "a", "b"), togglePinnedGiftIds(gifts, "c", maxPinned = 6))
        assertEquals(listOf("c", "a"), togglePinnedGiftIds(gifts, "c", maxPinned = 2))
    }
}
