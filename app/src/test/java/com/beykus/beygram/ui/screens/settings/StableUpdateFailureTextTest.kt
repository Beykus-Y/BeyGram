package com.beykus.beygram.ui.screens.settings

import com.beykus.beygram.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StableUpdateFailureTextTest {
    @Test fun connectionFailuresSayToCheckTheConnection() {
        assertEquals(R.string.updates_error_network,
            updateFailureTextRes(UnknownHostException("api.github.com"), R.string.updates_error_check))
        assertEquals(R.string.updates_error_network,
            updateFailureTextRes(ConnectException("connection refused"), R.string.updates_error_download))
        assertEquals(R.string.updates_error_network,
            updateFailureTextRes(SocketTimeoutException("read timed out"), R.string.updates_error_check))
    }

    @Test fun otherFailuresUseTheFriendlyTextOfTheirStep() {
        // Repository messages can carry server text; the screen shows the step text instead of them.
        assertEquals(R.string.updates_error_check,
            updateFailureTextRes(IOException("Ошибка GitHub: HTTP 503."), R.string.updates_error_check))
        assertEquals(R.string.updates_error_download,
            updateFailureTextRes(IOException("Контрольная сумма APK не совпадает."), R.string.updates_error_download))
    }
}
