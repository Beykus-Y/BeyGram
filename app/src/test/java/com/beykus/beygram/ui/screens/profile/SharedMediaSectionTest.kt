package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSearchPage
import com.beykus.beygram.ui.screens.chat.SharedMediaWindow
import kotlinx.coroutines.runBlocking
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaSectionTest {
    @Test fun autoPaginationWaitsForIdleAndStopsOnError() {
        assertTrue(shouldAutoLoadSharedMediaPage(true, true, false, null))
        assertFalse(shouldAutoLoadSharedMediaPage(false, true, false, null))
        assertFalse(shouldAutoLoadSharedMediaPage(true, false, false, null))
        assertFalse(shouldAutoLoadSharedMediaPage(true, true, true, null))
        assertFalse(shouldAutoLoadSharedMediaPage(true, true, false, "network error"))
    }

    @Test fun manualRetryWorksAfterAutoPaginationStopsOnFailure() = runBlocking {
        var requests = 0
        val window = SharedMediaWindow(
            load = { _, _ ->
                requests++
                if (requests == 1) Result.failure(IllegalStateException("network"))
                else Result.success(MessageSearchPage(listOf(message(7)), nextFromMessageId = 0L))
            },
            failure = { "offline" }
        )
        window.restart(null)
        window.loadOlder()
        assertEquals("offline", window.error)
        assertFalse(shouldAutoLoadSharedMediaPage(true, window.hasOlder, window.loading, window.error))
        assertEquals(1, requests)
        // Explicit retry is still permitted and clears the error when the page arrives.
        window.loadOlder()
        assertEquals(2, requests)
        assertEquals(null, window.error)
        assertEquals(listOf(7L), window.messages.map { it.id })
        assertFalse(window.hasOlder)
    }

    @Test fun personalChatIncludesMusicTab() {
        assertEquals(SharedMediaKind.entries.toList(), chatSharedKinds)
        assertEquals(SharedMediaKind.Music, chatSharedKinds.last())
    }

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

    @Test fun linkUnderSpoilerDefersToChat() {
        assertTrue(sharedLinkUnderSpoiler(message(1, "скрыто", MessageTextEntity(0, 6, TextStyleKind.Spoiler), MessageTextEntity(0, 6, TextStyleKind.Url, "https://example.com"))))
    }

    @Test fun visibleLinkOpensFromList() {
        assertFalse(sharedLinkUnderSpoiler(message(1, "см. https://example.com тут", MessageTextEntity(4, 19, TextStyleKind.Url), MessageTextEntity(24, 3, TextStyleKind.Spoiler))))
    }

    @Test fun previewFadeMemoryRemembersFileOnce() {
        val id = 9_000_001
        assertFalse(SharedPreviewFadeMemory.contains(id))
        SharedPreviewFadeMemory.add(id)
        assertTrue(SharedPreviewFadeMemory.contains(id))
        SharedPreviewFadeMemory.add(id)
        assertTrue(SharedPreviewFadeMemory.contains(id))
    }
}
