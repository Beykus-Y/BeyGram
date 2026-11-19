package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNotePendingTest {
    private fun media(id: String) = OutgoingMedia(
        path = "/tmp/video-note-" + id + ".mp4",
        fileName = "video-note-" + id + ".mp4",
        mimeType = "video/mp4",
        kind = MediaKind.VideoNote,
        width = 384,
        height = 384,
        durationSeconds = 10,
        size = 1024L
    )

    @Test fun processingNoteIsShownWhileItsExportRuns() {
        val processing = listOf(PendingVideoNote(7L, "a", processing = true))
        assertEquals(listOf(PendingVideoNote(7L, "a", processing = true)), pendingVideoNotesFor(7L, processing, emptyList()))
    }

    @Test fun autoSendResultIsShownUntilTdlibAcceptsIt() {
        val results = listOf(VideoNoteExportResult(7L, listOf(media("a")), false, "a"))
        assertEquals(listOf(PendingVideoNote(7L, "a", processing = false)), pendingVideoNotesFor(7L, emptyList(), results))
    }

    @Test fun autoSendResultShowsItsThumbnailWhileWaiting() {
        val withThumbnail = media("a").copy(thumbnailPath = "/tmp/video-note-1-thumb.jpg")
        val results = listOf(VideoNoteExportResult(7L, listOf(withThumbnail), false, "a"))
        assertEquals(
            listOf(PendingVideoNote(7L, "a", processing = false, previewPath = "/tmp/video-note-1-thumb.jpg")),
            pendingVideoNotesFor(7L, emptyList(), results)
        )
    }

    @Test fun previewResultStaysInTheDialogNotInTheChat() {
        val results = listOf(VideoNoteExportResult(7L, listOf(media("a")), previewFirst = true, sessionName = "a"))
        assertEquals(emptyList<PendingVideoNote>(), pendingVideoNotesFor(7L, emptyList(), results))
    }

    @Test fun otherChatsAreFilteredOut() {
        val processing = listOf(PendingVideoNote(8L, "a", processing = true))
        val results = listOf(VideoNoteExportResult(9L, listOf(media("b")), false, "b"))
        assertEquals(emptyList<PendingVideoNote>(), pendingVideoNotesFor(7L, processing, results))
    }

    @Test fun sessionIsShownOnceWhileProcessingHandsOverToSending() {
        val processing = listOf(PendingVideoNote(7L, "a", processing = true))
        val results = listOf(VideoNoteExportResult(7L, listOf(media("a")), false, "a"))
        assertEquals(listOf(PendingVideoNote(7L, "a", processing = true)), pendingVideoNotesFor(7L, processing, results))
    }

    @Test fun forumNoteStaysInTheTopicItWasRecordedIn() {
        val processing = listOf(PendingVideoNote(7L, "a", processing = true, topicId = 11L))
        val results = listOf(VideoNoteExportResult(7L, listOf(media("b")), false, "b", topicId = 11L))
        assertEquals(emptyList<PendingVideoNote>(), pendingVideoNotesFor(7L, processing, results, topicId = 12L))
        assertEquals(listOf("a", "b"), pendingVideoNotesFor(7L, processing, results, topicId = 11L).map { it.sessionName })
    }
    @Test fun accountSpecificNotesNeverAppearInSameIdForeignChat() {
        val processing = listOf(PendingVideoNote(7L, "b-process", true, accountId = "B"))
        val results = listOf(VideoNoteExportResult(7L, listOf(media("b")), false, "b-export", accountId = "B"))
        assertEquals(emptyList<PendingVideoNote>(), pendingVideoNotesFor(7L, processing, results, accountId = "A"))
        assertEquals(listOf("b-process", "b-export"),
            pendingVideoNotesFor(7L, processing, results, accountId = "B").map { it.sessionName })
    }

    @Test fun feedListsTheNotesOfEveryTopicOfItsChat() {
        val processing = listOf(PendingVideoNote(7L, "a", processing = true, topicId = 11L))
        val results = listOf(VideoNoteExportResult(7L, listOf(media("b")), false, "b", topicId = 12L))
        assertEquals(listOf("a", "b"), pendingVideoNotesFor(7L, processing, results, topicId = null).map { it.sessionName })
    }

    @Test fun feedClaimsNotesOfAnyTopicOfItsOwnChatAndAccount() {
        val note = VideoNoteExportResult(7L, listOf(media("a")), false, "a", topicId = 11L, accountId = "A")
        assertTrue(note.claimedBy(7L, "A", topicId = null))
        assertFalse(note.claimedBy(8L, "A", topicId = null))
        assertFalse(note.claimedBy(7L, "B", topicId = null))
    }

    @Test fun topicViewClaimsOnlyTheNotesOfItsOwnTopic() {
        val note = VideoNoteExportResult(7L, listOf(media("a")), false, "a", topicId = 11L)
        assertTrue(note.claimedBy(7L, note.accountId, topicId = 11L))
        assertFalse(note.claimedBy(7L, note.accountId, topicId = 12L))
    }

    @Test fun feedSendsWithTheReplyStoredWithTheNoteNotTheComposersReply() {
        val note = VideoNoteExportResult(7L, listOf(media("a")), false, "a", topicId = 5L, replyToMessageId = 42L)
        assertEquals(42L, note.sendReplyId(feedSender = true, composerReplyId = null))
        assertEquals(42L, note.sendReplyId(feedSender = true, composerReplyId = 99L))
    }

    @Test fun topicViewKeepsUsingTheComposersReplyForANoteRecordedInTheTopic() {
        val note = VideoNoteExportResult(7L, listOf(media("a")), false, "a", topicId = 5L, replyToMessageId = 42L)
        assertEquals(99L, note.sendReplyId(feedSender = false, composerReplyId = 99L))
        assertNull(note.sendReplyId(feedSender = false, composerReplyId = null))
    }

    @Test fun noteRecordedInTheFeedKeepsItsReplyWhenATopicViewSendsIt() {
        val note = VideoNoteExportResult(7L, listOf(media("a")), false, "a", topicId = 5L, replyToMessageId = 42L, recordedInFeed = true)
        assertEquals(42L, note.sendReplyId(feedSender = false, composerReplyId = null))
    }

    @Test fun noteRecordedInTheFeedWithoutReplyNeverTakesTheComposersReply() {
        val note = VideoNoteExportResult(7L, listOf(media("a")), false, "a", topicId = 1L, recordedInFeed = true)
        assertNull(note.sendReplyId(feedSender = false, composerReplyId = 99L))
    }

    @Test fun retryAfterAcceptedSegmentsDoesNotRepeatTheReply() {
        assertEquals(42L, initialSendReply(nextIndex = 0, replyId = 42L))
        assertNull(initialSendReply(nextIndex = 1, replyId = 42L))
    }
}
