package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.StealthScheduleUnavailableException
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthScheduleTest {
    @Test fun legacyShortDelayNeverContactsTdlibUnlessExplicitlyConsented() = runBlocking {
        var calls = 0
        val scheduler = TdStealthScheduler(TdRequests { calls++; error("TDLib must not be touched") }) { 3 }
        assertTrue(scheduler.active())
        for (options in listOf(MessageSendOptions(), MessageSendOptions(scheduledAtSeconds = 1_700_000_000L))) {
            val failure = runCatching { scheduler.resolve(42L, options) }.exceptionOrNull()
            assertTrue(failure is StealthScheduleUnavailableException)
        }
        val consented = scheduler.resolve(42L, MessageSendOptions(sendNowConsent = true))
        assertTrue(scheduler.scheduledRequest(consented) { true })
        assertEquals(0, calls)
    }

    @Test fun enablingStealthAfterOrdinarySendPreparationBlocksDispatch() = runBlocking {
        var lead: Int? = null
        val scheduler = TdStealthScheduler(TdRequests { error("TDLib must not be touched") }) { lead }
        val prepared = scheduler.resolve(42L, MessageSendOptions())
        lead = 30
        var dispatched = false
        val failure = runCatching {
            scheduler.scheduledRequest(prepared) { dispatched = true }
        }.exceptionOrNull()
        assertTrue(failure is StealthScheduleUnavailableException)
        assertFalse(dispatched)
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
}
