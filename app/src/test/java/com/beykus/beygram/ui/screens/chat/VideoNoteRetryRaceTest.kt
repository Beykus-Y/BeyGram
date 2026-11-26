package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteRetryRaceTest {
    private fun media(id: String) = OutgoingMedia(
        path = "/tmp/video-note-" + id + ".mp4",
        fileName = "video-note-" + id + ".mp4",
        mimeType = "video/mp4",
        kind = MediaKind.VideoNote,
        size = 1024L
    )

    @Test fun aFinishedAttemptDoesNotReleaseANewerClaim() {
        val retried = VideoNoteExportResult(
            chatId = 7L,
            media = listOf(media("race")),
            previewFirst = true,
            sessionName = "test-retry-race",
            sending = true,
            attemptId = 2L
        )

        assertTrue(retried.finishAttempt(attemptId = 1L).sending)
        assertFalse(retried.finishAttempt(attemptId = 2L).sending)
    }

    @Test fun theClaimGetsANewTokenForEveryAttempt() {
        val first = VideoNoteExportResult(7L, listOf(media("race")), true, "test-retry-race")
        val claimed = first.copy(sending = true, attemptId = first.attemptId + 1)

        assertEquals(1L, claimed.attemptId)
        assertEquals(2L, claimed.copy(attemptId = claimed.attemptId + 1).attemptId)
    }
}