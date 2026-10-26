package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GiftTextAndPagingTest {
    private fun gift(id: String, name: String) = ReceivedGiftItem(
        id = id,
        senderName = null,
        dateSeconds = 0,
        emoji = "",
        starCount = null,
        name = name,
        text = "",
        isPrivate = false,
        isSaved = true,
        isUpgraded = false,
        wasRefunded = false
    )

    @Test
    fun `missing or implausible server limit keeps the default`() {
        assertEquals(128, GIFT_TEXT_MAX_LENGTH_DEFAULT)
        assertEquals(GIFT_TEXT_MAX_LENGTH_DEFAULT, giftTextLimit(null))
        assertEquals(GIFT_TEXT_MAX_LENGTH_DEFAULT, giftTextLimit(0L))
        assertEquals(GIFT_TEXT_MAX_LENGTH_DEFAULT, giftTextLimit(-1L))
        assertEquals(GIFT_TEXT_MAX_LENGTH_DEFAULT, giftTextLimit(100_000L))
    }

    @Test
    fun `plausible server limit is used as reported`() {
        assertEquals(255, giftTextLimit(255L))
        assertEquals(4096, giftTextLimit(4096L))
    }

    @Test
    fun `text is valid up to the limit and too long beyond it`() {
        assertEquals(GiftTextValidation.Valid, validateGiftText("", 128))
        assertEquals(GiftTextValidation.Valid, validateGiftText("a".repeat(128), 128))
        assertEquals(GiftTextValidation.TooLong, validateGiftText("a".repeat(129), 128))
    }

    @Test
    fun `length is counted in UTF-16 units like the dialog counter`() {
        // Each gift emoji is two UTF-16 units: 64 of them fit a limit of 128, 65 do not.
        assertEquals(GiftTextValidation.Valid, validateGiftText("🎁".repeat(64), 128))
        assertEquals(GiftTextValidation.TooLong, validateGiftText("🎁".repeat(65), 128))
    }

    @Test
    fun `merging a page keeps one copy of each gift in arrival order`() {
        val first = listOf(gift("1", "a"), gift("2", "b"))
        val next = listOf(gift("2", "b-again"), gift("3", "c"))

        val merged = mergeReceivedGifts(first, next)

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
        assertEquals("b", merged.single { it.id == "2" }.name)
    }

    @Test
    fun `a page that repeats only known gifts adds nothing`() {
        val known = listOf(gift("1", "a"))

        assertEquals(known, mergeReceivedGifts(known, listOf(gift("1", "a"))))
    }

    @Test
    fun `an empty or unchanged cursor ends the list`() {
        assertEquals("", nextReceivedGiftsOffset(requestedOffset = "", returnedOffset = ""))
        assertEquals("", nextReceivedGiftsOffset(requestedOffset = "abc", returnedOffset = ""))
        assertEquals("", nextReceivedGiftsOffset(requestedOffset = "abc", returnedOffset = "abc"))
        assertEquals("def", nextReceivedGiftsOffset(requestedOffset = "abc", returnedOffset = "def"))
    }

    @Test
    fun `walking pages until the cursor stops loads every gift once`() {
        val pages = mapOf(
            "" to ReceivedGiftsPage(listOf(gift("1", "a"), gift("2", "b")), nextOffset = "p2"),
            "p2" to ReceivedGiftsPage(listOf(gift("2", "b"), gift("3", "c")), nextOffset = "p3"),
            "p3" to ReceivedGiftsPage(listOf(gift("4", "d")), nextOffset = "p3")
        )
        var offset = ""
        var loaded = emptyList<ReceivedGiftItem>()
        var requests = 0
        do {
            val page = pages.getValue(offset)
            requests++
            loaded = mergeReceivedGifts(loaded, page.gifts)
            offset = nextReceivedGiftsOffset(offset, page.nextOffset)
        } while (offset.isNotEmpty() && requests < 10)

        assertEquals(listOf("1", "2", "3", "4"), loaded.map { it.id })
        assertEquals(3, requests)
    }
}
