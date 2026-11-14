package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.LiveMessageArrival
import com.beykus.beygram.domain.model.MessageIdReplacement
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.TextStyleKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextSendQueueTest {

    private val scopeA = TextSendScopeKey(accountId = "acc-1", chatId = 100L, forumTopicId = 0L)
    private val scopeB = TextSendScopeKey(accountId = "acc-2", chatId = 100L, forumTopicId = 0L)
    private val scopeTopic = TextSendScopeKey(accountId = "acc-1", chatId = 100L, forumTopicId = 42L)

    @Test
    fun rapidConsecutiveSendsExecuteInFifoOrderWithoutMixingSnapshots() = runTest(UnconfinedTestDispatcher()) {
        val queue = TextSendQueue(backgroundScope)
        val firstGate = CompletableDeferred<Unit>()
        val executed = mutableListOf<Triple<String, Long?, MessageSendOptions>>()

        val firstEntities = listOf(MessageTextEntity(0, 5, TextStyleKind.Bold))
        val secondEntities = listOf(MessageTextEntity(0, 6, TextStyleKind.Italic))
        val firstOptions = MessageSendOptions(disableNotification = true)
        val secondOptions = MessageSendOptions(disableLinkPreview = true)

        queue.enqueue(
            scopeKey = scopeA,
            text = "First",
            entities = firstEntities,
            replyToMessageId = 10L,
            replyPreview = null,
            options = firstOptions,
            baselineSequence = 1L,
            timeLabel = "12:00",
            execute = { attempt ->
                firstGate.await()
                executed += Triple(attempt.text, attempt.replyToMessageId, attempt.options)
                Result.success(Unit)
            },
            formatError = { it.message ?: "err" }
        )
        queue.enqueue(
            scopeKey = scopeA,
            text = "Second",
            entities = secondEntities,
            replyToMessageId = 20L,
            replyPreview = null,
            options = secondOptions,
            baselineSequence = 1L,
            timeLabel = "12:00",
            execute = { attempt ->
                executed += Triple(attempt.text, attempt.replyToMessageId, attempt.options)
                Result.success(Unit)
            },
            formatError = { it.message ?: "err" }
        )

        // Both attempts are tracked while the first is still awaiting TDLib.
        assertEquals(listOf("First", "Second"), queue.current(scopeA).map { it.text })
        assertTrue(executed.isEmpty())

        firstGate.complete(Unit)

        assertEquals(
            listOf(
                Triple("First", 10L, firstOptions),
                Triple("Second", 20L, secondOptions)
            ),
            executed
        )
        assertTrue(queue.current(scopeA).isEmpty())
    }

    @Test
    fun attemptsAreIsolatedByAccountChatAndTopic() = runTest(UnconfinedTestDispatcher()) {
        val queue = TextSendQueue(backgroundScope)
        val gate = CompletableDeferred<Unit>()

        queue.enqueue(
            scopeKey = scopeA,
            text = "Account 1",
            entities = emptyList(),
            replyToMessageId = null,
            replyPreview = null,
            options = MessageSendOptions(),
            baselineSequence = 0L,
            timeLabel = "12:00",
            execute = {
                gate.await()
                Result.success(Unit)
            },
            formatError = { "err" }
        )

        assertEquals(1, queue.current(scopeA).size)
        assertTrue(queue.current(scopeB).isEmpty())
        assertTrue(queue.current(scopeTopic).isEmpty())

        gate.complete(Unit)
        assertTrue(queue.current(scopeA).isEmpty())
    }

    @Test
    fun failedAttemptStaysVisibleWithReasonAndCanRetryOrDiscard() = runTest(UnconfinedTestDispatcher()) {
        val queue = TextSendQueue(backgroundScope)
        var shouldFail = true

        val attempt = queue.enqueue(
            scopeKey = scopeA,
            text = "Will fail once",
            entities = emptyList(),
            replyToMessageId = 55L,
            replyPreview = null,
            options = MessageSendOptions(),
            baselineSequence = 0L,
            timeLabel = "12:01",
            execute = {
                if (shouldFail) Result.failure(IllegalStateException("Network timeout"))
                else Result.success(Unit)
            },
            formatError = { it.message ?: "Send failed" }
        )

        val failedList = queue.current(scopeA)
        assertEquals(1, failedList.size)
        assertEquals(TextSendAttemptPhase.Failed, failedList.single().phase)
        assertEquals("Network timeout", failedList.single().errorMessage)
        assertEquals(MessageSendState.Failed, failedList.single().toChatMessage().sendState)
        assertEquals("Network timeout", failedList.single().toChatMessage().sendError)

        // Retry succeeds in-place and clears the attempt.
        shouldFail = false
        assertTrue(
            queue.retry(
                scopeKey = scopeA,
                attemptId = attempt.attemptId,
                execute = { Result.success(Unit) },
                formatError = { "err" }
            )
        )
        assertTrue(queue.current(scopeA).isEmpty())

        // Discarding a failed attempt removes it cleanly.
        val discarded = queue.enqueue(
            scopeKey = scopeA,
            text = "Discard me",
            entities = emptyList(),
            replyToMessageId = null,
            replyPreview = null,
            options = MessageSendOptions(),
            baselineSequence = 0L,
            timeLabel = "12:02",
            execute = { Result.failure(IllegalStateException("Blocked")) },
            formatError = { it.message ?: "err" }
        )
        assertEquals(1, queue.current(scopeA).size)
        queue.discard(scopeA, discarded.attemptId)
        assertTrue(queue.current(scopeA).isEmpty())
    }

    @Test
    fun visiblePendingTextAttemptsSuppressesDuplicatesAfterHistoryArrival() {
        val first = TextSendAttempt(
            attemptId = 1L,
            scope = scopeA,
            text = "Same",
            entities = emptyList(),
            replyToMessageId = null,
            replyPreview = null,
            options = MessageSendOptions(),
            baselineSequence = 1L,
            timeLabel = "12:00",
            phase = TextSendAttemptPhase.Sending
        )
        val second = first.copy(attemptId = 2L)
        val failed = first.copy(attemptId = 3L, text = "Failed", phase = TextSendAttemptPhase.Failed, errorMessage = "Oops")

        val historyWithOneArrival = ChatHistory(
            messages = listOf(
                ChatMessage(
                    id = 900L,
                    chatId = 100L,
                    text = "Same",
                    time = "12:00",
                    isOutgoing = true,
                    sendState = MessageSendState.Sent
                )
            ),
            liveArrivals = listOf(LiveMessageArrival(sequence = 2L, messageId = 10L)),
            liveArrivalSequence = 2L,
            recentReplacements = listOf(MessageIdReplacement(oldId = 10L, newId = 900L))
        )

        val visible = visiblePendingTextAttempts(listOf(first, second, failed), historyWithOneArrival)
        // `first` is claimed by message 900L; `second` and `failed` remain visible in newest-first order.
        assertEquals(listOf(3L, 2L), visible.map { it.attemptId })
        assertFalse(visible.any { it.attemptId == 1L })
    }
}
