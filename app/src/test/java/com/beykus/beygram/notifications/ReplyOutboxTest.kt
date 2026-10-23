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
    private val session = "session-1"

    private fun outbox() = ReplyOutbox(directory)

    @Test fun secondaryOutboxesNeverShareNumericChatOrMessageIds() {
        val a = ReplyOutbox(File(folder.root, "secondary/a-first"))
        val b = ReplyOutbox(File(folder.root, "secondary/a-second"))
        val entryA = a.add(42L, 5, 99, 123L, "from A", 1L, "session-a")
        val entryB = b.add(42L, 5, 99, 123L, "from B", 1L, "session-b")
        assertEquals(listOf(entryA), a.entries())
        assertEquals(listOf(entryB), b.entries())
        assertEquals(0, a.clear())
        assertTrue(a.entries().isEmpty())
        assertEquals(listOf(entryB), b.entries())
    }

    @Test fun cyrillicReplyWithNewlineSurvivesReopen() {
        val added = outbox().add(chatId = -1001234L, groupId = 7, maxId = 42, replyToMessageId = 99L, text = "Привет!\nВторая строка", nowMillis = 1_000L, session = session)
        assertEquals(listOf(added), outbox().entries())
    }

    @Test fun forumTopicSurvivesReopen() {
        val added = outbox().add(chatId = -1001L, groupId = 1, maxId = 1, replyToMessageId = 5L, text = "в тему", nowMillis = 1L, session = session, topicId = 77L)
        assertEquals(77L, outbox().entries().single().topicId)
        assertEquals(added.id, outbox().entries().single().id)
    }

    @Test fun replyStoredBeforeTopicsReadsWithoutTopic() {
        directory.mkdirs()
        val id = "legacy-reply"
        java.io.DataOutputStream(File(directory, "$id.reply").outputStream().buffered()).use { output ->
            val text = "старый".toByteArray(Charsets.UTF_8)
            output.writeInt(2)
            output.writeUTF(id)
            output.writeUTF(session)
            output.writeLong(-1001L)
            output.writeInt(1)
            output.writeInt(1)
            output.writeLong(5L)
            output.writeLong(1L)
            output.writeInt(0)
            output.writeInt(text.size)
            output.write(text)
        }
        val entry = outbox().entries().single()
        assertEquals(0L, entry.topicId)
        assertEquals("старый", entry.text)
    }

    @Test fun removeDeletesOnlyTheSentReply() {
        val outbox = outbox()
        val sent = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "первый", nowMillis = 1L, session = session)
        val pending = outbox.add(chatId = 2L, groupId = 2, maxId = 2, replyToMessageId = 0L, text = "второй", nowMillis = 2L, session = session)
        assertTrue(outbox.remove(sent.id))
        assertEquals(listOf(pending), outbox().entries())
    }

    @Test fun failedAttemptsAreCountedUntilTheReplyIsDropped() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L, session = session)
        assertFalse(outbox.recordFailure(entry, maxAttempts = 3))
        assertFalse(outbox.recordFailure(outbox().entries().single(), maxAttempts = 3))
        assertEquals(2, outbox().entries().single().attempts)
        assertTrue(outbox.recordFailure(outbox().entries().single(), maxAttempts = 3))
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun dropIsReportedOnlyAfterTheReplyIsRemoved() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L, session = session)
        // A directory with a file inside cannot be removed, so the last attempt does not count as a drop.
        File(directory, entry.id + ".reply").apply { delete(); mkdirs(); File(this, "inside").writeBytes(byteArrayOf(1)) }
        assertFalse(outbox.recordFailure(entry, maxAttempts = 1))
        assertTrue(File(directory, entry.id + ".reply").exists())
    }

    @Test fun failureDoesNotWriteBackAClearedReply() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L, session = session)
        outbox.clear()
        assertTrue(outbox.recordFailure(entry, maxAttempts = 3))
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun expiredRepliesAreDiscardedAndRecentOnesKept() {
        val outbox = outbox()
        outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "старый", nowMillis = 0L, session = session)
        val recent = outbox.add(chatId = 2L, groupId = 2, maxId = 2, replyToMessageId = 0L, text = "свежий", nowMillis = 900L, session = session)
        assertEquals(1, outbox.discardExpired(nowMillis = 1_000L, ttlMillis = 500L))
        assertEquals(listOf(recent), outbox().entries())
    }

    @Test fun entriesComeBackOldestFirst() {
        val outbox = outbox()
        val newer = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "второй", nowMillis = 20L, session = session)
        val older = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "первый", nowMillis = 10L, session = session)
        assertEquals(listOf(older, newer), outbox().entries())
    }

    @Test fun damagedFileIsRemovedAndOtherRepliesStay() {
        val outbox = outbox()
        val kept = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "целый", nowMillis = 1L, session = session)
        val damaged = File(directory, "broken.reply").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(listOf(kept), outbox().entries())
        assertFalse(damaged.exists())
    }

    @Test fun fileStoredUnderAnotherNameIsRemoved() {
        val outbox = outbox()
        val entry = outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "ответ", nowMillis = 1L, session = session)
        assertTrue(File(directory, entry.id + ".reply").renameTo(File(directory, "other.reply")))
        assertTrue(outbox().entries().isEmpty())
        assertFalse(File(directory, "other.reply").exists())
    }

    @Test fun clearRemovesEveryReply() {
        val outbox = outbox()
        outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "а", nowMillis = 1L, session = session)
        outbox.add(chatId = 2L, groupId = 2, maxId = 2, replyToMessageId = 0L, text = "б", nowMillis = 2L, session = session)
        assertEquals(0, outbox.clear())
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun clearReportsRepliesItCannotDelete() {
        val outbox = outbox()
        outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "удалится", nowMillis = 1L, session = session)
        // A directory with a file inside cannot be deleted, so clear() reports it and leaves it in place.
        val stuck = File(directory, "stuck.reply").apply { mkdirs(); File(this, "inside").writeBytes(byteArrayOf(1)) }
        assertEquals(1, outbox.clear())
        assertTrue(stuck.exists())
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun storedReplyLeavesOnlyItsFile() {
        outbox().add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "текст", nowMillis = 1L, session = session)
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
            outbox.add(chatId = 1L, groupId = 1, maxId = 1, replyToMessageId = 0L, text = "x".repeat(70_000), nowMillis = 1L, session = session)
            fail("A reply above the size limit must not be stored")
        } catch (expected: IOException) {
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }
}
