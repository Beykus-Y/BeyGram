package com.beykus.beygram.share

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareOutcomeTest {
    @Test fun wrappedTimeoutIsNotAConfirmedRejection() {
        val error = runBlocking {
            try {
                withTimeout(1L) { awaitCancellation() }
            } catch (timeout: TimeoutCancellationException) {
                timeout
            }
        }
        assertTrue(isShareOutcomeUnknown(error))
        assertTrue(isShareOutcomeUnknown(UserMessageException(UiText.Raw("timeout"), error)))
    }

    @Test fun normalRejectionIsNotATimeout() {
        assertFalse(isShareOutcomeUnknown(null))
        assertFalse(isShareOutcomeUnknown(IllegalStateException("denied")))
    }
    @Test fun beforeHandoffCopiesCanBeDiscarded() {
        val error = IllegalStateException("permission refused")
        val handoff = ShareMediaHandoff()
        val classified = handoff.classify(Result.failure(error)).exceptionOrNull()
        assertTrue(classified is ShareMediaNotHandedOffException)
        assertFalse(isShareOutcomeUnknown(classified))
        assertTrue(classified?.cause === error)
    }

    @Test fun afterHandoffFailureMustKeepTdlibFiles() {
        val error = ShareOutcomeUncertainException()
        val handoff = ShareMediaHandoff()
        handoff.markHandedOff()
        assertTrue(handoff.classify(Result.failure(error)).exceptionOrNull() === error)
        assertTrue(isShareOutcomeUnknown(error))
        assertTrue(handoff.classify(Result.success(Unit)).isSuccess)
    }
}
