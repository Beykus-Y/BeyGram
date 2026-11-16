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
}
