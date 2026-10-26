package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class MediaMappingTest {
    private fun file(id: Int) = TdApi.File().apply { this.id = id; size = 100; local = TdApi.LocalFile(); remote = TdApi.RemoteFile() }

    @Test fun photoUsesFullResolutionAndSeparateSmallPreview() {
        val store = MediaFileStore()
        val mapper = TdMediaMapper(store)
        val content = TdApi.MessagePhoto().apply {
            photo = TdApi.Photo(false, null, arrayOf(
                TdApi.PhotoSize("s", file(1), 80, 60, intArrayOf()),
                TdApi.PhotoSize("m", file(2), 320, 240, intArrayOf()),
                TdApi.PhotoSize("x", file(3), 1280, 960, intArrayOf())
            ))
            caption = TdApi.FormattedText("caption", emptyArray())
            hasSpoiler = true
        }
        val media = mapper.media(content)!!
        assertEquals(3, media.fileId)
        assertEquals(2, media.thumbnailFileId)
        assertEquals(1280, media.width)
        assertTrue(media.hasSpoiler)
        assertEquals("caption", mapper.caption(content))
        assertEquals(100L, store.observe(3).value.size)
    }

    @Test fun photoRequestUsesLocalFileCaptionAndActualThumbnailDimensions() {
        val media = OutgoingMedia("/photo.jpg", "photo.jpg", "image/jpeg", MediaKind.Photo, 1000, 500,
            thumbnailPath = "/thumb.jpg", thumbnailWidth = 320, thumbnailHeight = 160)
        val content = media.toInputContent("hello") as TdApi.InputMessagePhoto
        assertEquals("/photo.jpg", (content.photo.photo as TdApi.InputFileLocal).path)
        assertEquals(1000, content.photo.width)
        assertEquals(160, content.photo.thumbnail.height)
        assertEquals("hello", content.caption.text)
        assertNull(content.selfDestructType)
    }

    @Test fun viewOncePhotoCarriesTheSelfDestructType() {
        val media = OutgoingMedia("/photo.jpg", "photo.jpg", "image/jpeg", MediaKind.Photo, 1000, 500)
        val content = media.toInputContent("", selfDestructType = TdApi.MessageSelfDestructTypeImmediately()) as TdApi.InputMessagePhoto
        assertTrue(content.selfDestructType is TdApi.MessageSelfDestructTypeImmediately)
    }

    @Test fun videoRequestPreservesDimensionsAndDuration() {
        val media = OutgoingMedia("/video.mp4", "video.mp4", "video/mp4", MediaKind.Video, 1920, 1080, 12)
        val content = media.toInputContent("") as TdApi.InputMessageVideo
        assertEquals(12, content.video.duration)
        assertEquals(1080, content.video.height)
        assertFalse(content.video.supportsStreaming)
        assertEquals("/video.mp4", (content.video.video as TdApi.InputFileLocal).path)
    }

    @Test fun stickerMessageKeepsSetIdentityEmojiAndPremiumFlag() {
        val mapper = TdMediaMapper(MediaFileStore())
        val sticker = TdApi.Sticker().apply {
            setId = 55
            id = 66
            width = 512
            height = 512
            emoji = "🎉"
            format = TdApi.StickerFormatWebm()
            this.sticker = file(10)
            thumbnail = TdApi.Thumbnail().apply {
                width = 128
                height = 128
                format = TdApi.ThumbnailFormatWebp()
                file = file(11)
            }
        }
        val content = TdApi.MessageSticker().apply {
            this.sticker = sticker
            isPremium = true
        }

        val media = mapper.media(content)!!

        assertEquals(MediaKind.Sticker, media.kind)
        assertEquals(55L, media.stickerSetId)
        assertEquals(66L, media.stickerId)
        assertEquals("🎉", media.stickerEmoji)
        assertTrue(media.isPremiumSticker)
        assertEquals("video/webm", media.mimeType)
        assertEquals(10, media.fileId)
        assertEquals(11, media.thumbnailFileId)
    }

    @Test fun stickerWithoutSetIdStaysASingleSticker() {
        val mapper = TdMediaMapper(MediaFileStore())
        val sticker = TdApi.Sticker().apply {
            setId = 0
            id = 7
            width = 512
            height = 512
            emoji = "🙂"
            format = TdApi.StickerFormatWebp()
            this.sticker = file(8)
        }

        val media = mapper.media(TdApi.MessageSticker().apply { this.sticker = sticker })!!

        assertEquals(MediaKind.Sticker, media.kind)
        assertEquals(0L, media.stickerSetId)
        assertEquals(7L, media.stickerId)
        assertFalse(media.isPremiumSticker)
    }

    @Test fun fileModeDoesNotTurnAnOriginalImageIntoCompressedPhoto() {
        val media = OutgoingMedia("/original.png", "original.png", "image/png", MediaKind.Document)
        val content = media.toInputContent("original") as TdApi.InputMessageDocument
        assertTrue(content.document.disableContentTypeDetection)
        assertEquals("/original.png", (content.document.document as TdApi.InputFileLocal).path)
        assertEquals("original", content.caption.text)
    }
}
