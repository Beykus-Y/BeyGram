package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageSendOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextSendRecoveryTest {
    private val key = TextSendScopeKey("account", 1, 2)
    private class MemoryStore : TextSendAttemptStore {
        var rows = emptyList<TextSendAttempt>()
        override fun load() = rows
        override fun save(attempts: List<TextSendAttempt>) { rows = attempts.toList() }
    }
    @Test fun pendingSnapshotsSurviveOwnerLossWithoutAutomaticResend() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore()
        val queue = TextSendQueue(backgroundScope, store)
        val gate = CompletableDeferred<Unit>()
        for (text in listOf("first", "second")) queue.enqueue(key, text, emptyList(), 10, null,
            MessageSendOptions(disableNotification = true), 0, "12:00", execute = { gate.await(); Result.success(Unit) }, formatError = { "error" })
        assertEquals(listOf("first", "second"), store.rows.map { it.text })
        val restored = TextSendQueue(backgroundScope, store)
        assertEquals(listOf("first", "second"), restored.current(key).map { it.text })
        assertTrue(restored.current(key).all { it.phase == TextSendAttemptPhase.Failed && it.replyToMessageId == 10L && it.options.disableNotification })
        assertEquals(0, restored.activeWorkerCount)
        gate.complete(Unit)
        assertEquals(0, queue.activeWorkerCount)
    }
    @Test fun doubleRetryOnlyDispatchesOneAttemptAndIdleWorkersAreReleased() = runTest(UnconfinedTestDispatcher()) {
        val queue = TextSendQueue(backgroundScope)
        val attempt = queue.enqueue(key, "retry", emptyList(), null, null, MessageSendOptions(), 0, "12:00",
            execute = { Result.failure(IllegalStateException()) }, formatError = { "error" })
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        suspend fun execute(a: TextSendAttempt): Result<Unit> { calls++; gate.await(); return Result.success(Unit) }
        assertTrue(queue.retry(key, attempt.attemptId, execute = ::execute, formatError = { "error" }))
        assertFalse(queue.retry(key, attempt.attemptId, execute = ::execute, formatError = { "error" }))
        assertEquals(1, calls)
        gate.complete(Unit)
        assertEquals(0, queue.activeWorkerCount)
        assertTrue(queue.current(key).isEmpty())
    }
    @Test fun hundredsOfCompletedChatsDoNotRetainWorkers() = runTest(UnconfinedTestDispatcher()) {
        val queue = TextSendQueue(backgroundScope)
        repeat(300) { chat ->
            queue.enqueue(key.copy(chatId = chat.toLong()), "message", emptyList(), null, null,
                MessageSendOptions(), 0, "12:00", execute = { Result.success(Unit) }, formatError = { "error" })
        }
        assertEquals(0, queue.activeWorkerCount)
    }

    @Test fun failedPersistDoesNotPublishOrDispatchNewAttempt() = runTest(UnconfinedTestDispatcher()) {
        val store = object : TextSendAttemptStore {
            override fun load() = emptyList<TextSendAttempt>()
            override fun save(attempts: List<TextSendAttempt>) { throw java.io.IOException("disk full") }
        }
        val queue = TextSendQueue(backgroundScope, store)
        var dispatched = false
        try {
            queue.enqueue(key, "draft", emptyList(), null, null, MessageSendOptions(), 0, "12:00",
                execute = { dispatched = true; Result.success(Unit) }, formatError = { "error" })
            fail("Expected queue storage failure")
        } catch (_: TextSendStorageException) {
            assertFalse(dispatched)
            assertTrue(queue.current(key).isEmpty())
            assertEquals(0, queue.activeWorkerCount)
            assertTrue(queue.storageFailed.value)
        }
    }

    @Test fun unreadableSnapshotIsNotOverwrittenByNewSend() = runTest(UnconfinedTestDispatcher()) {
        var writes = 0
        val store = object : TextSendAttemptStore {
            override fun load(): List<TextSendAttempt> = throw java.io.IOException("corrupt snapshot")
            override fun save(attempts: List<TextSendAttempt>) { writes++ }
        }
        val queue = TextSendQueue(backgroundScope, store)
        assertTrue(queue.storageFailed.value)
        try {
            queue.enqueue(key, "new draft", emptyList(), null, null, MessageSendOptions(), 0, "12:00",
                execute = { error("Must not dispatch") }, formatError = { "error" })
            fail("Expected queue storage failure")
        } catch (_: TextSendStorageException) {
            assertEquals(0, writes)
            assertTrue(queue.current(key).isEmpty())
        }
    }

    @Test fun diskFailureAfterDeliveryDoesNotResendOrStopFifo() = runTest(UnconfinedTestDispatcher()) {
        val store = object : TextSendAttemptStore {
            var rows = emptyList<TextSendAttempt>()
            var failWrites = false
            override fun load() = rows
            override fun save(attempts: List<TextSendAttempt>) {
                if (failWrites) throw java.io.IOException("disk full")
                rows = attempts.toList()
            }
        }
        val queue = TextSendQueue(backgroundScope, store)
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        for (text in listOf("first", "second")) queue.enqueue(
            key, text, emptyList(), null, null, MessageSendOptions(), 0, "12:00",
            execute = { attempt -> gate.await(); sent += attempt.text; Result.success(Unit) },
            formatError = { "error" }
        )
        store.failWrites = true
        gate.complete(Unit)
        assertEquals(listOf("first", "second"), sent)
        assertTrue(queue.current(key).isEmpty())
        assertEquals(0, queue.activeWorkerCount)
        assertTrue(queue.storageFailed.value)
        val restored = TextSendQueue(backgroundScope, store, "check history")
        assertTrue(restored.current(key).all {
            it.phase == TextSendAttemptPhase.Failed && it.errorMessage == "check history"
        })
        assertEquals(2, restored.current(key).size)
        assertEquals(0, restored.activeWorkerCount)
    }

    @Test fun snapshotCodecRejectsMalformedUnknownAndOversizedData() {
        for (raw in listOf("[", "{", "{\"version\":2,\"attempts\":[]}", "[] garbage")) {
            val result = runCatching { TextSendSnapshotCodec.decode(raw.toByteArray()) }
            assertTrue("Expected invalid snapshot to be rejected", result.isFailure)
        }
        assertTrue(runCatching {
            TextSendSnapshotCodec.decode(ByteArray(TextSendSnapshotCodec.MAX_BYTES + 1))
        }.isFailure)
    }

    @Test fun snapshotCodecSupportsLegacyAndVersionedEmptyQueues() {
        assertTrue(TextSendSnapshotCodec.decode("[]".toByteArray()).isEmpty())
        assertTrue(TextSendSnapshotCodec.decode(TextSendSnapshotCodec.encode(emptyList())).isEmpty())
    }

    @Test fun closedSessionCannotPublishLateDeliveryOrAcceptNewSends() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore()
        val worker = kotlinx.coroutines.CoroutineScope(
            backgroundScope.coroutineContext + kotlinx.coroutines.SupervisorJob()
        )
        val queue = TextSendQueue(worker, store)
        val gate = CompletableDeferred<Unit>()
        var completed = false
        queue.enqueue(key, "old session", emptyList(), null, null, MessageSendOptions(), 0, "12:00",
            execute = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
                Result.success(Unit)
            }, onSuccess = { completed = true }, formatError = { "error" })
        assertEquals(1, store.rows.size)
        queue.close()
        store.rows = emptyList() // Session cleanup has purged the durable snapshot.
        gate.complete(Unit)
        assertFalse(completed)
        assertTrue(store.rows.isEmpty())
        assertTrue(queue.current(key).isEmpty())
        try {
            queue.enqueue(key, "stale callback", emptyList(), null, null, MessageSendOptions(), 0, "12:00",
                execute = { error("Must not dispatch") }, formatError = { "error" })
            fail("Closed session accepted a send")
        } catch (_: TextSendStorageException) {
            assertTrue(store.rows.isEmpty())
        }
    }

}
