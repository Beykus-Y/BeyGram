package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaFileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TDLib's answer to a GetFile can arrive after an update that already changed the file. The answer must not roll that back. */
class MediaFileStoreRefreshOrderTest {
    private val asked = mutableListOf<Int>()

    @Test fun anAnswerBuiltBeforeACompletedDownloadDoesNotRollItBack() {
        val store = MediaFileStore(refreshFile = { asked += it })
        // The read creates the state and asks TDLib for it. The answer is still on its way.
        assertEquals(0L, store.observe(1).value.size)
        assertEquals(listOf(1), asked)
        // TDLib reports the finished download first, then the older answer arrives without a local copy.
        store.update(MediaFileState(1, size = 100, localPath = "/done.mp4", downloadedBytes = 100))
        store.refresh(MediaFileState(1, size = 100))
        assertEquals("/done.mp4", store.observe(1).value.localPath)
        assertEquals(100L, store.observe(1).value.downloadedBytes)
    }

    @Test fun anAnswerThatNothingOvertookIsStillApplied() {
        val store = MediaFileStore(refreshFile = { asked += it })
        assertEquals(0L, store.observe(2).value.size)
        store.refresh(MediaFileState(2, size = 100, localPath = "/cached.jpg", downloadedBytes = 100))
        assertEquals("/cached.jpg", store.observe(2).value.localPath)
    }

    @Test fun anOlderIdleAnswerDoesNotHideARunningTransfer() {
        val store = MediaFileStore(refreshFile = { asked += it })
        assertEquals(0L, store.observe(3).value.size)
        store.update(MediaFileState(3, size = 100, isDownloading = true, downloadedBytes = 40))
        // The answer is idle and older than the progress update, so it must not hide the running transfer.
        store.refresh(MediaFileState(3, size = 100))
        assertEquals(40L, store.observe(3).value.downloadedBytes)
        assertTrue(store.observe(3).value.isDownloading)
    }

    @Test fun anAnswerToAnEarlierQuestionDoesNotRollBackWhenTheFileWasAskedAgain() {
        val store = MediaFileStore(maxEntries = 1, refreshFile = { asked += it })
        // The first read asks question 1 about file 1. Reading file 2 evicts the idle file 1, and reading file 1 again asks question 2.
        assertEquals(0L, store.observe(1).value.size)
        assertEquals(0L, store.observe(2).value.size)
        assertEquals(0L, store.observe(1).value.size)
        assertEquals(listOf(1, 2, 1), asked)
        // Answer 1 arrives first and is no news. Then the download finishes, and answer 2, built before that update, arrives.
        store.refresh(MediaFileState(1, size = 100))
        store.update(MediaFileState(1, size = 100, localPath = "/done.mp4", downloadedBytes = 100))
        store.refresh(MediaFileState(1, size = 100))
        assertEquals("/done.mp4", store.observe(1).value.localPath)
    }

    @Test fun aQuestionThatFailedDoesNotHideTheNextAnswer() {
        val store = MediaFileStore(maxEntries = 1, refreshFile = { asked += it })
        assertEquals(0L, store.observe(1).value.size)
        store.update(MediaFileState(1, size = 100, localPath = "/done.mp4", downloadedBytes = 100))
        // TDLib answered the question with an error. The update above must not be counted against a later answer.
        store.refreshFailed(1)
        assertEquals(0L, store.observe(2).value.size)
        assertEquals(0L, store.observe(1).value.size)
        store.refresh(MediaFileState(1, size = 100, localPath = "/done.mp4", downloadedBytes = 100))
        assertEquals("/done.mp4", store.observe(1).value.localPath)
    }
}
