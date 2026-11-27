package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContextScenePlacementTest {
    private val viewport = Rect(300f, 20f, 680f, 620f)

    @Test fun passiveMediaExcludeSpoilersSecretsAndAlbums() {
        val photo = MessageMedia(MediaKind.Photo, 1)
        val message = ChatMessage(1, 1, "caption", "", false, media = photo)
        assertTrue(message.supportsContextScene())
        assertTrue(message.copy(text = "", media = photo.copy(kind = MediaKind.Video)).supportsContextScene())
        assertFalse(message.copy(media = photo.copy(hasSpoiler = true)).supportsContextScene())
        assertFalse(message.copy(media = photo.copy(isSecret = true)).supportsContextScene())
        assertFalse(message.copy(mediaAlbumId = 1L).supportsContextScene())
        assertFalse(message.copy(media = photo.copy(kind = MediaKind.VideoNote)).supportsContextScene())
    }

    @Test fun keepsTheNearestSideThatFitsTheMenu() {
        val nearTop = Rect(312f, 30f, 530f, 190f)
        val nearBottom = Rect(400f, 435f, 658f, 595f)
        val top = scene(nearTop)
        val bottom = scene(nearBottom)
        assertEquals(nearTop, top.messageBounds)
        assertFalse(top.menu.placedAbove)
        assertEquals(nearBottom, bottom.messageBounds)
        assertTrue(bottom.menu.placedAbove)
    }

    @Test fun movesTheMessageAndKeepsTheMenuInsideASplitPane() {
        val message = Rect(480f, 170f, 676f, 470f)
        val result = scene(message)
        assertTrue(result.messageBounds.top < message.top)
        assertTrue(result.menu.x >= viewport.left)
        assertTrue(result.menu.x + 248 <= viewport.right)
        assertTrue(result.menu.y + requireNotNull(result.menu.maxHeightPx) <= viewport.bottom)
        assertEquals(message.width, result.messageBounds.width, 0f)
        assertEquals(message.height, result.messageBounds.height, 0f)
    }

    @Test fun letsExistingFallbackHandleLongOrUnreachableMessages() {
        val tall = Rect(320f, 20f, 640f, 590f)
        val offscreen = Rect(310f, 630f, 640f, 690f)
        assertNull(computeMessageContextScene(tall, viewport, 280, 140, 248, 8, false))
        assertNull(computeMessageContextScene(offscreen, viewport, 280, 140, 248, 8, false))
        assertNotNull(computeMessageContextScene(Rect(310f, 120f, 450f, 400f), viewport, 500, 140, 248, 8, false))
    }

    @Test fun reactionsAndMenuUseOppositeSidesOfTheMessage() {
        val message = Rect(400f, 260f, 620f, 420f)
        val result = requireNotNull(computeMessageContextScene(message, viewport, 240, 140, 248, 8, true, 210, 56))
        val reactions = requireNotNull(result.reactionBounds)
        if (result.menu.placedAbove) {
            assertTrue(reactions.top >= result.messageBounds.bottom + 8)
        } else {
            assertTrue(reactions.bottom <= result.messageBounds.top - 8)
        }
        assertTrue(reactions.left >= viewport.left && reactions.right <= viewport.right)
    }

    private fun scene(message: Rect) =
        requireNotNull(computeMessageContextScene(message, viewport, 280, 140, 248, 8, true))
}
