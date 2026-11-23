package com.beykus.beygram.ui.screens.chats

import com.beykus.beygram.domain.model.TelegramFolderLoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatsListPagingTest {
    private val loaded = TelegramFolderLoadState(loadedOnce = true)

    @Test fun nearTheEndOfALoadedFolderRequestsTheNextPage() {
        assertTrue(shouldAutoLoadFolderPage(lastVisibleIndex = 8, totalItemsCount = 12, state = loaded, rowCount = 10, lastRequestedRowCount = null))
    }

    @Test fun farFromTheEndDoesNotRequest() {
        assertFalse(shouldAutoLoadFolderPage(lastVisibleIndex = 2, totalItemsCount = 50, state = loaded, rowCount = 40, lastRequestedRowCount = null))
    }

    @Test fun neverLoadedOrBusyOrFailedOrFinishedFoldersDoNotRequest() {
        val near = { state: TelegramFolderLoadState ->
            shouldAutoLoadFolderPage(lastVisibleIndex = 5, totalItemsCount = 6, state = state, rowCount = 3, lastRequestedRowCount = null)
        }

        assertFalse(near(TelegramFolderLoadState()))
        assertFalse(near(loaded.copy(loading = true)))
        assertFalse(near(loaded.copy(error = true)))
        assertFalse(near(loaded.copy(hasMore = false)))
    }

    @Test fun samePageWithoutGrowthIsNotRequestedAgain() {
        assertFalse(shouldAutoLoadFolderPage(lastVisibleIndex = 8, totalItemsCount = 12, state = loaded, rowCount = 10, lastRequestedRowCount = 10))
        assertTrue(shouldAutoLoadFolderPage(lastVisibleIndex = 8, totalItemsCount = 12, state = loaded, rowCount = 14, lastRequestedRowCount = 10))
    }

    @Test fun emptyLoadedFolderWithMorePagesRequestsOnce() {
        assertTrue(shouldAutoLoadFolderPage(lastVisibleIndex = 1, totalItemsCount = 2, state = loaded, rowCount = 0, lastRequestedRowCount = null))
        assertFalse(shouldAutoLoadFolderPage(lastVisibleIndex = 1, totalItemsCount = 2, state = loaded, rowCount = 0, lastRequestedRowCount = 0))
    }

    @Test fun emptyContentWaitsUntilTheFolderIsAnswered() {
        assertEquals(FolderEmptyContent.Loading, folderEmptyContent(TelegramFolderLoadState()))
        assertEquals(FolderEmptyContent.Loading, folderEmptyContent(loaded))
        assertEquals(FolderEmptyContent.Error, folderEmptyContent(loaded.copy(error = true)))
        assertEquals(FolderEmptyContent.Empty, folderEmptyContent(loaded.copy(hasMore = false)))
    }
}
