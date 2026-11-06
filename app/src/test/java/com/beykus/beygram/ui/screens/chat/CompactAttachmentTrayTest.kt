package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactAttachmentTrayTest {
    private fun item(path: String, kind: MediaKind = MediaKind.Photo, name: String = "photo.jpg") =
        OutgoingMedia(path = path, fileName = name, mimeType = "image/jpeg", kind = kind)

    @Test fun twoPhotosAndTenMixedVisualsUseFixedHeightTray() {
        assertTrue(isCompactVisualAlbum(listOf(item("/a"), item("/b")), null))
        val album = (0 until 10).map { index ->
            item("/item-$index", if (index % 2 == 0) MediaKind.Photo else MediaKind.Video)
        }
        assertTrue(isCompactVisualAlbum(album, null))
    }

    @Test fun emptyAndNonvisualSelectionsKeepExistingDetailedLayout() {
        assertFalse(isCompactVisualAlbum(emptyList(), null))
        assertFalse(isCompactVisualAlbum(listOf(item("/a"), item("/pdf", MediaKind.Document)), null))
        assertFalse(isCompactVisualAlbum(listOf(item("/audio", MediaKind.Audio)), null))
    }

    @Test fun filesWithIdenticalNamesHaveDistinctStableKeys() {
        assertEquals("path:/a", stagedMediaKey(item("/a"), 0))
        assertEquals("path:/b", stagedMediaKey(item("/b"), 1))
        assertFalse(stagedMediaKey(item("/a"), 0) == stagedMediaKey(item("/b"), 1))
    }

    @Test fun remoteMediaAndFallbackIndexRemainStable() {
        val remote = item("", MediaKind.Photo).copy(remoteFileId = 73)
        assertEquals("remote:73:2", stagedMediaKey(remote, 2))
        assertEquals("index:2", stagedMediaKey(item(""), 2))
    }
}
