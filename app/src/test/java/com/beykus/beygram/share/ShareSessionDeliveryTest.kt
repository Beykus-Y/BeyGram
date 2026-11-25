package com.beykus.beygram.share

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareSessionDeliveryTest {
    private val chat1 = ShareDestination(1L, 0L, "A")
    private val chat2 = ShareDestination(2L, 0L, "B")

    private val validateAll: suspend (ShareDestination) -> Result<Unit> = { Result.success(Unit) }
    private val sendMediaUnused: suspend (Long, Long, List<OutgoingMedia>, String) -> Result<Unit> = { _, _, _, _ -> error("unused") }
    private val sendOk: suspend (Long, Long, String) -> Result<Unit> = { _, _, _ -> Result.success(Unit) }

    private fun newSession(scheduler: kotlinx.coroutines.test.TestScope): ShareSession {
        val dispatcher = StandardTestDispatcher(scheduler.testScheduler)
        return ShareSession(1L, emptyList(), "hello", mainDispatcher = dispatcher, workDispatcher = dispatcher)
    }

    @Test fun failedRecipientStaysPendingAndRetryReachesOnlyIt() = runTest {
        val session = newSession(this)
        val calls = mutableListOf<Long>()
        var failChat2 = true
        val sendText: suspend (Long, Long, String) -> Result<Unit> = { chatId, _, _ ->
            calls += chatId
            if (chatId == 2L && failChat2) Result.failure(UserMessageException(UiText.Res(R.string.share_send_failed)))
            else Result.success(Unit)
        }

        session.send(listOf(chat1, chat2), this, validateAll, sendText, sendMediaUnused)
        advanceUntilIdle()
        assertEquals(listOf(chat2), session.pendingDestinations)
        assertFalse(session.sent)
        assertNotNull(session.deliveryNote)

        failChat2 = false
        session.send(listOf(chat1, chat2), this, validateAll, sendText, sendMediaUnused)
        advanceUntilIdle()
        assertTrue(session.sent)
        assertEquals(1L, session.sentChatId)
        assertEquals(1, calls.count { it == 1L })
    }

    @Test fun unconfirmedRecipientBlocksResendUntilDropped() = runTest {
        val session = newSession(this)
        val calls = mutableListOf<Long>()
        val sendText: suspend (Long, Long, String) -> Result<Unit> = { chatId, _, _ ->
            calls += chatId
            if (chatId == 2L) Result.failure(ShareOutcomeUncertainException()) else Result.success(Unit)
        }

        session.send(listOf(chat1, chat2), this, validateAll, sendText, sendMediaUnused)
        advanceUntilIdle()
        assertTrue(session.retryBlocked)
        val callsBeforeRetry = calls.toList()

        session.send(listOf(chat1, chat2), this, validateAll, sendText, sendMediaUnused)
        advanceUntilIdle()
        assertEquals(callsBeforeRetry, calls.toList())

        assertTrue(session.dropPending(chat2, this))
        advanceUntilIdle()
        assertFalse(session.retryBlocked)
        assertTrue(session.sent)
        assertEquals(1L, session.sentChatId)
    }

    @Test fun validationFailureKeepsReasonAndProgressNote() = runTest {
        val session = newSession(this)
        val validate: suspend (ShareDestination) -> Result<Unit> = { destination ->
            if (destination.chatId == 2L) Result.failure(UserMessageException(UiText.Res(R.string.share_unavailable_recipient)))
            else Result.success(Unit)
        }

        session.send(listOf(chat1, chat2), this, validate, sendOk, sendMediaUnused)
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.share_unavailable_recipient), session.error)
        assertNotNull(session.deliveryNote)
        assertEquals(listOf(chat2), session.pendingDestinations)
    }

    @Test fun dropIsRefusedForSingleRecipientAndBeforeDelivery() = runTest {
        val fresh = newSession(this)
        assertFalse(fresh.dropPending(chat1, this))

        val single = newSession(this)
        val failing: suspend (Long, Long, String) -> Result<Unit> = { _, _, _ ->
            Result.failure(UserMessageException(UiText.Res(R.string.share_send_failed)))
        }
        single.send(listOf(chat1), this, validateAll, failing, sendMediaUnused)
        advanceUntilIdle()
        assertEquals(listOf(chat1), single.pendingDestinations)
        assertFalse(single.canDropPending)
        assertFalse(single.dropPending(chat1, this))
        assertEquals(listOf(chat1), single.pendingDestinations)
    }
}
