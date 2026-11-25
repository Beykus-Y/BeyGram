package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GiftRarityTier
import com.beykus.beygram.domain.model.GiftVisual
import com.beykus.beygram.domain.model.StickerFileFormat
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdGiftVisualTest {
    @Test
    fun everyStickerFormatTheAppCanDrawKeepsItsFormat() {
        assertEquals(StickerFileFormat.Tgs, TdApi.StickerFormatTgs().toStickerFileFormat())
        assertEquals(StickerFileFormat.Webm, TdApi.StickerFormatWebm().toStickerFileFormat())
        assertEquals(StickerFileFormat.Webp, TdApi.StickerFormatWebp().toStickerFileFormat())
    }

    @Test
    fun everyRarityTierMapsToItsTierAndAPerMilleValueIsNotATier() {
        assertEquals(GiftRarityTier.Uncommon, TdApi.UpgradedGiftAttributeRarityUncommon().toRarityTier())
        assertEquals(GiftRarityTier.Rare, TdApi.UpgradedGiftAttributeRarityRare().toRarityTier())
        assertEquals(GiftRarityTier.Epic, TdApi.UpgradedGiftAttributeRarityEpic().toRarityTier())
        assertEquals(GiftRarityTier.Legendary, TdApi.UpgradedGiftAttributeRarityLegendary().toRarityTier())
        assertNull(TdApi.UpgradedGiftAttributeRarityPerMille().apply { perMille = 25 }.toRarityTier())
        assertNull((null as TdApi.UpgradedGiftAttributeRarity?).toRarityTier())
    }

    private fun payments() = TdPayments(TdRequests { error("TDLib is not used by these tests") }, MediaFileStore())

    @Test
    fun aStickerWithoutAFileOrAKnownFormatHasNoVisual() {
        val payments = payments()
        assertNull(payments.giftVisual(null))
        assertNull(payments.giftVisual(TdApi.Sticker()))
        val noFormat = TdApi.Sticker().apply { sticker = TdApi.File().apply { id = 5 } }
        assertNull(payments.giftVisual(noFormat))
    }

    @Test
    fun aTgsStickerKeepsItsFileThumbnailFormatAndSize() {
        val sticker = TdApi.Sticker().apply {
            sticker = TdApi.File().apply { id = 11 }
            format = TdApi.StickerFormatTgs()
            width = 512
            height = 512
            thumbnail = TdApi.Thumbnail().apply { file = TdApi.File().apply { id = 12 } }
        }
        assertEquals(
            GiftVisual(fileId = 11, format = StickerFileFormat.Tgs, thumbnailFileId = 12, width = 512, height = 512),
            payments().giftVisual(sticker)
        )
    }

    @Test
    fun aWebmStickerKeepsItsFormatSoItsThumbnailCanStandIn() {
        val sticker = TdApi.Sticker().apply {
            sticker = TdApi.File().apply { id = 21 }
            format = TdApi.StickerFormatWebm()
            thumbnail = TdApi.Thumbnail().apply { file = TdApi.File().apply { id = 22 } }
        }
        val visual = payments().giftVisual(sticker)
        assertEquals(StickerFileFormat.Webm, visual?.format)
        assertEquals(22, visual?.thumbnailFileId)
    }

    @Test
    fun aStickerRegistersItsFileAndThumbnailInTheMediaStore() {
        val store = MediaFileStore()
        val payments = TdPayments(TdRequests { error("TDLib is not used by these tests") }, store)
        val sticker = TdApi.Sticker().apply {
            sticker = TdApi.File().apply { id = 31 }
            format = TdApi.StickerFormatTgs()
            thumbnail = TdApi.Thumbnail().apply { file = TdApi.File().apply { id = 32 } }
        }
        payments.giftVisual(sticker)
        assertEquals(31, store.snapshot(31)?.id)
        assertEquals(32, store.snapshot(32)?.id)
    }

    @Test
    fun aRegularGiftHasNoCollectibleDetails() {
        assertNull(payments().giftCollectible(null))
    }

    @Test
    fun anUpgradedGiftKeepsItsNumberBackdropSymbolAndRarity() {
        val gift = TdApi.UpgradedGift().apply {
            number = 7
            backdrop = TdApi.UpgradedGiftBackdrop().apply {
                colors = TdApi.UpgradedGiftBackdropColors().apply {
                    centerColor = 0x112233
                    edgeColor = 0x445566
                    symbolColor = 1
                    textColor = 2
                }
            }
            model = TdApi.UpgradedGiftModel().apply {
                rarity = TdApi.UpgradedGiftAttributeRarityPerMille().apply { perMille = 40 }
            }
            symbol = TdApi.UpgradedGiftSymbol().apply {
                sticker = TdApi.Sticker().apply {
                    sticker = TdApi.File().apply { id = 33 }
                    format = TdApi.StickerFormatWebp()
                }
            }
        }
        val details = payments().giftCollectible(gift)
        assertEquals(7, details?.number)
        assertEquals(0x112233, details?.backdropCenterColor)
        assertEquals(0x445566, details?.backdropEdgeColor)
        assertEquals(40, details?.modelRarityPerMille)
        assertNull(details?.modelRarityTier)
        assertEquals(33, details?.symbol?.fileId)
    }
}

