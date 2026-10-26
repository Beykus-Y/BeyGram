package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSetContent
import org.junit.Assert.assertEquals
import org.junit.Test

class StickerSetLogicTest {
    private fun set(id: Long, vararg stickers: StickerItem) = StickerSetContent(
        id = id,
        title = "Set $id",
        stickers = stickers.toList()
    )

    @Test fun singleStickerWithoutSetGetsTheFallbackInsteadOfARequest() {
        assertEquals(
            StickerSetContentState.Unavailable,
            stickerSetContentState(0L, set(0L, StickerItem(setId = 0L, stickerId = 1L, fileId = 2)))
        )
    }

    @Test fun setWhoseStickersWereAllFilteredOutIsEmpty() {
        assertEquals(StickerSetContentState.Empty, stickerSetContentState(12L, set(12L)))
    }

    @Test fun regularSetWithAtLeastOneStickerIsReady() {
        assertEquals(
            StickerSetContentState.Ready,
            stickerSetContentState(12L, set(12L, StickerItem(setId = 12L, stickerId = 3L, fileId = 4)))
        )
    }
}
