package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GiftChatKind
import com.beykus.beygram.domain.model.GiftCollectibleDetails
import com.beykus.beygram.domain.model.GiftVisual
import com.beykus.beygram.domain.model.StickerFileFormat
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structured gift mapping (Gifts 2.0, #1142, Stage B). Fixtures use the real TdApi classes of the shipped library and
 * fill only public fields, so the test never guesses a constructor signature.
 */
class TdGiftContentTest {
    private fun file(id: Int) = TdApi.File().apply { this.id = id }

    private fun sticker(id: Int) = TdApi.Sticker().apply {
        this.id = id.toLong()
        this.sticker = file(id)
        this.emoji = "🎁"
    }

    private fun gift(starCount: Long, premium: Boolean = false, stickerId: Int = 11) = TdApi.Gift().apply {
        id = 1L
        sticker = this@TdGiftContentTest.sticker(stickerId)
        this.starCount = starCount
        isPremium = premium
    }

    private fun upgradedGift(number: Int = 42, title: String = "Plush Pepe", premium: Boolean = false) =
        TdApi.UpgradedGift().apply {
            id = 2L
            this.number = number
            this.title = title
            name = "pepe"
            isPremium = premium
            model = TdApi.UpgradedGiftModel().apply {
                name = "model"
                sticker = this@TdGiftContentTest.sticker(21)
            }
            symbol = TdApi.UpgradedGiftSymbol().apply {
                name = "symbol"
                sticker = this@TdGiftContentTest.sticker(22)
            }
            backdrop = TdApi.UpgradedGiftBackdrop().apply {
                id = 7
                name = "backdrop"
                colors = TdApi.UpgradedGiftBackdropColors().apply {
                    centerColor = 0x112233
                    edgeColor = 0x445566
                    symbolColor = 0x778899
                    textColor = 0xAABBCC
                }
            }
        }

    private fun messageGift(
        gift: TdApi.Gift?,
        note: String? = null,
        refunded: Boolean = false,
        converted: Boolean = false,
        upgraded: Boolean = false,
        uniqueNumber: Int = 0,
    ) = TdApi.MessageGift().apply {
        this.gift = gift
        text = note?.let { TdApi.FormattedText(it, null) }
        wasRefunded = refunded
        wasConverted = converted
        wasUpgraded = upgraded
        uniqueGiftNumber = uniqueNumber
    }

    @Test
    fun `a regular gift keeps its real sticker, stars and note`() {
        val mapped = giftChatContent(messageGift(gift(50L, stickerId = 33), note = "с днём рождения"))!!

        assertEquals(GiftChatKind.Regular, mapped.kind)
        assertEquals(33, mapped.stickerFileId)
        assertEquals(50L, mapped.starCount)
        assertEquals("с днём рождения", mapped.note)
        assertFalse(mapped.isRefunded)
    }

    @Test
    fun `a refunded gift is marked as refunded`() {
        val mapped = giftChatContent(messageGift(gift(50L), refunded = true))!!

        assertEquals(GiftChatKind.Refunded, mapped.kind)
        assertTrue(mapped.isRefunded)
    }

    @Test
    fun `an upgraded gift keeps model, symbol, backdrop and number`() {
        val content = TdApi.MessageUpgradedGift().apply {
            gift = upgradedGift(number = 42, title = "Plush Pepe")
        }

        val mapped = giftChatContent(content)!!

        assertEquals(GiftChatKind.Upgraded, mapped.kind)
        assertEquals(21, mapped.modelFileId)
        assertEquals(22, mapped.symbolFileId)
        assertEquals("Plush Pepe", mapped.title)
        assertEquals("pepe", mapped.name)
        assertEquals("model", mapped.modelName)
        assertEquals("symbol", mapped.symbolName)
        assertEquals("backdrop", mapped.backdropName)
        assertEquals(42, mapped.number)
    }

    @Test
    fun `currency gifts are not collectible items`() {
        val premium = TdApi.MessageGiftedPremium().apply {
            monthCount = 3
            sticker = this@TdGiftContentTest.sticker(44)
            text = TdApi.FormattedText("с праздником", null)
        }
        val stars = TdApi.MessageGiftedStars().apply {
            starCount = 500L
            sticker = this@TdGiftContentTest.sticker(45)
        }
        val ton = TdApi.MessageGiftedTon().apply {
            tonAmount = 2_500_000_000L
            sticker = this@TdGiftContentTest.sticker(46)
        }

        val premiumMapped = giftChatContent(premium)!!
        assertEquals(GiftChatKind.Premium, premiumMapped.kind)
        assertEquals(3, premiumMapped.premiumMonths)
        assertEquals(44, premiumMapped.stickerFileId)
        assertEquals("с праздником", premiumMapped.note)
        assertEquals(0, premiumMapped.modelFileId)

        val starsMapped = giftChatContent(stars)!!
        assertEquals(GiftChatKind.Stars, starsMapped.kind)
        assertEquals(500L, starsMapped.starCount)
        assertEquals(0, starsMapped.number)

        val tonMapped = giftChatContent(ton)!!
        assertEquals(GiftChatKind.Ton, tonMapped.kind)
        assertEquals(2_500_000_000L, tonMapped.tonAmount)
        assertEquals(0, tonMapped.modelFileId)
    }

    @Test
    fun `refunded upgraded gift and purchase offers keep the gift identity`() {
        val refunded = TdApi.MessageRefundedUpgradedGift().apply { gift = gift(10L) }
        val refundedMapped = giftChatContent(refunded)!!
        assertEquals(GiftChatKind.Refunded, refundedMapped.kind)
        assertTrue(refundedMapped.isRefunded)
        assertEquals(11, refundedMapped.stickerFileId)

        val offer = TdApi.MessageUpgradedGiftPurchaseOffer().apply { gift = upgradedGift(number = 7) }
        val offerMapped = giftChatContent(offer)!!
        assertEquals(GiftChatKind.PurchaseOffer, offerMapped.kind)
        assertEquals(7, offerMapped.number)

        val rejected = TdApi.MessageUpgradedGiftPurchaseOfferRejected().apply {
            gift = upgradedGift(number = 8)
            wasExpired = true
        }
        val rejectedMapped = giftChatContent(rejected)!!
        assertEquals(GiftChatKind.PurchaseOfferRejected, rejectedMapped.kind)
        assertEquals(8, rejectedMapped.number)
    }

    @Test
    fun `the card gets the same visual and collectible data as the gift screens`() {
        // The chat card reuses GiftVisualImage/GiftCollectibleBackdrop, so it needs exactly the shapes the profile
        // screens consume: a sticker file for the artwork and the collectible attributes for backdrop and number.
        val visuals = { sticker: TdApi.Sticker? ->
            sticker?.let { GiftVisual(it.sticker.id, StickerFileFormat.Webp, null, it.width, it.height) }
        }
        val collectibles = { gift: TdApi.UpgradedGift? ->
            gift?.let {
                GiftCollectibleDetails(
                    number = it.number,
                    symbol = visuals(it.symbol?.sticker),
                    backdropCenterColor = it.backdrop?.colors?.centerColor,
                    backdropEdgeColor = it.backdrop?.colors?.edgeColor,
                    backdropSymbolColor = it.backdrop?.colors?.symbolColor,
                    backdropTextColor = it.backdrop?.colors?.textColor,
                    modelRarityPerMille = null,
                    modelRarityTier = null,
                )
            }
        }

        val upgraded = giftChatContent(
            TdApi.MessageUpgradedGift().apply { gift = upgradedGift(number = 42) },
            visuals,
            collectibles,
        )!!

        assertEquals(21, upgraded.visual?.fileId)
        assertEquals(StickerFileFormat.Webp, upgraded.visual?.format)
        assertEquals(42, upgraded.collectible?.number)
        assertEquals(22, upgraded.collectible?.symbol?.fileId)
        assertEquals(0x112233, upgraded.collectible?.backdropCenterColor)
        assertEquals(0x445566, upgraded.collectible?.backdropEdgeColor)

        // A regular gift has artwork but nothing collectible.
        val regular = giftChatContent(messageGift(gift(50L, stickerId = 33)), visuals, collectibles)!!
        assertEquals(33, regular.visual?.fileId)
        assertEquals("🎁", regular.emoji)
        assertNull(regular.collectible)

        // The artwork is not on the device yet on the first frame: the card shows the sticker's emoji until then.
        assertEquals("🎁", upgraded.emoji)
    }

    @Test
    fun `without a media layer the projection stays textual`() {
        // The mapping tests and any caller without a media layer keep working: no invented artwork.
        val mapped = giftChatContent(messageGift(gift(50L, stickerId = 33)))!!
        assertNull(mapped.visual)
        assertNull(mapped.collectible)
        assertEquals(33, mapped.stickerFileId)
    }

    @Test
    fun `a non-gift message has no gift content`() {
        assertNull(giftChatContent(TdApi.MessageText()))
    }
}