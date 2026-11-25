package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaViewerPagerTest {
    private fun item(id: Long, kind: MediaKind = MediaKind.Photo, caption: String = "") =
        MediaViewerItem(
            messageId = id,
            media = MessageMedia(kind = kind, fileId = id.toInt()),
            viewOnce = false,
            chatId = 1L,
            caption = caption,
        )
    @Test fun retainsMediaIdentityWhenPagesReordered() {
        assertEquals(2, mediaViewerSelectedIndex(listOf(12L, 13L, 14L), 14L))
        assertEquals(0, mediaViewerSelectedIndex(listOf(14L, 12L), 14L))
    }

    @Test fun deletedMediaFallsBackToSurvivingPage() {
        assertEquals(0, mediaViewerSelectedIndex(listOf(12L), 14L))
        assertEquals(0, mediaViewerSelectedIndex(listOf(12L, 13L), 14L))
    }

    @Test fun staleLazyLayoutIndexCannotReadOutsideSnapshot() {
        val pages = listOf(12L)
        assertEquals(12L, mediaViewerPageKey(pages, 0))
        assertEquals("unavailable-page-1", mediaViewerPageKey(pages, 1))
        assertNotEquals(mediaViewerPageKey(pages, 1), mediaViewerPageKey(pages, 2))
        assertEquals("unavailable-page-0", mediaViewerPageKey(emptyList(), 0))
    }

    @Test fun loadingMorePagesCannotResetInProgressSwipe() {
        // currentPage has advanced to 2, but settledPage remains 1 until swipe finishes.
        assertEquals(false, mediaViewerShouldScrollToSelection(true, 1, 1))
        assertEquals(false, mediaViewerShouldScrollToSelection(true, 1, 2))
        assertEquals(false, mediaViewerShouldScrollToSelection(false, 2, 2))
    }

    @Test fun explicitExternalSelectionCanMoveSettledPageWhenIdle() {
        assertEquals(true, mediaViewerShouldScrollToSelection(false, 1, 3))
    }

    @Test fun retainedIdsKeepSameKeysAcrossChanges() {
        assertEquals(mediaViewerPageKey(listOf(7L, 12L, 14L), 1), mediaViewerPageKey(listOf(12L), 0))
    }

    @Test fun overlappingWindowAndHistoryMergePreservesNewestPhotoAndLiveState() {
        val window = listOf(item(20L, MediaKind.Video), item(30L), item(40L))
        val history = listOf(item(40L, caption = "live"), item(50L))
        val resolved = resolveMediaViewerPages(
            windowPages = window,
            historyPages = history,
            selectedId = 40L,
            fallbackItem = null,
            historyMessageIdRange = 35L..55L,
            liveHistoryIds = setOf(40L, 50L),
        )
        assertEquals(listOf(20L, 30L, 40L, 50L), resolved.map { it.messageId })
        assertEquals("live", resolved.first { it.messageId == 40L }.caption)
        assertEquals(MediaKind.Video, resolved.first { it.messageId == 20L }.media.kind)
    }

    @Test fun deletedMessageInsideHistoryRangeIsNotResurrectedFromWindow() {
        val window = listOf(item(30L), item(40L), item(45L), item(50L))
        val history = listOf(item(40L), item(50L))
        val resolved = resolveMediaViewerPages(
            windowPages = window,
            historyPages = history,
            selectedId = 40L,
            fallbackItem = null,
            historyMessageIdRange = 40L..50L,
            liveHistoryIds = setOf(40L, 50L),
        )
        assertEquals(listOf(30L, 40L, 50L), resolved.map { it.messageId })
    }

    @Test fun disjointWindowAndHistoryAreNeverStitchedAcrossGap() {
        val window = listOf(item(10L), item(12L))
        val history = listOf(item(100L), item(110L))
        val resolvedWindow = resolveMediaViewerPages(
            windowPages = window,
            historyPages = history,
            selectedId = 12L,
            fallbackItem = null,
            historyMessageIdRange = 95L..120L,
            liveHistoryIds = setOf(100L, 110L),
        )
        assertEquals(listOf(10L, 12L), resolvedWindow.map { it.messageId })
    }
}
