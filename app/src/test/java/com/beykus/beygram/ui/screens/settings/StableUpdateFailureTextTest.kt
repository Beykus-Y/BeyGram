package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.R
import com.beykus.beygram.data.update.StableUpdateException
import com.beykus.beygram.data.update.StableUpdateReason
import com.beykus.beygram.data.update.UpdateStep
import com.beykus.beygram.data.update.updateErrorReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StableUpdateFailureTextTest {
    @Test fun connectionFailuresSayToCheckTheConnection() {
        assertEquals(R.string.updates_error_network,
            updateFailureTextRes(updateErrorReason(UnknownHostException("api.github.com")), UpdateStep.Check))
        assertEquals(R.string.updates_error_network,
            updateFailureTextRes(updateErrorReason(ConnectException("connection refused")), UpdateStep.Download))
    }

    @Test fun slowConnectionsGetTheTimeoutText() {
        assertEquals(R.string.updates_error_timeout,
            updateFailureTextRes(updateErrorReason(SocketTimeoutException("read timed out")), UpdateStep.Check))
    }

    @Test fun unclassifiedErrorsUseTheFriendlyTextOfTheirStep() {
        // Repository messages can carry server text; without a typed reason the screen shows the step text.
        assertEquals(R.string.updates_error_check,
            updateFailureTextRes(updateErrorReason(IOException("Ошибка GitHub: HTTP 503.")), UpdateStep.Check))
        assertEquals(R.string.updates_error_download,
            updateFailureTextRes(updateErrorReason(IOException("неизвестная ошибка")), UpdateStep.Download))
    }

    @Test fun typedFailuresGetTheirOwnText() {
        assertEquals(R.string.updates_error_no_space,
            updateFailureTextRes(updateErrorReason(StableUpdateException(StableUpdateReason.NoFreeSpace, "мало места")),
                UpdateStep.Download))
        assertEquals(R.string.updates_error_already_installed,
            updateFailureTextRes(updateErrorReason(StableUpdateException(StableUpdateReason.AlreadyInstalled, "тот же код")),
                UpdateStep.Download))
        assertEquals(R.string.updates_error_checksum,
            updateFailureTextRes(updateErrorReason(StableUpdateException(StableUpdateReason.ChecksumMismatch, "hash")),
                UpdateStep.Download))
    }

    @Test fun everyReasonHasItsOwnText() {
        val texts = StableUpdateReason.values().map { updateReasonTextRes(it) }
        assertEquals(StableUpdateReason.values().size, texts.toSet().size)
    }

    @Test fun rateLimitAndMissingReleaseAreNamedByReason() {
        assertEquals(R.string.updates_error_rate_limited, updateReasonTextRes(StableUpdateReason.RateLimited))
        assertEquals(R.string.updates_error_missing_release, updateReasonTextRes(StableUpdateReason.MissingRelease))
        assertNotEquals(updateReasonTextRes(StableUpdateReason.Outdated), updateReasonTextRes(StableUpdateReason.AlreadyInstalled))
    }
}
