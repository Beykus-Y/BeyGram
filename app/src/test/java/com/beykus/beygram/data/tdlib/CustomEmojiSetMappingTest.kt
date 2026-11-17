package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.CustomEmojiSetBrief
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomEmojiSetMappingTest {
    @Test fun onlyCustomEmojiStickersGiveAnId() {
        assertEquals(42L, customEmoji(42L).customEmojiIdOrNull())
        assertNull(customEmoji(0L).customEmojiIdOrNull())
        assertNull(TdApi.Sticker().apply { fullType = TdApi.StickerFullTypeRegular() }.customEmojiIdOrNull())
        assertNull(TdApi.Sticker().customEmojiIdOrNull())
    }

    @Test fun setInfoKeepsItsTitleAndTheCustomEmojiOfItsCovers() {
        val info = TdApi.StickerSetInfo().apply {
            id = 7L
            title = "Cats"
            covers = arrayOf(customEmoji(10L), TdApi.Sticker().apply { fullType = TdApi.StickerFullTypeRegular() }, customEmoji(11L))
        }
        assertEquals(CustomEmojiSetBrief(7L, "Cats", listOf(10L, 11L)), info.toCustomEmojiSetBrief())
    }

    @Test fun setKeepsTheCustomEmojiIdsInOrder() {
        val set = TdApi.StickerSet().apply { stickers = arrayOf(customEmoji(3L), customEmoji(0L), customEmoji(1L)) }
        assertEquals(listOf(3L, 1L), set.customEmojiIds())
    }

    private fun customEmoji(customEmojiId: Long): TdApi.Sticker =
        TdApi.Sticker().apply { fullType = TdApi.StickerFullTypeCustomEmoji(customEmojiId, false) }
}
