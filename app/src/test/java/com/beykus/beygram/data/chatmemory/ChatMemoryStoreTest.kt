package com.beykus.beygram.data.chatmemory

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatMemoryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var store: ChatMemoryStore

    @Before
    fun open() {
        val context = RuntimeEnvironment.getApplication()
        store = ChatMemoryStore(ChatMemoryDatabase(context, File(folder.root, "memory.db").path))
    }

    @After
    fun close() {
        store.close()
    }

    @Test
    fun aClosedStoreDoesNotCreateItsDatabaseAgain() {
        // Deleting an account's files closes its store first. A call that arrives later must be refused, and must not create
        // the database file again.
        store.observe(listOf(observation(1, "hello")), nowMillis = 100)
        val database = File(folder.root, "memory.db")
        assertTrue(database.exists())
        store.close()
        database.delete()

        var refused = false
        try {
            store.usage()
        } catch (expected: IllegalStateException) {
            refused = true
        }
        assertTrue(refused)
        assertFalse(database.exists())
    }

    private fun observation(
        messageId: Long,
        text: String,
        chatId: Long = CHAT,
        attachment: Boolean = false,
        outgoing: Boolean = false
    ) = MemoryObservation(
        chatId = chatId,
        messageId = messageId,
        isOutgoing = outgoing,
        dateSeconds = 1_700_000_000L + messageId,
        senderId = 7L,
        senderName = "Anna",
        snapshot = MemorySnapshot(text, attachment)
    )

    @Test
    fun trimRemovesOnlyWhatTheLimitNeeds() {
        // One message with a long edit history, then many short deleted messages. A little space must not cost the
        // deleted messages, which are the cheapest part of the store.
        val longText = "x".repeat(4000)
        store.observe(listOf(observation(1, longText)), nowMillis = 1)
        for (edit in 1..100) {
            store.observe(listOf(observation(1, longText + edit)), nowMillis = 1L + edit)
        }
        val deletedIds = (100L until 300L).toList()
        store.observe(deletedIds.map { observation(it, "m") }, nowMillis = 1_000)
        store.markDeleted(CHAT, deletedIds, fallback = emptyMap(), nowMillis = 2_000)
        val before = store.usage().bytes

        val removed = store.trim(limitBytes = before - 60_000L)

        assertEquals(1, removed)
        assertEquals(200, store.usage().deletedMessages)
        assertTrue(store.usage().bytes <= before - 60_000L)
    }

    @Test
    fun newMessageIsKeptWithoutVersions() {
        store.observe(listOf(observation(1, "hello")), nowMillis = 100)

        assertEquals(1, store.usage().messages)
        assertEquals(0, store.usage().deletedMessages)
        assertTrue(store.versions(CHAT, 1).isEmpty())
    }

    @Test
    fun editKeepsTheEarlierText() {
        store.observe(listOf(observation(1, "hello")), nowMillis = 100)

        val archived = store.observe(listOf(observation(1, "hello there")), nowMillis = 200)

        assertEquals(mapOf((CHAT to 1L) to 1), archived)
        assertEquals(listOf(MemorizedVersion("hello", false, 200)), store.versions(CHAT, 1))
        assertEquals(mapOf(1L to 1), store.versionCounts(CHAT, listOf(1L, 2L)))
    }

    @Test
    fun sameTextIsNotAVersion() {
        store.observe(listOf(observation(1, "hello")), nowMillis = 100)

        val archived = store.observe(listOf(observation(1, "hello")), nowMillis = 200)

        assertTrue(archived.isEmpty())
        assertTrue(store.versions(CHAT, 1).isEmpty())
    }

    @Test
    fun deletedMessageKeepsItsLastText() {
        store.observe(listOf(observation(5, "first"), observation(7, "second", attachment = true)), nowMillis = 100)

        store.markDeleted(CHAT, listOf(7L), fallback = emptyMap(), nowMillis = 300)

        val deleted = store.deletedMessages(CHAT, 1L, Long.MAX_VALUE)
        assertEquals(1, deleted.size)
        assertEquals(7L, deleted.single().messageId)
        assertEquals("second", deleted.single().text)
        assertTrue(deleted.single().hasAttachment)
        assertEquals(300L, deleted.single().deletedAtMillis)
        assertEquals("Anna", deleted.single().senderName)
        assertEquals(1, store.usage().deletedMessages)
    }

    @Test
    fun deletedMessagesFollowTheRequestedRange() {
        store.observe(listOf(observation(3, "a"), observation(9, "b")), nowMillis = 100)
        store.markDeleted(CHAT, listOf(3L, 9L), fallback = emptyMap(), nowMillis = 200)

        assertEquals(listOf(3L), store.deletedMessages(CHAT, 1L, 5L).map { it.messageId })
        assertTrue(store.deletedMessages(CHAT, 10L, 20L).isEmpty())
        assertTrue(store.deletedMessages(CHAT, 9L, 3L).isEmpty())
    }

    @Test
    fun deletionKeepsAMessageThatOnlyTheScreenKnows() {
        store.markDeleted(CHAT, listOf(42L), fallback = mapOf(42L to observation(42, "from screen")), nowMillis = 500)

        assertEquals("from screen", store.deletedMessages(CHAT, 1L, Long.MAX_VALUE).single().text)
    }

    @Test
    fun deletionWithoutAnyTextKeepsNothing() {
        store.markDeleted(CHAT, listOf(42L), fallback = emptyMap(), nowMillis = 500)

        assertTrue(store.deletedMessages(CHAT, 1L, Long.MAX_VALUE).isEmpty())
        assertEquals(0, store.usage().messages)
    }

    @Test
    fun editAfterDeletionDoesNotChangeTheKeptText() {
        store.observe(listOf(observation(4, "before")), nowMillis = 100)
        store.markDeleted(CHAT, listOf(4L), fallback = emptyMap(), nowMillis = 200)

        val archived = store.observe(listOf(observation(4, "after")), nowMillis = 300)

        assertTrue(archived.isEmpty())
        assertEquals("before", store.deletedMessages(CHAT, 1L, Long.MAX_VALUE).single().text)
    }

    @Test
    fun editOfAnUnknownMessageIsNotStoredWhenInsertIsOff() {
        store.observe(listOf(observation(8, "new text")), nowMillis = 100, insertMissing = false)

        assertEquals(0, store.usage().messages)
    }

    @Test
    fun trimRemovesUndeletedMessagesFirstAndKeepsDeletedOnes() {
        val longText = "x".repeat(1000)
        for (id in 1L..10L) {
            store.observe(listOf(observation(id, longText)), nowMillis = 100 + id)
        }
        store.markDeleted(CHAT, listOf(1L), fallback = emptyMap(), nowMillis = 200)
        val limit = 5_000L

        val removed = store.trim(limit)

        assertTrue(removed > 0)
        assertTrue(store.usage().bytes <= limit)
        assertEquals(listOf(1L), store.deletedMessages(CHAT, 1L, Long.MAX_VALUE).map { it.messageId })
        // The oldest undeleted message went first, and the newest one is still there: it still records an edit.
        assertTrue(store.observe(listOf(observation(2, "gone")), nowMillis = 300, insertMissing = false).isEmpty())
        assertEquals(mapOf((CHAT to 10L) to 1), store.observe(listOf(observation(10, "edited")), nowMillis = 301, insertMissing = false))
    }

    @Test
    fun clearAllRemovesEverything() {
        store.observe(listOf(observation(1, "a")), nowMillis = 1)
        store.observe(listOf(observation(1, "b")), nowMillis = 2)
        store.markDeleted(CHAT, listOf(1L), fallback = emptyMap(), nowMillis = 3)

        store.clearAll()

        val usage = store.usage()
        assertEquals(0, usage.messages)
        assertEquals(0, usage.versions)
        assertEquals(0L, usage.bytes)
        assertTrue(store.versions(CHAT, 1).isEmpty())
    }

    @Test
    fun chatsAreKeptApart() {
        store.observe(listOf(observation(1, "a", chatId = 10L), observation(1, "b", chatId = 20L)), nowMillis = 1)
        store.markDeleted(20L, listOf(1L), fallback = emptyMap(), nowMillis = 2)

        assertTrue(store.deletedMessages(10L, 1L, 1L).isEmpty())
        assertEquals("b", store.deletedMessages(20L, 1L, 1L).single().text)
    }

    @Test
    fun outgoingMessagesAreNotKeptWhenDeleted() {
        store.observe(listOf(observation(3, "mine", outgoing = true)), nowMillis = 100)

        store.markDeleted(CHAT, listOf(3L), fallback = mapOf(3L to observation(3, "mine", outgoing = true)), nowMillis = 200)

        assertTrue(store.deletedMessages(CHAT, 1L, Long.MAX_VALUE).isEmpty())
        assertEquals(1, store.usage().messages)
    }

    @Test
    fun aChangeWithoutASenderNameKeepsTheStoredName() {
        store.observe(listOf(observation(6, "one")), nowMillis = 100)
        val change = MemoryObservation(CHAT, 6L, false, 0L, 0L, "", MemorySnapshot("two", false))
        store.observe(listOf(change), nowMillis = 200, insertMissing = false)

        store.markDeleted(CHAT, listOf(6L), fallback = emptyMap(), nowMillis = 300)

        val kept = store.deletedMessages(CHAT, 1L, Long.MAX_VALUE).single()
        assertEquals("Anna", kept.senderName)
        assertEquals("two", kept.text)
    }

    private companion object {
        const val CHAT = 100L
    }
}
