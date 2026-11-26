package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentEditTest {
    private fun item(path: String, kind: MediaKind = MediaKind.Photo, spoiler: Boolean = false) =
        OutgoingMedia(path = path, fileName = "photo.jpg", mimeType = "image/jpeg", kind = kind, hasSpoiler = spoiler)

    @Test fun spoilerSurvivesTheEdit() {
        val first = item("/first")
        val replaced = item("/replaced", spoiler = true)
        val last = item("/last")
        val edited = item("/edited")

        val result = replaceEditedAttachment(listOf(first, replaced, last), replaced, edited, keepOriginal = false)

        assertEquals(listOf("/first", "/edited", "/last"), result.map { it.path })
        assertTrue(result[1].hasSpoiler)
        assertFalse(result[0].hasSpoiler)
    }

    @Test fun theEditedPhotoKeepsItsPlaceInTheAlbum() {
        val replaced = item("/second")
        val pending = listOf(item("/first"), replaced, item("/third"))

        val result = replaceEditedAttachment(pending, replaced, item("/edited"), keepOriginal = false)

        assertEquals(listOf("/first", "/edited", "/third"), result.map { it.path })
    }

    @Test fun aKeptOriginalFollowsTheEditAndBecomesADocument() {
        val replaced = item("/replaced", spoiler = true)
        val pending = listOf(item("/before"), replaced, item("/after"))

        val result = replaceEditedAttachment(pending, replaced, item("/edited"), keepOriginal = true)

        assertEquals(listOf("/before", "/edited", "/replaced", "/after"), result.map { it.path })
        assertTrue(result[1].hasSpoiler)
        assertEquals(MediaKind.Document, result[2].kind)
        assertTrue(result[2].hasSpoiler)
    }

    @Test fun aMissingSourceAppendsInsteadOfDroppingTheEdit() {
        val pending = listOf(item("/a"))

        val result = replaceEditedAttachment(pending, item("/gone"), item("/edited"), keepOriginal = true)

        assertEquals(listOf("/a", "/edited", "/gone"), result.map { it.path })
        assertEquals(MediaKind.Document, result[2].kind)
    }
}