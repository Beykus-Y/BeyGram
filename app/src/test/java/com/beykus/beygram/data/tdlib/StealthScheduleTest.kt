package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.StealthScheduleUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class StealthScheduleTest {
    @Test fun legacyShortDelayNeverContactsTdlibUnlessExplicitlyConsented() = runBlocking {
        var calls = 0
        val scheduler = TdStealthScheduler(TdRequests { calls++; error("TDLib must not be touched") }, { 3 })
        assertTrue(scheduler.active())
        for (options in listOf(MessageSendOptions(), MessageSendOptions(scheduledAtSeconds = 1_700_000_000L))) {
            val failure = runCatching { scheduler.resolve(42L, options) }.exceptionOrNull()
            assertTrue(failure is StealthScheduleUnavailableException)
        }
        assertEquals(0, calls)
        val consented = scheduler.resolve(42L, MessageSendOptions(sendNowConsent = true))
        // Only the consented send reaches TDLib: the stub client refuses it, so the call fails right after the hand-off.
        runCatching { scheduler.scheduledRequest(consented, TdApi.GetMe()) }
        assertEquals(1, calls)
    }

    @Test fun enablingStealthAfterOrdinarySendPreparationBlocksDispatch() = runBlocking {
        var lead: Int? = null
        var touched = false
        val scheduler = TdStealthScheduler(TdRequests { touched = true; error("TDLib must not be touched") }, { lead })
        val prepared = scheduler.resolve(42L, MessageSendOptions())
        lead = 30
        val failure = runCatching {
            scheduler.scheduledRequest(prepared, TdApi.GetMe())
        }.exceptionOrNull()
        assertTrue(failure is StealthScheduleUnavailableException)
        assertFalse(touched)
    }

    @Test fun aRefusedAdmissionNeverReachesTdlib() = runBlocking {
        var touched = false
        val request = TdRequests { touched = true; error("TDLib must not be touched") }
        val failure = runCatching {
            request.invokeAdmitted(TdApi.GetMe(), lock = { it() }, admit = { error("refused") })
        }.exceptionOrNull()
        assertEquals("refused", failure?.message)
        assertFalse(touched)
    }

    @Test fun theHandOffToTdlibHappensInsideTheSettingsLock() = runBlocking {
        var locked = false
        var handedOffLocked = false
        val lock: (() -> Unit) -> Unit = { block -> locked = true; try { block() } finally { locked = false } }
        val request = TdRequests { handedOffLocked = locked; error("handed to TDLib") }
        runCatching { request.invokeAdmitted(TdApi.GetMe(), lock, admit = {}) }
        assertTrue(handedOffLocked)
    }

    @Test fun aRequestCancelledWhileWaitingForTheSettingsLockIsNeverSent() = runBlocking {
        val monitor = Any()
        val holding = CountDownLatch(1)
        val waiting = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Another thread holds the settings lock until the waiting request has been cancelled.
        val holder = thread {
            synchronized(monitor) {
                holding.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        assertTrue(holding.await(10, TimeUnit.SECONDS))
        val admitted = AtomicBoolean(false)
        val touched = AtomicBoolean(false)
        val request = TdRequests { touched.set(true); error("TDLib must not be touched") }
        val lock: (() -> Unit) -> Unit = { block ->
            waiting.countDown()
            synchronized(monitor) { block() }
        }
        val caller = launch(Dispatchers.Default) {
            runCatching { request.invokeAdmitted(TdApi.GetMe(), lock, admit = { admitted.set(true) }) }
        }
        assertTrue(waiting.await(10, TimeUnit.SECONDS))
        caller.cancel()
        release.countDown()
        caller.join()
        holder.join()
        assertFalse(admitted.get())
        assertFalse(touched.get())
    }

    @Test fun aResendOfUncheckedMessagesIsRefusedEvenIfTheSwitchGoesOffAndBackOn() = runBlocking {
        // The switch reads on when the resend starts, off in between and on at the hand-off. The messages are plain
        // failed messages, not waiting in the scheduled queue, so no resend may reach TDLib.
        val reads = listOf<Int?>(15, null, 15).iterator()
        val request = ScriptedTdRequests { function ->
            when (function) {
                is TdApi.GetOption -> TdApi.OptionValueInteger(serverNow)
                is TdApi.GetMessage -> TdApi.Message()
                else -> TdApi.Messages()
            }
        }
        val failure = runCatching {
            TdStealthScheduler(request, { reads.next() }).resend(42L, listOf(7L), sendNowConsent = false)
        }.exceptionOrNull()
        assertTrue(failure is StealthScheduleUnavailableException)
        assertTrue(request.sent.none { it is TdApi.ResendMessages })
    }

    @Test fun aResendOfMessagesStillWaitingInTheScheduledQueueGoesThroughWhileProtectionIsOn() = runBlocking {
        val request = ScriptedTdRequests { function ->
            when (function) {
                is TdApi.GetOption -> TdApi.OptionValueInteger(serverNow)
                is TdApi.GetMessage -> TdApi.Message().apply {
                    schedulingState = TdApi.MessageSchedulingStateSendAtDate((serverNow + 600).toInt(), 0)
                }
                else -> TdApi.Messages()
            }
        }
        TdStealthScheduler(request, { 15 }).resend(42L, listOf(7L), sendNowConsent = false)
        assertTrue(request.sent.any { it is TdApi.ResendMessages })
    }

    @Test fun protectionTurnedOnDuringAnOrdinaryResendRefusesIt() = runBlocking {
        val reads = listOf<Int?>(null, 15).iterator()
        val request = ScriptedTdRequests { TdApi.Messages() }
        val failure = runCatching {
            TdStealthScheduler(request, { reads.next() }).resend(42L, listOf(7L), sendNowConsent = false)
        }.exceptionOrNull()
        assertTrue(failure is StealthScheduleUnavailableException)
        assertTrue(request.sent.isEmpty())
    }

    @Test fun textIsScheduledExactlyTheChosenLeadAfterServerTime() {
        assertEquals(1_000_015L, stealthScheduleAt(1_000_000L, 15, 0L))
        assertEquals(1_000_060L, stealthScheduleAt(1_000_000L, 60, 0L))
    }

    @Test fun everyLeadStaysClearOfTheImmediateSendThreshold() {
        val now = 1_700_000_000L
        val at = stealthScheduleAt(now, 15, 0L)
        assertTrue(MessageSendOptions(scheduledAtSeconds = at).scheduledTimeIsFuture(now))
        assertTrue(at - now > MessageSendOptions.MIN_SCHEDULE_LEAD_SECONDS)
    }

    @Test fun uploadsGetTimeAtASlowMobileSpeedRoundedUp() {
        assertEquals(15L + 1, stealthScheduleAt(0L, 15, 1L))
        assertEquals(15L + 16, stealthScheduleAt(0L, 15, 16 * STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND))
        assertEquals(15L + 17, stealthScheduleAt(0L, 15, 16 * STEALTH_ASSUMED_UPLOAD_BYTES_PER_SECOND + 1))
    }

    @Test fun uploadAllowanceIsCapped() {
        assertEquals(15L + STEALTH_MAX_UPLOAD_ALLOWANCE_SECONDS, stealthScheduleAt(0L, 15, Long.MAX_VALUE / 2))
    }

    @Test fun preliminaryUploadIsDoneWhenAllBytesAreUpOrTdlibHoldsTheRest() {
        assertTrue(preuploadDone(file(size = 100, uploaded = 100, active = true)))
        assertTrue(preuploadDone(file(size = 100, uploaded = 90, active = false)))
        assertTrue(preuploadDone(file(size = 100, uploaded = 0, active = false, completed = true)))
        assertFalse(preuploadDone(file(size = 100, uploaded = 0, active = true)))
        assertFalse(preuploadDone(file(size = 100, uploaded = 40, active = true)))
        // Not started yet: nothing is up, so the upload is not done.
        assertFalse(preuploadDone(file(size = 100, uploaded = 0, active = false)))
    }

    private fun file(size: Long, uploaded: Long, active: Boolean, completed: Boolean = false) =
        TdApi.File(1, size, size, TdApi.LocalFile(), TdApi.RemoteFile("", "", active, completed, uploaded))

    private val serverNow = 1_700_000_000L

    /** Answers the requests that reach TDLib with [answer]; each one that reaches TDLib is recorded in [sent]. */
    private class ScriptedTdRequests(private val answer: (TdApi.Function<*>) -> TdApi.Object) :
        TdRequests({ error("TDLib is not used by these tests") }) {
        val sent = mutableListOf<TdApi.Function<*>>()

        override fun handOff(function: TdApi.Function<*>, handler: Client.ResultHandler) {
            sent += function
            handler.onResult(answer(function))
        }
    }
}
