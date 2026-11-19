package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ComposerSendUncertainException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageDraft
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.domain.model.TextStyleKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSendSnapshotTest {

    private fun photo(name: String) = OutgoingMedia(
        path = "/uploads/$name", fileName = name, mimeType = "image/jpeg", kind = MediaKind.Photo, size = 1_000L
    )

    private fun video(name: String) = OutgoingMedia(
        path = "/uploads/$name", fileName = name, mimeType = "video/mp4", kind = MediaKind.Video, durationSeconds = 3, size = 2_000L
    )

    private fun document(name: String) = OutgoingMedia(
        path = "/uploads/$name", fileName = name, mimeType = "application/pdf", kind = MediaKind.Document, size = 3_000L
    )

    /** Staged items whose prepared form is the item itself, which is enough for the batch logic. */
    private fun staged(vararg media: OutgoingMedia): List<Pair<OutgoingMedia, OutgoingMedia>> = media.map { it to it }

    private class Call(val names: List<String>, val caption: String, val entities: List<MessageTextEntity>)

    /** Records every batch it is asked to send. [failAt] and [uncertainAt] are batch indexes that fail. */
    private class Recorder(private val failAt: Int? = null, private val uncertainAt: Int? = null) {
        val calls = mutableListOf<Call>()

        fun send(media: List<OutgoingMedia>, caption: String, entities: List<MessageTextEntity>): Result<Unit> {
            val index = calls.size
            calls += Call(media.map { it.fileName }, caption, entities)
            return when (index) {
                failAt -> Result.failure(IllegalStateException("refused"))
                uncertainAt -> Result.failure(ComposerSendUncertainException(null))
                else -> Result.success(Unit)
            }
        }
    }

    private class Run(val outcome: AttachmentSendOutcome, val confirmed: List<String>, val consumed: List<String>)

    private fun sendItems(
        items: List<Pair<OutgoingMedia, OutgoingMedia>>,
        recorder: Recorder,
        caption: String = "",
        entities: List<MessageTextEntity> = emptyList()
    ): Run {
        val confirmed = mutableListOf<String>()
        val consumed = mutableListOf<String>()
        val outcome = runBlocking {
            runAttachmentSend(
                items = items,
                caption = caption,
                captionEntities = entities,
                send = { media, batchCaption, batchEntities -> recorder.send(media, batchCaption, batchEntities) },
                onBatchConfirmed = { sent -> confirmed += sent.map { it.fileName } },
                onCaptionConsumed = { consumed += it }
            )
        }
        return Run(outcome, confirmed, consumed)
    }

    private fun snapshot(
        items: List<Pair<OutgoingMedia, OutgoingMedia>>,
        caption: String = "",
        entities: List<MessageTextEntity> = emptyList(),
        reply: Long? = null
    ) = AttachmentSendSnapshot(
        chatId = 7L,
        forumTopicId = 3L,
        replyToMessageId = reply,
        caption = caption,
        captionEntities = entities,
        options = MessageSendOptions(),
        items = items
    )

    @Test
    fun `photo video and document go out as three ordered batches`() {
        val recorder = Recorder()
        val run = sendItems(staged(photo("a.jpg"), video("b.mp4"), document("c.pdf")), recorder, caption = "hi")
        assertEquals(AttachmentSendOutcome.Completed(3), run.outcome)
        assertEquals(listOf(listOf("a.jpg"), listOf("b.mp4"), listOf("c.pdf")), recorder.calls.map { it.names })
        assertEquals(listOf("hi", "", ""), recorder.calls.map { it.caption })
        assertEquals(listOf("a.jpg", "b.mp4", "c.pdf"), run.confirmed)
        assertEquals(listOf("hi"), run.consumed)
    }

    @Test
    fun `photos and videos form one album and the caption rides on it`() {
        val recorder = Recorder()
        val run = sendItems(staged(photo("a.jpg"), video("b.mp4")), recorder, caption = "album")
        assertEquals(AttachmentSendOutcome.Completed(2), run.outcome)
        assertEquals(listOf(listOf("a.jpg", "b.mp4")), recorder.calls.map { it.names })
        assertEquals(listOf("album"), recorder.calls.map { it.caption })
    }

    @Test
    fun `unicode caption and its entities go only with the first batch`() {
        val caption = "Привет 👋🏽 мир https://example.com"
        val entity = MessageTextEntity(offset = 15, length = 19, kind = TextStyleKind.Url)
        val recorder = Recorder()
        sendItems(staged(photo("a.jpg"), document("b.pdf"), document("c.pdf")), recorder, caption = caption, entities = listOf(entity))
        assertEquals(listOf(caption, "", ""), recorder.calls.map { it.caption })
        assertEquals(listOf(listOf(entity), emptyList(), emptyList()), recorder.calls.map { it.entities })
    }

    @Test
    fun `an empty caption is never reported as consumed`() {
        val run = sendItems(staged(photo("a.jpg")), Recorder())
        assertEquals(AttachmentSendOutcome.Completed(1), run.outcome)
        assertTrue(run.consumed.isEmpty())
    }

    @Test
    fun `a failure between batches keeps the confirmed items out and returns the rest`() {
        val recorder = Recorder(failAt = 1)
        val run = sendItems(staged(photo("a.jpg"), video("b.mp4"), document("c.pdf")), recorder, caption = "hi")
        val failed = run.outcome as AttachmentSendOutcome.Failed
        assertEquals(1, failed.sentCount)
        assertEquals(3, failed.total)
        assertFalse(failed.uncertain)
        assertTrue(failed.captionConsumed)
        assertEquals(listOf("b.mp4", "c.pdf"), failed.remaining.map { it.first.fileName })
        assertEquals(listOf("a.jpg"), run.confirmed)
        assertEquals(listOf("hi"), run.consumed)
        assertEquals(2, recorder.calls.size)
    }

    @Test
    fun `sent and total count items, not batches, when a later single item fails`() {
        val recorder = Recorder(failAt = 1)
        val run = sendItems(staged(document("a.pdf"), photo("b.jpg"), video("c.mp4")), recorder)
        val failed = run.outcome as AttachmentSendOutcome.Failed
        assertEquals(1, failed.sentCount)
        assertEquals(3, failed.total)
        assertEquals(listOf("b.jpg", "c.mp4"), failed.remaining.map { it.first.fileName })
    }

    @Test
    fun `a failed first batch keeps the caption for the next attempt`() {
        val entity = MessageTextEntity(offset = 0, length = 2, kind = TextStyleKind.Url)
        val items = staged(photo("a.jpg"), document("b.pdf"))
        val run = sendItems(items, Recorder(failAt = 0), caption = "hi", entities = listOf(entity))
        val failed = run.outcome as AttachmentSendOutcome.Failed
        assertEquals(0, failed.sentCount)
        assertFalse(failed.captionConsumed)
        assertTrue(run.consumed.isEmpty())
        assertEquals(2, failed.remaining.size)

        val retryRecorder = Recorder()
        val retry = snapshot(items, caption = "hi", entities = listOf(entity)).remainderAfter(failed, MessageSendOptions())
        val second = sendItems(retry.items, retryRecorder, caption = retry.caption, entities = retry.captionEntities)
        assertEquals(AttachmentSendOutcome.Completed(2), second.outcome)
        assertEquals(listOf("hi", ""), retryRecorder.calls.map { it.caption })
        assertEquals(listOf(listOf(entity), emptyList()), retryRecorder.calls.map { it.entities })
    }

    @Test
    fun `an uncertain result removes nothing from the failing batch and sends nothing after it`() {
        val recorder = Recorder(uncertainAt = 1)
        val run = sendItems(staged(photo("a.jpg"), video("b.mp4"), document("c.pdf")), recorder, caption = "hi")
        val failed = run.outcome as AttachmentSendOutcome.Failed
        assertTrue(failed.uncertain)
        assertEquals(1, failed.sentCount)
        assertEquals(listOf("b.mp4", "c.pdf"), failed.remaining.map { it.first.fileName })
        assertEquals(listOf("a.jpg"), run.confirmed)
        assertEquals(2, recorder.calls.size)
    }

    @Test
    fun `an uncertain first batch confirms nothing and keeps the caption`() {
        val recorder = Recorder(uncertainAt = 0)
        val run = sendItems(staged(photo("a.jpg"), document("b.pdf")), recorder, caption = "hi")
        val failed = run.outcome as AttachmentSendOutcome.Failed
        assertTrue(failed.uncertain)
        assertEquals(0, failed.sentCount)
        assertFalse(failed.captionConsumed)
        assertTrue(run.confirmed.isEmpty())
        assertEquals(1, recorder.calls.size)
    }

    @Test
    fun `a retry sends only the remainder and does not repeat a consumed caption`() {
        val items = staged(photo("a.jpg"), video("b.mp4"), document("c.pdf"))
        val first = sendItems(items, Recorder(failAt = 1), caption = "hi")
        val failed = first.outcome as AttachmentSendOutcome.Failed
        val retry = snapshot(items, caption = "hi", reply = 9L).remainderAfter(failed, MessageSendOptions(sendNowConsent = true))

        val recorder = Recorder()
        val second = sendItems(retry.items, recorder, caption = retry.caption, entities = retry.captionEntities)
        assertEquals(AttachmentSendOutcome.Completed(2), second.outcome)
        assertEquals(listOf(listOf("b.mp4"), listOf("c.pdf")), recorder.calls.map { it.names })
        assertEquals(listOf("", ""), recorder.calls.map { it.caption })
        assertTrue(second.confirmed.none { it == "a.jpg" })
        assertEquals(9L, retry.replyToMessageId)
        assertEquals(3L, retry.forumTopicId)
        assertEquals(7L, retry.chatId)
        assertTrue(retry.options.sendNowConsent)
    }

    @Test
    fun `a snapshot keeps its reply and topic for every batch`() {
        val items = staged(photo("a.jpg"), document("b.pdf"))
        val snap = snapshot(items, caption = "x", reply = 42L)
        val replies = mutableListOf<Long?>()
        runBlocking {
            runAttachmentSend(
                items = snap.items,
                caption = snap.caption,
                captionEntities = snap.captionEntities,
                send = { _, _, _ -> replies += snap.replyToMessageId; Result.success(Unit) },
                onBatchConfirmed = {},
                onCaptionConsumed = {}
            )
        }
        assertEquals(listOf(42L, 42L), replies)
        assertEquals(3L, snap.forumTopicId)
    }

    @Test
    fun `the consumed caption clears the draft only while the draft still holds it`() {
        val entity = MessageTextEntity(offset = 0, length = 2, kind = TextStyleKind.Url)
        val sent = MessageDraft(text = "hi", entities = listOf(entity), replyToMessageId = 5L)
        val cleared = sent.withAttachmentCaptionConsumed("hi")
        assertEquals("", cleared.text)
        assertTrue(cleared.entities.isEmpty())
        assertEquals(5L, cleared.replyToMessageId)

        val edited = MessageDraft(text = "hi there", entities = listOf(entity), replyToMessageId = 5L)
        assertEquals(edited, edited.withAttachmentCaptionConsumed("hi"))
    }

    @Test
    fun `the consumed reply clears only while it is still the answered message`() {
        val draft = MessageDraft(text = "", replyToMessageId = 5L)
        assertNull(draft.withAttachmentReplyConsumed(5L).replyToMessageId)
        assertEquals(6L, MessageDraft(replyToMessageId = 6L).withAttachmentReplyConsumed(5L).replyToMessageId)
    }
}
