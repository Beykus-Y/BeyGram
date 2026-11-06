package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactAttachmentSendPolicyTest {
    private fun media(id: Int, kind: MediaKind = MediaKind.Photo): OutgoingMedia =
        OutgoingMedia(
            path = "/staged/$id",
            fileName = "photo.jpg",
            mimeType = when (kind) {
                MediaKind.Video -> "video/mp4"
                MediaKind.Document -> "application/pdf"
                MediaKind.Audio -> "audio/mpeg"
                else -> "image/jpeg"
            },
            kind = kind
        )

    private fun prepared(vararg items: OutgoingMedia) = items.map { it to it }

    @Test fun twoToTenVisualsUseOneTelegramAlbumInSendOrder() {
        for (count in 2..10) {
            val staged = (0 until count).map { i ->
                media(i, if (i % 2 == 0) MediaKind.Photo else MediaKind.Video)
            }
            val batches = attachmentSendBatches(staged.map { it to it })
            assertEquals(1, batches.size)
            assertEquals(staged, batches.single().map { it.first })
            assertEquals(staged, batches.single().map { it.second })
        }
    }

    @Test fun oneVisualAndMixedMediaKeepSequentialSendSemantics() {
        val photo = media(1)
        val video = media(2, MediaKind.Video)
        val pdf = media(3, MediaKind.Document)
        val audio = media(4, MediaKind.Audio)
        assertEquals(listOf(listOf(photo to photo)), attachmentSendBatches(prepared(photo)))
        val batches = attachmentSendBatches(prepared(photo, video, pdf, audio))
        assertEquals(4, batches.size)
        assertEquals(listOf(photo, video, pdf, audio), batches.map { it.single().second })
    }

    @Test fun videoDeliveredAsFileNeverJoinsAlbum() {
        val video = media(1, MediaKind.Video)
        val original = media(2)
        val deliveredFile = outgoingMediaFor(
            video, MediaPreparation(mode = MediaQualityMode.Balanced, delivery = MediaDeliveryMode.File)
        )
        assertEquals(MediaKind.Document, deliveredFile.kind)
        val batches = attachmentSendBatches(listOf(video to deliveredFile, original to original))
        assertEquals(2, batches.size)
        assertEquals(listOf(MediaKind.Document, MediaKind.Photo), batches.map { it.single().second.kind })
    }

    @Test fun elevenItemsCannotBeGroupedAsOneAlbum() {
        val items = (0..10).map { media(it) }
        assertEquals(11, attachmentSendBatches(items.map { it to it }).size)
    }

    @Test fun originalAndPreparedObjectsStayAssociatedInEachBatch() {
        val original = media(1, MediaKind.Video)
        val compressed = original.copy(path = "/compressed/1", fileName = "compressed.mp4")
        val photo = media(2)
        val batches = attachmentSendBatches(listOf(original to compressed, photo to photo))
        assertEquals(1, batches.size)
        assertEquals(original.path, batches[0][0].first.path)
        assertEquals(compressed.path, batches[0][0].second.path)
        assertEquals(photo.path, batches[0][1].second.path)
    }

    @Test fun unicodeCaptionCountMatchesUserVisibleCharacters() {
        assertEquals(0, attachmentCaptionLength(""))
        assertEquals(3, attachmentCaptionLength("abc"))
        assertEquals(1, attachmentCaptionLength("🔥"))
        assertEquals(2, attachmentCaptionLength("🔥!"))
        assertTrue(attachmentCaptionLength("🔥") < "🔥".length)
        assertFalse(attachmentCaptionLength("🔥") == "🔥".length)
    }
}
