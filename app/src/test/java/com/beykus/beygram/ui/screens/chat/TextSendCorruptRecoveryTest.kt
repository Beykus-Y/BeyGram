package com.beykus.beygram.ui.screens.chat

import android.app.Application
import com.beykus.beygram.domain.model.MessageSendOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Uses real android.util.AtomicFile / org.json, so it needs Robolectric rather than the stubbed plain-JVM android.jar. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TextSendCorruptRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = TextSendScopeKey("account", 1, 2)

    @Test fun corruptSnapshotQuarantineAndResetPreservesFileAndUnblocksAccount() = runTest(UnconfinedTestDispatcher()) {
        val noBackup = folder.newFolder("no_backup")
        val snapshot = File(noBackup, PendingTextSendFiles.name("account"))
        val backup = File(snapshot.path + ".bak")
        snapshot.writeText("{corrupt-json")
        backup.writeText("[] garbage")
        val corruptDir = File(noBackup, PendingTextSendFiles.CORRUPT_QUARANTINE)
        val store = FileTextSendAttemptStore(snapshot, corruptDir)
        val dispatcher = backgroundScope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher
        val queue = TextSendQueue(backgroundScope, store, storageDispatcher = dispatcher)

        assertTrue(queue.storageCorrupt.value)
        assertTrue(queue.storageFailed.value)

        // Fail-closed protection remains intact until explicit reset.
        try {
            queue.enqueue(key, "blocked", emptyList(), null, null, MessageSendOptions(), 0, "12:00",
                execute = { error("Must not dispatch before reset") }, formatError = { "error" })
            fail("Expected storage failure before quarantine reset")
        } catch (_: TextSendStorageException) {
            assertEquals("{corrupt-json", snapshot.readText())
        }

        queue.quarantineAndReset()

        assertFalse(queue.storageCorrupt.value)
        assertFalse(queue.storageFailed.value)
        assertFalse(snapshot.exists())
        assertFalse(backup.exists())
        val quarantined = corruptDir.listFiles().orEmpty()
        assertEquals(setOf("{corrupt-json", "[] garbage"), quarantined.map { it.readText() }.toSet())

        val sent = queue.enqueue(key, "after reset", emptyList(), null, null, MessageSendOptions(), 0, "12:01",
            execute = { Result.failure(IllegalStateException("offline")) }, formatError = { "offline" })
        // A redundant reset after recovery must not drop active attempts.
        queue.quarantineAndReset()
        assertEquals(listOf(sent.attemptId), queue.current(key).map { it.attemptId })
        assertEquals(listOf("after reset"), store.load().map { it.text })
    }
}
