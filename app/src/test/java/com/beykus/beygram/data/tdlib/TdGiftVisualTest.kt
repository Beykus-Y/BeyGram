package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.GiftRarityTier
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
}
