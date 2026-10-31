package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentExitPreviewTest {
    private fun photo(id: String) = OutgoingMedia(
        path = "/staged/$id", fileName = "photo.jpg", mimeType = "image/jpeg",
        kind = MediaKind.Photo
    )

    @Test fun finalPhotoRemovalCanFadeLastRealContents() {
        val previous = listOf(photo("a"))
        assertEquals(previous, nextAttachmentExitPreview(previous, emptyList(), panelVisible = false))
    }

    @Test fun noticeOnlyStateNeverReplaysOldPhotosAfterDismissal() {
        val previous = listOf(photo("a"))
        val withNotice = nextAttachmentExitPreview(previous, emptyList(), panelVisible = true)
        assertTrue(withNotice.isEmpty())
        assertTrue(nextAttachmentExitPreview(withNotice, emptyList(), panelVisible = false).isEmpty())
    }

    @Test fun newSelectionSupersedesSnapshotWithoutRestoringOldFiles() {
        val previous = listOf(photo("a"), photo("b"))
        val newSelection = listOf(photo("c"))
        assertEquals(newSelection, nextAttachmentExitPreview(previous, newSelection, panelVisible = true))
    }

    @Test fun emptyInitialStateHasNoGhostPreview() {
        assertTrue(nextAttachmentExitPreview(emptyList(), emptyList(), panelVisible = false).isEmpty())
    }
}
