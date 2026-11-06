package com.beykus.beygram.data.chatmemory

import com.beykus.beygram.data.settings.ChatMemorySettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatMemoryRecorderLifecycleTest {
    private val context = RuntimeEnvironment.getApplication()

    // A new account for every test, so that no database or preference is shared between tests.
    private val accountId = "lifecycle-${UUID.randomUUID()}"
    private val settings = ChatMemorySettingsStore.forAccount(context, accountId)

    // The writer is a thread the test controls. A task that waits on the gate keeps every later write queued, as a long trim does.
    private val gate = CountDownLatch(1)
    private val writer: ExecutorService = Executors.newSingleThreadExecutor()
    private val recorder = ChatMemoryRecorder(
        context = context,
        accountId = accountId,
        settings = settings,
        factsOf = { ChatMemoryChatFacts(isSecret = false, hasProtectedContent = false, hasAutoDeleteTimer = false) },
        onVersionsKept = {},
        writer = writer
    )
    private val databaseFile = File(context.noBackupFilesDir, "chat-memory/$accountId.db")

    @After
    fun tearDown() {
        gate.countDown()
        writer.shutdownNow()
        ChatMemoryRecorder.stopAccount(accountId, waitMillis = 1_000L)
        runCatching { ChatMemoryStore.deleteAccountFiles(context, accountId) }
        ChatMemorySettingsStore.releaseAccount(accountId)
    }

    @Test
    fun aWriteQueuedBeforeTheAccountIsRemovedIsStoredBeforeItsFilesGo() {
        settings.update { it.copy(enabled = true) }
        writer.execute { gate.await() }
        recorder.recordSeen(listOf(observation(1L)))

        // The writer is still busy, so the account cannot be removed yet, and the check does not wait for it.
        assertFalse(ChatMemoryRecorder.stopAccount(accountId, waitMillis = 0L))
        gate.countDown()
        assertTrue(ChatMemoryRecorder.stopAccount(accountId, waitMillis = 5_000L))
        ChatMemoryStore.deleteAccountFiles(context, accountId)

        // Nothing is left on disk, and a write that comes after the removal does not open the database again.
        listOf("", "-wal", "-shm").forEach { suffix -> assertFalse(File(databaseFile.path + suffix).exists()) }
        recorder.recordSeen(listOf(observation(2L)))
        assertEquals(0, recorder.usage().messages)
        assertFalse(ChatMemoryStore.hasDatabase(context, accountId))
    }

    @Test
    fun aFileThatCannotBeRemovedFailsTheDeletionInsteadOfBeingIgnored() {
        // A non-empty directory cannot be removed by File.delete(), so the journal of the account stays behind.
        val stuck = File(databaseFile.path + "-journal")
        stuck.mkdirs()
        File(stuck, "held").writeText("kept")
        try {
            val failure = runCatching { ChatMemoryStore.deleteAccountFiles(context, accountId) }.exceptionOrNull()

            // The caller keeps its tombstone when this throws, so the cleanup runs again instead of reporting success.
            assertTrue(failure is IOException)
            assertTrue(stuck.exists())
        } finally {
            stuck.deleteRecursively()
        }
    }

    @Test
    fun aDeletionIsQueuedAndDoesNotWaitForTheWritesBeforeIt() {
        settings.update { it.copy(enabled = true) }
        writer.execute { gate.await() }
        recorder.recordSeen(listOf(observation(1L)))

        // The caller that reports the deletion returns while the writer is still busy. The TDLib thread must not wait here.
        val caller = Executors.newSingleThreadExecutor()
        try {
            caller.submit(Runnable { recorder.markDeleted(CHAT, listOf(1L), fallback = emptyMap()) }).get(2, TimeUnit.SECONDS)
        } finally {
            caller.shutdownNow()
            gate.countDown()
        }

        // The deletion is applied after the write it follows: once the queue is stored, the message reads as deleted.
        recorder.awaitWrites()
        assertEquals(listOf(1L), recorder.deletedBetween(CHAT, 1L, 1L).map { it.messageId })
    }

    @Test
    fun aMessageTheUserDeletedIsDroppedButADeletionTheOtherSideMadeStaysKept() {
        settings.update { it.copy(enabled = true) }
        recorder.recordSeen(listOf(observation(1L), observation(2L), observation(3L)))
        recorder.markDeleted(CHAT, listOf(2L), fallback = emptyMap())
        recorder.forget(CHAT, listOf(1L, 2L))
        recorder.awaitWrites()

        assertEquals(listOf(2L), recorder.deletedBetween(CHAT, 1L, 3L).map { it.messageId })
        assertEquals(2, recorder.usage().messages)
    }

    private fun observation(messageId: Long) = MemoryObservation(
        chatId = CHAT,
        messageId = messageId,
        isOutgoing = false,
        dateSeconds = 1_700_000_000L,
        senderId = 7L,
        senderName = "Anna",
        snapshot = MemorySnapshot("hello", hasAttachment = false)
    )

    private companion object {
        const val CHAT = 42L
    }
}
