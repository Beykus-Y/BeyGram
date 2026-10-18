package com.beykus.beygram.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ReplyOutboxTest {
    @get:Rule val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "reply-outbox")

    private fun outbox() = ReplyOutbox(directory)

    @Test fun cyrillicReplyWithNewlineSurvivesReopen() {
        val added = outbox().add(chatId = -1001234L, groupId = 7, maxId = 42, replyToMessageId = 99L, text = "Привет!\nВторая строка", nowMillis = 1_000L)
        assertEquals(listOf(added), outbox().entries())
    }

    @Test fun removeDeletesOnlyTheSentReply() {
        val outbox = outbox()
        val sent = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "первый", nowMillis = 1L)
        val pending = outbox.add(chatId = 2L, groupId = 2, maxId = 2, replyToMessageId = 0L, text = "второй", nowMillis = 2L)
        assertTrue(outbox.remove(sent.id))
        assertEquals(listOf(pending), outbox().entries())
    }

    @Test fun failedAttemptsAreCountedUntilTheReplyIsDropped() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L)
        assertFalse(outbox.recordFailure(entry, maxAttempts = 3))
        assertFalse(outbox.recordFailure(outbox().entries().single(), maxAttempts = 3))
        assertEquals(2, outbox().entries().single().attempts)
        assertTrue(outbox.recordFailure(outbox().entries().single(), maxAttempts = 3))
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun failureDoesNotWriteBackAClearedReply() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L)
        outbox.clear()
        assertTrue(outbox.recordFailure(entry, maxAttempts = 3))
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun expiredRepliesAreDiscardedAndRecentOnesKept() {
        val outbox = outbox()
        outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "старый", nowMillis = 0L)
        val recent = outbox.add(chatId = 2L, groupId = 2, maxId = 2, replyToMessageId = 0L, text = "свежий", nowMillis = 900L)
        assertEquals(1, outbox.discardExpired(nowMillis = 1_000L, ttlMillis = 500L))
        assertEquals(listOf(recent), outbox().entries())
    }

    @Test fun entriesComeBackOldestFirst() {
        val outbox = outbox()
        val newer = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "второй", nowMillis = 20L)
        val older = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "первый", nowMillis = 10L)
        assertEquals(listOf(older, newer), outbox().entries())
    }

    @Test fun damagedFileIsRemovedAndOtherRepliesStay() {
        val outbox = outbox()
        val kept = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "целый", nowMillis = 1L)
        val damaged = File(directory, "broken.reply").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(listOf(kept), outbox().entries())
        assertFalse(damaged.exists())
    }

    @Test fun fileStoredUnderAnotherNameIsRemoved() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L)
        assertTrue(File(directory, entry.id + ".reply").renameTo(File(directory, "other.reply")))
        assertTrue(outbox().entries().isEmpty())
        assertFalse(File(directory, "other.reply").exists())
    }

    @Test fun clearRemovesEveryReply() {
        val outbox = outbox()
        outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "а", nowMillis = 1L)
        outbox.add(chatId = 2L, groupId = 2, maxId = 2, replyToMessageId = 0L, text = "б", nowMillis = 2L)
        outbox.clear()
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun storedReplyLeavesOnlyItsFile() {
        outbox().add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "текст", nowMillis = 1L)
        val files = directory.listFiles().orEmpty().map { it.name }
        assertEquals(1, files.size)
        assertTrue(files.single().endsWith(".reply"))
    }

    @Test fun interruptedWriteLeftoverIsSweptOnOpen() {
        directory.mkdirs()
        val leftover = File(directory, "half.reply.tmp").apply { writeBytes(byteArrayOf(9)) }
        outbox()
        assertFalse(leftover.exists())
    }

    @Test fun oversizedReplyIsNotStored() {
        val outbox = outbox()
        try {
            outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "x".repeat(70_000), nowMillis = 1L)
            fail("A reply above the size limit must not be stored")
        } catch (expected: IOException) {
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }
}
