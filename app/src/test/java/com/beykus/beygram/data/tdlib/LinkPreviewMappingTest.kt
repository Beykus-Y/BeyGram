package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class LinkPreviewMappingTest {
    private fun file(id: Int) = TdApi.File().apply {
        this.id = id
        size = 100
        local = TdApi.LocalFile()
        remote = TdApi.RemoteFile()
    }

    private fun size(id: Int, width: Int, height: Int) =
        TdApi.PhotoSize("s$id", file(id), width, height, intArrayOf())

    private fun photo(vararg sizes: TdApi.PhotoSize) = TdApi.Photo(false, null, arrayOf(*sizes))

    private fun preview(kind: TdApi.LinkPreviewType) = TdApi.LinkPreview().apply {
        displayUrl = "https://example.com/story"
        siteName = "Example"
        title = "A story"
        description = TdApi.FormattedText("Short summary", emptyArray())
        type = kind
    }

    private fun text(preview: TdApi.LinkPreview?) = TdApi.MessageText().apply {
        text = TdApi.FormattedText("https://example.com/story", emptyArray())
        linkPreview = preview
    }

    @Test fun articlePhotoMapsFieldsAndChoosesSmallestSuitableSize() {
        val store = MediaFileStore()
        val mapper = TdMediaMapper(store)
        val article = TdApi.LinkPreviewTypeArticle(photo(
            size(1, 80, 60),
            size(2, 320, 240),
            size(3, 1280, 960)
        ))

        val card = mapper.linkPreview(text(preview(article)), isSecretChat = false)!!

        assertEquals("https://example.com/story", card.displayUrl)
        assertEquals("Example", card.siteName)
        assertEquals("A story", card.title)
        assertEquals("Short summary", card.description)
        assertEquals(2, card.photoFileId)
        assertEquals(320, card.photoWidth)
        assertEquals(240, card.photoHeight)
        assertEquals(100L, store.observe(2).value.size)
    }

    @Test fun photoAndVideoCoverBecomeTheCardImage() {
        val store = MediaFileStore()
        val mapper = TdMediaMapper(store)
        val photoCard = mapper.linkPreview(text(preview(TdApi.LinkPreviewTypePhoto(photo(size(4, 640, 480))))), false)!!
        val videoCard = mapper.linkPreview(text(preview(TdApi.LinkPreviewTypeVideo(TdApi.Video(), photo(size(5, 500, 280)), 0))), false)!!

        assertEquals(4, photoCard.photoFileId)
        assertEquals(5, videoCard.photoFileId)
        assertEquals(500, videoCard.photoWidth)
    }

    @Test fun unknownTypeKeepsTextOnlyCard() {
        val mapper = TdMediaMapper(MediaFileStore())
        val card = mapper.linkPreview(text(preview(TdApi.LinkPreviewTypeUnsupported())), false)!!

        assertEquals("Example", card.siteName)
        assertEquals("A story", card.title)
        assertNull(card.photoFileId)
        assertEquals(0, card.photoWidth)
    }

    @Test fun secretChatSuppressesTheWholePreview() {
        val mapper = TdMediaMapper(MediaFileStore())
        val content = text(preview(TdApi.LinkPreviewTypeArticle(photo(size(6, 320, 240)))))

        assertNull(visibleLinkPreview(content, isSecretChat = true))
        assertNull(mapper.linkPreview(content, isSecretChat = true))
        assertNotNull(mapper.linkPreview(content, isSecretChat = false))
    }

    @Test fun missingPreviewOrNonTextContentDoesNotCreateACard() {
        val mapper = TdMediaMapper(MediaFileStore())

        assertNull(mapper.linkPreview(text(null), false))
        assertNull(mapper.linkPreview(TdApi.MessagePhoto(), false))
    }

    @Test fun emptyPreviewIsNotShown() {
        val mapper = TdMediaMapper(MediaFileStore())
        val empty = TdApi.LinkPreview().apply {
            displayUrl = ""
            siteName = ""
            title = ""
            description = TdApi.FormattedText("", emptyArray())
            type = TdApi.LinkPreviewTypeUnsupported()
        }

        assertNull(mapper.linkPreview(text(empty), false))
    }
}
