package com.beykus.beygram.data.tdlib

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
}
