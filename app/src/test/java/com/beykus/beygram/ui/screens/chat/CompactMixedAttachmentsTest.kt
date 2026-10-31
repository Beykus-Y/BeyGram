package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactMixedAttachmentsTest {
    private fun item(path: String, kind: MediaKind, mime: String = "image/jpeg") =
        OutgoingMedia(path = path, fileName = "photo.jpg", mimeType = mime, kind = kind)

    @Test fun mediaAndDocumentsUseSingleHorizontalTray() {
        val mixed = listOf(
            item("/photo", MediaKind.Photo),
            item("/video", MediaKind.Video, "video/mp4"),
            item("/document", MediaKind.Document, "application/pdf")
        )
        assertTrue(isCompactAttachmentSet(mixed, null))
        assertFalse(isDocumentAttachmentTile(mixed[0], null))
        assertFalse(isDocumentAttachmentTile(mixed[1], null))
        assertTrue(isDocumentAttachmentTile(mixed[2], null))
    }

    @Test fun documentAndAudioKeepTheirFileIdentityEvenWithImageMimeType() {
        assertTrue(isDocumentAttachmentTile(item("/image-as-file", MediaKind.Document), null))
        assertTrue(isDocumentAttachmentTile(item("/audio", MediaKind.Audio, "audio/mpeg"), null))
        assertFalse(isDocumentAttachmentTile(item("/animation", MediaKind.Animation, "video/mp4"), null))
    }

    @Test fun videoSentAsFileShowsFilenameInsteadOfMediaThumbnail() {
        val video = item("/video", MediaKind.Video, "video/mp4")
        assertTrue(isDocumentAttachmentTile(
            video, MediaPreparation(delivery = MediaDeliveryMode.File)
        ))
        assertFalse(isDocumentAttachmentTile(
            video, MediaPreparation(delivery = MediaDeliveryMode.Media)
        ))
    }

    @Test fun emptyListHasNoTray() {
        assertFalse(isCompactAttachmentSet(emptyList(), null))
    }

    @Test fun unknownVideoDurationNeverCreatesAValue() {
        assertNull(attachmentDurationLabel(0))
        assertNull(attachmentDurationLabel(-1))
        assertEquals("0:05", attachmentDurationLabel(5))
        assertEquals("1:01", attachmentDurationLabel(61))
        assertEquals("60:00", attachmentDurationLabel(3600))
    }
}
