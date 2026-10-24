package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.StickerFileFormat
import com.beykus.beygram.domain.model.StickerItem
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerInputMappingTest {
    private fun file(id: Int) = TdApi.File().apply {
        this.id = id
        size = 100
        expectedSize = 100
        local = TdApi.LocalFile()
        remote = TdApi.RemoteFile()
    }

    private fun thumbnail(id: Int, format: TdApi.ThumbnailFormat = TdApi.ThumbnailFormatWebp()) =
        TdApi.Thumbnail().apply {
            this.format = format
            width = 128
            height = 96
            file = file(id)
        }

    @Test
    fun stickerRequestKeepsFileIdDimensionsEmojiAndStaticThumbnail() {
        val media = StickerItem(
            setId = 10,
            stickerId = 20,
            fileId = 77,
            thumbnailFileId = 78,
            thumbnailIsStatic = true,
            width = 512,
            height = 512,
            emoji = "😀",
            format = StickerFileFormat.Webp
        ).toOutgoingMedia()

        val content = media.toInputContent("") as TdApi.InputMessageSticker

        assertEquals(MediaKind.Sticker, media.kind)
        assertEquals(77, (content.sticker as TdApi.InputFileId).id)
        assertEquals(512, content.width)
        assertEquals(512, content.height)
        assertEquals("😀", content.emoji)
        assertEquals(78, (content.thumbnail.thumbnail as TdApi.InputFileId).id)
    }

    @Test
    fun webmStickerDoesNotPretendItsVideoThumbnailIsAStaticImage() {
        val media = StickerItem(
            setId = 1,
            stickerId = 2,
            fileId = 3,
            thumbnailFileId = 4,
            thumbnailIsStatic = false,
            width = 320,
            height = 320,
            emoji = "🙂",
            format = StickerFileFormat.Webm
        ).toOutgoingMedia()

        val content = media.toInputContent("") as TdApi.InputMessageSticker

        assertEquals(3, (content.sticker as TdApi.InputFileId).id)
        assertNull(content.thumbnail)
    }

    @Test
    fun gifRequestUsesInputAnimationAndKeepsCaptionAndThumbnail() {
        val media = com.beykus.beygram.domain.model.AnimationItem(
            fileId = 90,
            uniqueId = "gif-90",
            thumbnailFileId = 91,
            thumbnailWidth = 320,
            thumbnailHeight = 180,
            width = 640,
            height = 360,
            durationSeconds = 4,
            fileName = "party.gif",
            mimeType = "image/gif",
            size = 2_000
        ).toOutgoingMedia()

        val content = media.toInputContent("hello") as TdApi.InputMessageAnimation

        assertEquals(MediaKind.Animation, media.kind)
        assertEquals("hello", content.caption.text)
        assertEquals(90, (content.animation.animation as TdApi.InputFileId).id)
        assertEquals(4, content.animation.duration)
        assertEquals(640, content.animation.width)
        assertEquals(360, content.animation.height)
        assertEquals(91, (content.animation.thumbnail.thumbnail as TdApi.InputFileId).id)
    }

    @Test
    fun stickerMetadataMapsPremiumAndStaticThumbnailWithoutRegisteringVideoPreviews() {
        val sticker = TdApi.Sticker().apply {
            id = 5
            setId = 6
            width = 512
            height = 512
            emoji = "😎"
            format = TdApi.StickerFormatTgs()
            fullType = TdApi.StickerFullTypeRegular().apply { premiumAnimation = file(7) }
            thumbnail = thumbnail(8, TdApi.ThumbnailFormatWebp())
            this.sticker = file(9)
        }

        val mapped = sticker.toStickerItem { it.id }!!

        assertEquals(6L, mapped.setId)
        assertEquals(5L, mapped.stickerId)
        assertEquals(9, mapped.fileId)
        assertEquals(8, mapped.thumbnailFileId)
        assertEquals(StickerFileFormat.Tgs, mapped.format)
        assertTrue(mapped.thumbnailIsStatic)
        assertTrue(mapped.premium)
    }

    @Test
    fun animationMappingKeepsUniqueIdAndMinithumbnail() {
        val animation = TdApi.Animation().apply {
            duration = 3
            width = 320
            height = 200
            fileName = "wave.mp4"
            mimeType = "video/mp4"
            animation = file(30).apply { remote = TdApi.RemoteFile().apply { uniqueId = "same-gif" } }
            thumbnail = thumbnail(31, TdApi.ThumbnailFormatJpeg())
            minithumbnail = TdApi.Minithumbnail(20, 12, byteArrayOf(1, 2, 3))
        }

        val mapped = animation.toAnimationItem { it.id }!!

        assertEquals(30, mapped.fileId)
        assertEquals("same-gif", mapped.key)
        assertEquals(31, mapped.thumbnailFileId)
        assertEquals(3, mapped.durationSeconds)
        assertEquals(320, mapped.width)
        assertEquals("wave.mp4", mapped.fileName)
        assertFalse(mapped.minithumbnail!!.isEmpty())
    }
}
