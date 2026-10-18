package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaSectionTest {
    private fun message(id: Long, text: String = "", vararg entities: MessageTextEntity) =
        ChatMessage(id = id, chatId = 1L, text = text, time = "", isOutgoing = false, entities = entities.toList())

    @Test fun linkTitleShowsAddressHiddenUnderText() {
        assertEquals("https://example.com/a", sharedLinkTitle(message(1, "открыть сайт", MessageTextEntity(0, 12, TextStyleKind.Url, "https://example.com/a"))))
    }

    @Test fun linkTitleShowsPlainUrlFromText() {
        assertEquals("https://example.com", sharedLinkTitle(message(1, "см. https://example.com тут", MessageTextEntity(4, 19, TextStyleKind.Url))))
    }

    @Test fun linkTitleFallsBackToTextWithoutLink() {
        assertEquals("без ссылки", sharedLinkTitle(message(1, " без ссылки ")))
    }

    @Test fun linkTitleIgnoresEntityOutsideText() {
        assertEquals("ок", sharedLinkTitle(message(1, "ок", MessageTextEntity(5, 3, TextStyleKind.Url))))
    }

    @Test fun sharedPreviewLoadsOnlyAfterSpoilerIsRevealed() {
        val spoiler = MessageMedia(MediaKind.Photo, fileId = 1, hasSpoiler = true)
        assertFalse(shouldLoadSharedPreview(spoiler, revealed = false))
        assertTrue(shouldLoadSharedPreview(spoiler, revealed = true))
    }

    @Test fun sharedPreviewSkipsSecretAndVideoWithoutThumbnail() {
        assertFalse(shouldLoadSharedPreview(MessageMedia(MediaKind.Photo, fileId = 1, isSecret = true), revealed = true))
        assertFalse(shouldLoadSharedPreview(MessageMedia(MediaKind.Video, fileId = 2), revealed = true))
        assertTrue(shouldLoadSharedPreview(MessageMedia(MediaKind.Video, fileId = 2, thumbnailFileId = 3), revealed = true))
    }

    @Test fun secretAttachmentOpensInChatEvenWhenDownloaded() {
        assertEquals(SharedRowAction.ShowInChat, sharedRowAction(MessageMedia(MediaKind.Document, fileId = 1, isSecret = true), "/files/a.pdf"))
    }

    @Test fun notDownloadedAttachmentIsDownloadedFirst() {
        assertEquals(SharedRowAction.Download, sharedRowAction(MessageMedia(MediaKind.Document, fileId = 2), null))
    }

    @Test fun downloadedAttachmentOpensFromItsPath() {
        assertEquals(SharedRowAction.Open("/files/a.pdf"), sharedRowAction(MessageMedia(MediaKind.Document, fileId = 3), "/files/a.pdf"))
    }
}
