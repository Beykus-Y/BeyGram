package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.data.media.VideoNoteExportException
import com.beykus.beygram.data.media.VideoNoteOutputIssue
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteDeliveryStateTest {
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

    @Test fun failedFirstNoteDoesNotDropTheSecond() = runBlocking {
        val first = VideoNoteExportResult(123L, listOf(media("a")), false, "test-a")
        val second = VideoNoteExportResult(123L, listOf(media("b")), false, "test-b")
        VideoNoteExportCenter.publish(first)
        VideoNoteExportCenter.publish(second)
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(first.chatId, first.sessionName, null) { _, _ ->
            Result.failure(IllegalStateException("offline"))
        })
        withTimeout(3000) {
            VideoNoteExportCenter.results.first { list ->
                list.any { it.sessionName == first.sessionName && it.requiresPreview && !it.sending }
            }
        }
        val remaining = VideoNoteExportCenter.results.value
        assertEquals(2, remaining.count { it.chatId == 123L })
        assertTrue(remaining.any { it.sessionName == second.sessionName })
        VideoNoteExportCenter.discard(first.chatId, first.sessionName)
        VideoNoteExportCenter.discard(second.chatId, second.sessionName)
    }

    @Test fun sendContinuesFromFirstUnsentSegmentAndOnlyThenAcks() = runBlocking {
        val item = VideoNoteExportResult(234L, listOf(media("one"), media("two")), true, "test-segments")
        VideoNoteExportCenter.publish(item)
        val attempt = mutableListOf<String>()
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { media, _ ->
            attempt += media.fileName
            if (attempt.size == 2) Result.failure(IllegalStateException("network"))
            else Result.success(Unit)
        })
        withTimeout(3000) {
            VideoNoteExportCenter.results.first { list ->
                list.any { it.sessionName == item.sessionName && it.nextIndex == 1 && it.requiresPreview && !it.sending }
            }
        }
        val retry = mutableListOf<String>()
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { media, _ ->
            retry += media.fileName
            Result.success(Unit)
        })
        withTimeout(3000) {
            VideoNoteExportCenter.results.first { list -> list.none { it.sessionName == item.sessionName } }
        }
        assertEquals(listOf("video-note-one.mp4", "video-note-two.mp4"), attempt)
        assertEquals(listOf("video-note-two.mp4"), retry)
    }

    @Test fun multiplePreviewsRemainQueuedUntilExplicitlyDismissed() {
        val first = VideoNoteExportResult(345L, listOf(media("preview1")), true, "test-preview1")
        val second = VideoNoteExportResult(345L, listOf(media("preview2")), true, "test-preview2")
        VideoNoteExportCenter.publish(first)
        VideoNoteExportCenter.publish(second)
        val pending = VideoNoteExportCenter.results.value.filter { it.chatId == 345L && it.requiresPreview }
        assertEquals(listOf("test-preview1", "test-preview2"), pending.map { it.sessionName })
        VideoNoteExportCenter.discard(first.chatId, first.sessionName)
        assertTrue(VideoNoteExportCenter.results.value.any { it.sessionName == second.sessionName })
        VideoNoteExportCenter.discard(second.chatId, second.sessionName)
        assertFalse(VideoNoteExportCenter.results.value.any { it.chatId == 345L })
    }

    @Test fun outboundJobOutlivesCallerScope(): Unit = runBlocking {
        val item = VideoNoteExportResult(456L, listOf(media("app-scope")), false, "test-app-scope")
        VideoNoteExportCenter.publish(item)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { _, _ ->
            entered.complete(Unit)
            finish.await()
            Result.success(Unit)
        })
        entered.await()
        assertTrue(VideoNoteExportCenter.results.value.any { it.sessionName == item.sessionName && it.sending })
        finish.complete(Unit)
        withTimeout(3000) {
            VideoNoteExportCenter.results.first { list -> list.none { it.sessionName == item.sessionName } }
        }
        Unit
    }
    @Test fun differentAccountCannotClaimOrDiscardQueuedVideoNote() = runBlocking {
        val sameNumericChat = 711L
        val session = "account-isolation-test"
        val source = VideoNoteExportResult(sameNumericChat, listOf(media("owner")), false,
            session, accountId = "account-owner")
        VideoNoteExportCenter.publish(source)
        var foreignSendInvoked = false
        assertEquals(VideoNoteSendStart.NotFound, VideoNoteExportCenter.send(sameNumericChat, session, null, "account-foreign") { _, _ ->
            foreignSendInvoked = true
            Result.success(Unit)
        })
        assertFalse(foreignSendInvoked)
        VideoNoteExportCenter.discard(sameNumericChat, session, "account-foreign")
        assertTrue(VideoNoteExportCenter.results.value.any {
            it.accountId == "account-owner" && it.chatId == sameNumericChat && it.sessionName == session
        })
        VideoNoteExportCenter.discard(sameNumericChat, session, "account-owner")
        assertFalse(VideoNoteExportCenter.results.value.any {
            it.accountId == "account-owner" && it.sessionName == session
        })
    }

    @Test fun failedSendKeepsTheReasonAndARetryClearsIt() = runBlocking {
        val item = VideoNoteExportResult(812L, listOf(media("reason")), false, "test-failure-reason")
        VideoNoteExportCenter.publish(item)
        val reason = UserMessageException(UiText.Raw("Файл недоступен"))
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { _, _ ->
            Result.failure(reason)
        })
        val failed = withTimeout(3000) {
            VideoNoteExportCenter.results.first { list ->
                list.any { it.sessionName == item.sessionName && it.requiresPreview && !it.sending }
            }.first { it.sessionName == item.sessionName }
        }
        // The dialog needs the reason, not just "the draft is back in preview" (P0 #1228).
        assertEquals(VideoNoteFailureStage.Send, failed.failure?.stage)
        assertEquals(UiText.Raw("Файл недоступен"), failed.failure?.message)
        assertTrue(failed.failure?.detail.orEmpty().contains("UserMessageException"))

        // A retry clears the visible reason while it runs and removes the draft once accepted.
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { _, _ ->
            Result.success(Unit)
        })
        withTimeout(3000) {
            VideoNoteExportCenter.results.first { list -> list.none { it.sessionName == item.sessionName } }
        }
        Unit
    }

    @Test fun aSecondSendWhileSendingIsRejectedInsteadOfDuplicatingTheNote() = runBlocking {
        val item = VideoNoteExportResult(913L, listOf(media("single-flight")), false, "test-single-flight")
        VideoNoteExportCenter.publish(item)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        assertEquals(VideoNoteSendStart.Started, VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { _, _ ->
            entered.complete(Unit)
            finish.await()
            Result.success(Unit)
        })
        entered.await()
        assertEquals(
            VideoNoteSendStart.AlreadySending,
            VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { _, _ -> Result.success(Unit) }
        )
        finish.complete(Unit)
        withTimeout(3000) {
            VideoNoteExportCenter.results.first { list -> list.none { it.sessionName == item.sessionName } }
        }
        Unit
    }

    @Test fun aTapOnAMissingDraftIsReportedNotIgnored() {
        // ChatScreen used to swallow `false` and the Send button looked dead.
        assertEquals(
            VideoNoteSendStart.NotFound,
            VideoNoteExportCenter.send(1_014L, "missing-session", null) { _, _ -> Result.success(Unit) }
        )
    }

    @Test fun aPlainFailureGetsTheGenericLineAndAnExceptionDetail() {
        val failure = videoNoteSendFailure(IllegalStateException("network down"))
        assertEquals(UiText.Res(com.beykus.beygram.R.string.chat_video_note_send_failed), failure.message)
        assertEquals("IllegalStateException: network down", failure.detail)
    }

    @Test fun anUnreadableExportIsExplainedToTheUser() {
        val unreadable = videoNoteExportFailure(VideoNoteExportException(VideoNoteOutputIssue.NoDecodableFrame))
        assertEquals(VideoNoteFailureStage.Processing, unreadable.stage)
        assertEquals(UiText.Res(R.string.recorder_unreadable_video), unreadable.message)
        assertTrue(unreadable.detail.contains("NoDecodableFrame"))

        val codec = videoNoteExportFailure(VideoNoteExportException(VideoNoteOutputIssue.NotH264))
        assertEquals(UiText.Res(R.string.recorder_unsupported_codec), codec.message)

        // Anything that is not a typed export rejection keeps the generic line but never loses the technical detail.
        val generic = videoNoteExportFailure(IllegalStateException("No space left on device"))
        assertEquals(UiText.Res(R.string.recorder_save_failed), generic.message)
        assertEquals("IllegalStateException: No space left on device", generic.detail)
    }
}
