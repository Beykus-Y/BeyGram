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
}
