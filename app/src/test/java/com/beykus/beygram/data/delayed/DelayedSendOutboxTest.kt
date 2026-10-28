package com.beykus.beygram.data.delayed

import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DelayedSendOutboxTest {
    @get:Rule val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "delayed-send")
    private val accountId = "account-1"

    private fun outbox() = DelayedSendOutbox(directory, accountId)

    private fun entry(
        id: String,
        text: String = "текст",
        createdAtMillis: Long = 1L,
        status: DelayedSendStatus = DelayedSendStatus.Queued,
        entities: List<MessageTextEntity> = emptyList()
    ) = DelayedSendEntry(
        id = id,
        accountId = accountId,
        chatId = -1001234L,
        topicId = 77L,
        replyToMessageId = 99L,
        text = text,
        entities = entities,
        disableNotification = true,
        createdAtMillis = createdAtMillis,
        notBeforeMillis = createdAtMillis + 5_000L,
        status = status
    )

    @Test fun newOutboxIsEmpty() {
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun cyrillicTextEntitiesAndEmojiSurviveReopen() {
        val text = "Привет, мир! 👋 код"
        val entities = listOf(
            MessageTextEntity(text.indexOf("Привет"), "Привет".length, TextStyleKind.Bold),
            MessageTextEntity(text.indexOf("мир"), "мир".length, TextStyleKind.Url, "https://example.com/путь"),
            MessageTextEntity(text.indexOf("👋"), "👋".length, TextStyleKind.CustomEmoji, "5368324170671202286"),
            MessageTextEntity(text.indexOf("код"), "код".length, TextStyleKind.Code)
        )
        val saved = entry("first", text = text, entities = entities)
        outbox().save(saved)
        assertEquals(listOf(saved), outbox().entries())
    }

    @Test fun entriesComeBackOldestFirst() {
        val outbox = outbox()
        val newer = entry("newer", createdAtMillis = 20L)
        val older = entry("older", createdAtMillis = 10L)
        outbox.save(newer)
        outbox.save(older)
        assertEquals(listOf(older, newer), outbox().entries())
    }

    @Test fun savingAgainReplacesTheSameMessage() {
        val outbox = outbox()
        val queued = entry("one")
        outbox.save(queued)
        val dispatching = queued.copy(status = DelayedSendStatus.Dispatching)
        outbox.save(dispatching)
        assertEquals(listOf(dispatching), outbox().entries())
        assertEquals(listOf("one.delayed"), directory.listFiles().orEmpty().map { it.name })
    }

    @Test fun removeDeletesOnlyTheSentMessage() {
        val outbox = outbox()
        val sent = entry("sent", createdAtMillis = 1L)
        val pending = entry("pending", createdAtMillis = 2L)
        outbox.save(sent)
        outbox.save(pending)
        assertTrue(outbox.remove(sent.id))
        assertEquals(listOf(pending), outbox().entries())
    }

    @Test fun expiredMessagesAreDiscardedAndRecentOnesKept() {
        val outbox = outbox()
        outbox.save(entry("old", createdAtMillis = 0L))
        val onTheLimit = entry("on-the-limit", createdAtMillis = 500L)
        outbox.save(onTheLimit)
        val recent = entry("recent", createdAtMillis = 900L)
        outbox.save(recent)
        assertEquals(1, outbox.discardExpired(nowMillis = 1_000L, ttlMillis = 500L))
        assertEquals(listOf(onTheLimit, recent), outbox().entries())
    }

    @Test fun damagedFileIsRemovedAndOtherMessagesStay() {
        val outbox = outbox()
        val kept = entry("kept")
        outbox.save(kept)
        val damaged = File(directory, "broken.delayed").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(listOf(kept), outbox().entries())
        assertFalse(damaged.exists())
    }

    @Test fun fileStoredUnderAnotherNameIsRemoved() {
        val outbox = outbox()
        outbox.save(entry("moved"))
        assertTrue(File(directory, "moved.delayed").renameTo(File(directory, "other.delayed")))
        assertTrue(outbox().entries().isEmpty())
        assertFalse(File(directory, "other.delayed").exists())
    }

    @Test fun messageOfAnotherAccountIsRefused() {
        val foreign = entry("foreign").copy(accountId = "account-2")
        try {
            outbox().save(foreign)
            fail("A message of another account must not be stored in this outbox")
        } catch (expected: IllegalArgumentException) {
            assertTrue(outbox().entries().isEmpty())
        }
    }

    @Test fun oversizedTextIsNotStored() {
        val outbox = outbox()
        try {
            outbox.save(entry("big", text = "x".repeat(70_000)))
            fail("A message above the size limit must not be stored")
        } catch (expected: IOException) {
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun clearRemovesEveryMessage() {
        val outbox = outbox()
        outbox.save(entry("a", createdAtMillis = 1L))
        outbox.save(entry("b", createdAtMillis = 2L))
        assertEquals(0, outbox.clear())
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun clearReportsFilesItCannotDelete() {
        val outbox = outbox()
        outbox.save(entry("deletable"))
        // A directory with a file inside cannot be deleted, so clear() reports it and leaves it in place.
        val stuck = File(directory, "stuck.delayed").apply { mkdirs(); File(this, "inside").writeBytes(byteArrayOf(1)) }
        assertEquals(1, outbox.clear())
        assertTrue(stuck.exists())
        assertTrue(outbox().entries().isEmpty())
    }

    @Test fun interruptedWriteLeftoverIsSweptOnOpenAndRealMessagesStay() {
        val kept = entry("kept")
        outbox().save(kept)
        val leftover = File(directory, "half.delayed.tmp").apply { writeBytes(byteArrayOf(9)) }
        assertEquals(listOf(kept), outbox().entries())
        assertFalse(leftover.exists())
    }
}
