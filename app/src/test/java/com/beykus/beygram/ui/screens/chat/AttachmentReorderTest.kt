package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentReorderTest {
    private fun item(path: String, title: String = "photo.jpg") =
        OutgoingMedia(path = path, fileName = title, mimeType = "image/jpeg", kind = MediaKind.Photo)

    @Test fun moveUsesPendingSendOrderNotFilename() {
        val a = item("/a")
        val b = item("/b")
        val c = item("/c")
        val actual = reorderPendingAttachment(listOf(a, b, c), b, -1)
        assertEquals(listOf("/b", "/a", "/c"), actual.map { it.path })
        assertEquals(listOf("/b", "/c", "/a"), reorderPendingAttachment(actual, a, 1).map { it.path })
    }

    @Test fun invalidMovesAndMissingItemsDoNotReorder() {
        val a = item("/a")
        val b = item("/b")
        val source = listOf(a, b)
        assertEquals(source, reorderPendingAttachment(source, a, -1))
        assertEquals(source, reorderPendingAttachment(source, b, 1))
        assertEquals(source, reorderPendingAttachment(source, item("/missing"), -1))
        assertEquals(source, reorderPendingAttachment(source, a, 3))
        assertEquals(source, reorderPendingAttachment(source, a, 0))
    }

    @Test fun filePreparationAndAlbumMetadataKeepTheirIdentityAfterMove() {
        val a = item("/a").copy(size = 123)
        val b = item("/b").copy(size = 456)
        val reordered = reorderPendingAttachment(listOf(a, b), b, -1)
        assertEquals(b, reordered.first())
        assertEquals(a, reordered.last())
        assertEquals(listOf(456L, 123L), reordered.map { it.size })
    }
}
