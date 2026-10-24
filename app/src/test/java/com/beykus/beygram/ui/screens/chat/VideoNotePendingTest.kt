package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
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
}
