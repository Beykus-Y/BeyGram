package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.PaidReactionUncertainException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdPaidReactionsTest {
    /** A fake TDLib that records each request in order and runs the scripted step for add, commit and remove. */
    private class FakeTdlib(
        private val add: suspend () -> Unit = {},
        private val commit: suspend () -> Unit = {},
        private val remove: suspend () -> Unit = {}
    ) {
        val log = mutableListOf<String>()
        val added = mutableListOf<TdApi.AddPendingPaidMessageReaction>()
        val sender = TdPaidReactions { request(it) }

        private suspend fun request(function: TdApi.Function<TdApi.Ok>): TdApi.Ok {
            when (function) {
                is TdApi.AddPendingPaidMessageReaction -> {
                    log += "add"
                    added += function
                    add()
                }
                is TdApi.CommitPendingPaidMessageReactions -> {
                    log += "commit"
                    commit()
                }
                is TdApi.RemovePendingPaidMessageReactions -> {
                    log += "remove"
                    remove()
                }
                else -> error("unexpected request $function")
            }
            return TdApi.Ok()
        }
    }

    private fun refused(code: Int, message: String): Nothing = throw TdRequestError(code, message)

    @Test fun `a send adds the public reaction, commits it once, and removes nothing`() = runBlocking {
        val tdlib = FakeTdlib()
        assertTrue(tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false).isSuccess)
        assertEquals(listOf("add", "commit"), tdlib.log)
        val added = tdlib.added.single()
        assertEquals(5L, added.chatId)
        assertEquals(9L, added.messageId)
        assertEquals(10L, added.starCount)
        assertTrue(added.type is TdApi.PaidReactionTypeRegular)
    }

    @Test fun `an anonymous send asks TDLib for the anonymous type`() = runBlocking {
        val tdlib = FakeTdlib()
        assertTrue(tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 1L, isAnonymous = true).isSuccess)
        assertTrue(tdlib.added.single().type is TdApi.PaidReactionTypeAnonymous)
    }

    @Test fun `a zero amount is refused before any request is made`() = runBlocking {
        val tdlib = FakeTdlib()
        val failure = tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 0L, isAnonymous = false).exceptionOrNull()
        assertEquals(UiText.Res(R.string.err_paid_reaction_amount), (failure as UserMessageException).text)
        assertEquals(emptyList<String>(), tdlib.log)
    }

    @Test fun `a refused add is removed and never committed`() = runBlocking {
        val tdlib = FakeTdlib(add = { refused(400, "CHAT_WRITE_FORBIDDEN") })
        val failure = tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false).exceptionOrNull()
        assertEquals(listOf("add", "remove"), tdlib.log)
        assertEquals(UiText.Res(R.string.err_paid_reaction_unavailable), (failure as UserMessageException).text)
        assertFalse(failure is PaidReactionUncertainException)
    }

    @Test fun `a commit refused for low balance is removed and explained`() = runBlocking {
        val tdlib = FakeTdlib(commit = { refused(400, "BALANCE_TOO_LOW") })
        val failure = tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false).exceptionOrNull()
        assertEquals(listOf("add", "commit", "remove"), tdlib.log)
        assertEquals(UiText.Res(R.string.err_paid_reaction_balance_low), (failure as UserMessageException).text)
        assertFalse(failure is PaidReactionUncertainException)
    }

    @Test fun `a commit that loses its connection may have spent the Stars, so it is uncertain`() = runBlocking {
        val tdlib = FakeTdlib(commit = { throw IllegalStateException("connection closed") })
        val failure = tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false).exceptionOrNull()
        assertTrue(failure is PaidReactionUncertainException)
        assertEquals(UiText.Res(R.string.err_paid_reaction_uncertain), (failure as UserMessageException).text)
        assertEquals(listOf("add", "commit", "remove"), tdlib.log)
    }

    @Test fun `a commit that times out is uncertain too`() = runBlocking {
        val tdlib = FakeTdlib(commit = { withTimeout(1) { awaitCancellation() } })
        val failure = tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false).exceptionOrNull()
        assertTrue(failure is PaidReactionUncertainException)
        assertEquals(listOf("add", "commit", "remove"), tdlib.log)
    }

    @Test fun `a second send while one is in flight is refused as busy`() = runBlocking {
        val addStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val tdlib = FakeTdlib(add = {
            addStarted.complete(Unit)
            release.await()
        })
        val first = async { tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false) }
        addStarted.await()
        val second = tdlib.sender.send(chatId = 5L, messageId = 9L, starCount = 10L, isAnonymous = false)
        assertEquals(UiText.Res(R.string.err_paid_reaction_busy), (second.exceptionOrNull() as UserMessageException).text)
        release.complete(Unit)
        assertTrue(first.await().isSuccess)
        assertEquals(listOf("add", "commit"), tdlib.log)
    }

    @Test fun `TDLib refusals name their reason, and an unknown failure stays generic`() {
        assertEquals(UiText.Res(R.string.err_tdlib_flood_wait), paidReactionErrorText(TdRequestError(429, "Too Many Requests")))
        assertEquals(UiText.Res(R.string.err_tdlib_flood_wait), paidReactionErrorText(TdRequestError(400, "FLOOD_WAIT_30")))
        assertEquals(UiText.Res(R.string.err_tdlib_message_unavailable), paidReactionErrorText(TdRequestError(404, "Not Found")))
        assertEquals(UiText.Res(R.string.err_paid_reaction_unavailable), paidReactionErrorText(TdRequestError(400, "REACTION_INVALID")))
        assertEquals(UiText.Res(R.string.err_paid_reaction_failed), paidReactionErrorText(IllegalStateException("something new")))
    }

    @Test fun `a failed add is rolled back and its failure is rethrown`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = runCatching {
            sendPendingPaidReaction(
                add = {
                    events += "add"
                    error("refused")
                },
                commit = { events += "commit" },
                rollback = { events += "rollback" }
            )
        }.exceptionOrNull()
        assertEquals("refused", failure?.message)
        assertEquals(listOf("add", "rollback"), events)
    }

    @Test fun `a commit cancelled midway is still rolled back`() = runBlocking {
        val events = mutableListOf<String>()
        val commitStarted = CompletableDeferred<Unit>()
        val job = launch {
            sendPendingPaidReaction(
                add = { events += "add" },
                commit = {
                    events += "commit"
                    commitStarted.complete(Unit)
                    awaitCancellation()
                },
                rollback = { events += "rollback" }
            )
        }
        commitStarted.await()
        job.cancelAndJoin()
        assertEquals(listOf("add", "commit", "rollback"), events)
    }

    @Test fun `a failed rollback is kept as suppressed on the original failure`() = runBlocking {
        val failure = runCatching {
            sendPendingPaidReaction(
                add = { error("add refused") },
                commit = {},
                rollback = { error("remove failed") }
            )
        }.exceptionOrNull()
        assertEquals("add refused", failure?.message)
        assertEquals("remove failed", failure?.suppressed?.single()?.message)
    }
}
