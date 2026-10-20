package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaViewerPagerTest {
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
}
