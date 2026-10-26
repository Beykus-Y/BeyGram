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
    fun `every attachment type is visible without horizontal scrolling`() {
        val actions = attachmentActions(allowsPolls = true)
        assertEquals(8, actions.size)
        assertEquals(AttachmentAction.entries.toList(), actions)
        assertTrue(AttachmentAction.Location.isChatObject)
        assertTrue(AttachmentAction.Dice.isChatObject)
    }

    @Test
    fun `polls disappear where Telegram rejects them but dice remain`() {
        val actions = attachmentActions(allowsPolls = false)
        assertTrue(AttachmentAction.Poll !in actions)
        assertTrue(AttachmentAction.Dice in actions)
        assertEquals(7, actions.size)
    }

    @Test
    fun `quality only applies to video messages and plain audio documents`() {
        assertEquals(MediaQualityKind.Video, video().qualityKind())
        assertEquals(MediaQualityKind.Audio, audio().qualityKind())
        assertNull(video().copy(kind = MediaKind.Photo).qualityKind())
        assertNull(video().copy(kind = MediaKind.Document, mimeType = "video/mp4").qualityKind())
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
}
