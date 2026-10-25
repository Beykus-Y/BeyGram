package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.AnimationItem
import com.beykus.beygram.domain.model.AnimationPage
import com.beykus.beygram.domain.model.StickerItem
import com.beykus.beygram.domain.model.StickerSearchPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPickerLogicTest {
    private fun sticker(setId: Long, id: Long, fileId: Int = id.toInt()) =
        StickerItem(setId = setId, stickerId = id, fileId = fileId)

    private fun animation(fileId: Int, uniqueId: String = "") =
        AnimationItem(fileId = fileId, uniqueId = uniqueId)

    @Test
    fun stickerPagesKeepOrderAndDropRepeatedStickers() {
        val existing = listOf(sticker(1, 10), sticker(1, 11))
        val merged = mergeStickerPages(
            existing,
            StickerSearchPage(listOf(sticker(1, 11), sticker(2, 20), sticker(2, 21)), nextOffset = 5)
        )

        assertEquals(listOf(1L to 10L, 1L to 11L, 2L to 20L, 2L to 21L), merged.map { it.setId to it.stickerId })
    }

    @Test
    fun theSameStickerIdInAnotherSetIsNotADuplicate() {
        val merged = mergeStickerPages(
            listOf(sticker(1, 10)),
            StickerSearchPage(listOf(sticker(2, 10)), nextOffset = null)
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun animationPagesUseTheCrossDeviceUniqueId() {
        val existing = listOf(animation(1, "same-gif"), animation(2, "other-gif"))
        val merged = mergeAnimationPages(
            existing,
            AnimationPage(listOf(animation(3, "same-gif"), animation(4, "new-gif")), nextOffset = "next")
        )

        assertEquals(listOf("same-gif", "other-gif", "new-gif"), merged.map { it.key })
    }

    @Test
    fun missingUniqueIdsFallBackToTheLocalFileId() {
        val merged = mergeAnimationPages(
            listOf(animation(7)),
            AnimationPage(listOf(animation(7), animation(8)), nextOffset = "")
        )

        assertEquals(listOf("7", "8"), merged.map { it.key })
    }

    @Test
    fun emptyPagesDoNotChangeTheCurrentList() {
        val existing = listOf(sticker(1, 10), sticker(1, 11))
        val merged = mergeStickerPages(existing, StickerSearchPage())

        assertEquals(existing, merged)
        assertTrue(merged.isNotEmpty())
    }

    @Test
    fun remoteGifAttachmentsCompareByServerFileId() {
        val first = com.beykus.beygram.domain.model.AnimationItem(fileId = 5, fileName = "a.gif").toOutgoingMedia()
        val second = com.beykus.beygram.domain.model.AnimationItem(fileId = 6, fileName = "b.gif").toOutgoingMedia()
        val copy = first.copy(fileName = "renamed.gif")

        assertTrue(first.samePendingAttachment(copy))
        assertTrue(!first.samePendingAttachment(second))
    }
}
