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

}
