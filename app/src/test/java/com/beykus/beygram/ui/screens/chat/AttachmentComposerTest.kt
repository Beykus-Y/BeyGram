package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentComposerTest {

    private fun video(size: Long = 10_000_000L, duration: Int = 30) = OutgoingMedia(
        path = "/uploads/a/original/clip.mp4",
        fileName = "clip.mp4",
        mimeType = "video/mp4",
        kind = MediaKind.Video,
        width = 1920,
        height = 1080,
        durationSeconds = duration,
        size = size
    )

    private fun audio(size: Long = 2_000_000L, duration: Int = 120) = OutgoingMedia(
        path = "/uploads/b/original/song.mp3",
        fileName = "song.mp3",
        mimeType = "audio/mpeg",
        kind = MediaKind.Document,
        durationSeconds = duration,
        size = size
    )

    @Test
    fun compactMenuBalancesRowsWithinThePane() {
        assertEquals(5, attachmentMenuColumns(360f, 1f, 9))
        assertEquals(3, attachmentMenuColumns(300f, 1f, 9))
        assertEquals(3, attachmentMenuColumns(360f, 1.5f, 9))
        assertEquals(9, attachmentMenuColumns(640f, 1f, 9))
    }

    @Test
    fun `every attachment type is visible without horizontal scrolling`() {
        val actions = attachmentActions(allowsPolls = true)
        assertEquals(9, actions.size)
        assertEquals(AttachmentAction.entries.toList(), actions)
        assertTrue(AttachmentAction.Location.isChatObject)
        assertTrue(AttachmentAction.Dice.isChatObject)
    }

    @Test
    fun `music sits with media but replaces the tray and disappears in secret chats`() {
        assertTrue(!AttachmentAction.Music.isChatObject)
        assertTrue(AttachmentAction.Music.replacesTray)
        assertTrue(!AttachmentAction.Audio.replacesTray)
        val actions = attachmentActions(allowsPolls = true, allowsMusic = false)
        assertTrue(AttachmentAction.Music !in actions)
        assertEquals(8, actions.size)
    }

    @Test
    fun `polls disappear where Telegram rejects them but dice remain`() {
        val actions = attachmentActions(allowsPolls = false)
        assertTrue(AttachmentAction.Poll !in actions)
        assertTrue(AttachmentAction.Dice in actions)
        assertEquals(8, actions.size)
    }

    @Test
    fun `quality applies to videos, video documents awaiting conversion, and plain audio`() {
        assertEquals(MediaQualityKind.Video, video().qualityKind())
        assertEquals(MediaQualityKind.Audio, audio().qualityKind())
        assertNull(video().copy(kind = MediaKind.Photo).qualityKind())
        // An MP4 whose codec is not confirmed (AV1/VP9) is imported as a document; the composer still offers a
        // transcode, and a successful one turns it into a video message (#1175).
        assertEquals(
            MediaQualityKind.Video,
            video().copy(kind = MediaKind.Document, mimeType = "video/mp4").qualityKind(),
        )
        // Other video containers stay documents with no quality pass.
        assertNull(video().copy(kind = MediaKind.Document, mimeType = "video/x-matroska").qualityKind())
        assertNull(video().copy(kind = MediaKind.Document, mimeType = "application/pdf").qualityKind())
    }

    @Test
    fun `quality state is bound to one staged path`() {
        val first = video()
        val second = first.copy(path = "/uploads/b/original/other.mp4", fileName = "other.mp4")
        val preparations = mapOf(first.path to MediaPreparation(mode = MediaQualityMode.Compact))

        assertEquals(MediaQualityMode.Compact, preparationFor(preparations, first)?.mode)
        assertNull(preparationFor(preparations, second))
    }

    @Test
    fun `file delivery keeps the original bytes even when a compressed preview exists`() {
        val original = video()
        val compressed = original.copy(path = "/uploads/a/compressed.mp4", size = 3_000_000L)
        val preparation = MediaPreparation(
            mode = MediaQualityMode.Compact,
            delivery = MediaDeliveryMode.File,
            prepared = compressed
        )

        val outgoing = outgoingMediaFor(original, preparation)

        assertEquals(MediaKind.Document, outgoing.kind)
        assertEquals(original.path, outgoing.path)
        assertEquals(original.size, outgoing.size)
    }

    @Test
    fun `media delivery uses the prepared file but original mode keeps the source`() {
        val original = video()
        val compressed = original.copy(path = "/uploads/a/compressed.mp4", size = 3_000_000L)

        val media = outgoingMediaFor(
            original,
            MediaPreparation(mode = MediaQualityMode.Balanced, prepared = compressed)
        )
        val untouched = outgoingMediaFor(
            original,
            MediaPreparation(mode = MediaQualityMode.Original, prepared = compressed)
        )

        assertEquals(compressed.path, media.path)
        assertEquals(original.path, untouched.path)
        assertEquals(original.kind, untouched.kind)
    }

    @Test
    fun `a converted AV1 document is sent as a video message`() {
        // The importer keeps AV1/VP9 as a document; a successful transcode produces an H.264 video item, and that item
        // must be the one handed to TDLib, not the original document (#1175).
        val document = video().copy(kind = MediaKind.Document, mimeType = "video/mp4")
        val converted = document.copy(
            path = "/uploads/a/compressed.mp4",
            kind = MediaKind.Video,
            mimeType = "video/mp4",
            size = 4_000_000L,
        )

        val outgoing = outgoingMediaFor(
            document,
            MediaPreparation(mode = MediaQualityMode.Balanced, prepared = converted),
        )

        assertEquals(MediaKind.Video, outgoing.kind)
        assertEquals(converted.path, outgoing.path)
    }

    @Test
    fun `original mode on an unconvertible document keeps it a document`() {
        // The fallback: the user chose the original, the codec stays unconfirmed, so the file is sent as a document.
        val document = video().copy(kind = MediaKind.Document, mimeType = "video/mp4")

        val outgoing = outgoingMediaFor(document, MediaPreparation(mode = MediaQualityMode.Original))

        assertEquals(MediaKind.Document, outgoing.kind)
        assertEquals(document.path, outgoing.path)
    }

    @Test
    fun `estimates are approximate and only for known durations`() {
        val clip = video(size = 20_000_000L, duration = 60)
        val compact = qualityEstimateBytes(clip, MediaQualityMode.Compact)
        val balanced = qualityEstimateBytes(clip, MediaQualityMode.Balanced)
        val original = qualityEstimateBytes(clip, MediaQualityMode.Original)

        assertTrue(compact != null && balanced != null)
        assertTrue(compact!! < balanced!!)
        assertEquals(clip.size, original!!)
        assertNull(qualityEstimateBytes(clip.copy(durationSeconds = 0), MediaQualityMode.Compact))

        val track = audio(duration = 60)
        val audioCompact = qualityEstimateBytes(track, MediaQualityMode.Compact)
        val audioBalanced = qualityEstimateBytes(track, MediaQualityMode.Balanced)
        assertTrue(audioCompact != null && audioBalanced != null)
        assertTrue(audioCompact!! < audioBalanced!!)
    }

    @Test
    fun menuShowsGalleryFirstAndDiceLastWithPolls() {
        val actions = attachmentActions(allowsPolls = true)
        assertEquals(
            listOf(AttachmentAction.Camera, AttachmentAction.PhotoVideo, AttachmentAction.File, AttachmentAction.Music),
            actions.take(4)
        )
        assertEquals(AttachmentAction.Dice, actions.last())
        assertEquals(9, actions.size)
    }
}
