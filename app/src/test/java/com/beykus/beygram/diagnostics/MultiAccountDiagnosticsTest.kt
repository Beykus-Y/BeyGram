package com.beykus.beygram.diagnostics

import com.beykus.beygram.data.tdlib.TdAccountSwitchFailure
import com.beykus.beygram.data.tdlib.TdSecondaryAuthException
import com.beykus.beygram.data.tdlib.TdSecondaryRouteBlock
import com.beykus.beygram.data.tdlib.TdSecondaryRouteUnavailableException
import com.beykus.beygram.data.tdlib.TdSecondarySessionLimitException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAccountDiagnosticsTest {
    @Test fun sensitiveAttributeNamesAreNeverPublished() {
        val sanitized = MultiAccountDiagnostics.sanitizeAttributes(
            mapOf(
                "phone" to "+79991234567",
                "code" to "12345",
                "password" to "secret",
                "token" to "abcdef",
                "api_hash" to "hash",
                "route" to "FOLLOW_PRIMARY",
                "phase" to "READY",
                "count" to "2"
            )
        )
        assertEquals(setOf("route", "phase", "count"), sanitized.keys)
        assertEquals("FOLLOW_PRIMARY", sanitized["route"])
    }

    @Test fun allowedValuesAreRedactedAndTruncated() {
        val sanitized = MultiAccountDiagnostics.sanitizeAttributes(
            mapOf(
                "reason" to "call +79991234567 failed",
                "source" to "x".repeat(500)
            )
        )
        assertTrue(sanitized.getValue("reason").contains("[номер скрыт]"))
        assertFalse(sanitized.getValue("reason").contains("79991234567"))
        assertTrue(sanitized.getValue("source").length <= 160)
    }

    @Test fun accountIdsCannotLeakPhoneNumbers() {
        assertEquals("legacy", MultiAccountDiagnostics.sanitizeAccountId("legacy"))
        assertEquals("none", MultiAccountDiagnostics.sanitizeAccountId("none"))
        assertEquals("a-1234", MultiAccountDiagnostics.sanitizeAccountId("a-1234"))
        assertEquals("account", MultiAccountDiagnostics.sanitizeAccountId("+79991234567"))
        assertEquals("account", MultiAccountDiagnostics.sanitizeAccountId("123456789"))
    }

    @Test fun failuresAreClassifiedWithoutThrowableMessages() {
        assertEquals(
            "route_unavailable:LISTENER_NOT_READY",
            MultiAccountDiagnostics.errorCategory(
                TdSecondaryRouteUnavailableException(TdSecondaryRouteBlock.LISTENER_NOT_READY)
            )
        )
        assertEquals(
            "session_limit",
            MultiAccountDiagnostics.errorCategory(TdSecondarySessionLimitException())
        )
        assertEquals(
            "timeout",
            MultiAccountDiagnostics.errorCategory(TdAccountSwitchFailure.Timeout())
        )
        assertEquals(
            "tdlib_auth:400",
            MultiAccountDiagnostics.errorCategory(TdSecondaryAuthException(400, "PHONE_CODE_INVALID"))
        )
        assertEquals(
            "IllegalStateException",
            MultiAccountDiagnostics.errorCategory(IllegalStateException("sensitive message"))
        )
    }
}
