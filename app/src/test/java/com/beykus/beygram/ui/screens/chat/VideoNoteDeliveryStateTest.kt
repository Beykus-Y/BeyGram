package com.beykus.beygram.ui.screens.chat

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
        assertTrue(VideoNoteExportCenter.send(first.chatId, first.sessionName, null) { _, _ ->
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
        assertTrue(VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { media, _ ->
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
        assertTrue(VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { media, _ ->
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
        assertTrue(VideoNoteExportCenter.send(item.chatId, item.sessionName, null) { _, _ ->
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
    }
    @Test fun differentAccountCannotClaimOrDiscardQueuedVideoNote() = runBlocking {
        val sameNumericChat = 711L
        val session = "account-isolation-test"
        val source = VideoNoteExportResult(sameNumericChat, listOf(media("owner")), false,
            session, accountId = "account-owner")
        VideoNoteExportCenter.publish(source)
        var foreignSendInvoked = false
        assertFalse(VideoNoteExportCenter.send(sameNumericChat, session, null, "account-foreign") { _, _ ->
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
}
