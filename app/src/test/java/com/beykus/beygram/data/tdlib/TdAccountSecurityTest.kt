package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdAccountSecurityTest {
    @Test
    fun `password state exposes flags and masked addresses only`() {
        val state = TdApi.PasswordState(
            true,
            "favourite place",
            true,
            false,
            TdApi.EmailAddressAuthenticationCodeInfo("a***@example.com", 6),
            "l***@example.com",
            0
        ).toCloudPasswordState()

        assertEquals(true, state.hasPassword)
        assertEquals("favourite place", state.passwordHint)
        assertEquals(true, state.hasRecoveryEmailAddress)
        assertEquals("a***@example.com", state.pendingRecoveryEmailPattern)
        assertEquals("l***@example.com", state.loginEmailAddressPattern)
    }

    @Test
    fun `no pending recovery code means no pending address`() {
        val state = TdApi.PasswordState(
            false,
            null,
            false,
            false,
            null,
            null,
            0
        ).toCloudPasswordState()

        assertEquals(false, state.hasPassword)
        assertEquals("", state.passwordHint)
        assertEquals(false, state.hasRecoveryEmailAddress)
        assertNull(state.pendingRecoveryEmailPattern)
        assertNull(state.loginEmailAddressPattern)
    }

    @Test
    fun `wrong cloud password has its own message`() {
        assertEquals(
            UiText.Res(R.string.err_auth_password_invalid),
            accountSecurityErrorText(TdRequestError(400, "PASSWORD_HASH_INVALID"))
        )
    }

    @Test
    fun `flood wait is recognized by code or message`() {
        assertEquals(
            UiText.Res(R.string.err_tdlib_flood_wait),
            accountSecurityErrorText(TdRequestError(429, "Too Many Requests"))
        )
        assertEquals(
            UiText.Res(R.string.err_tdlib_flood_wait),
            accountSecurityErrorText(IllegalStateException("FLOOD_WAIT_42"))
        )
    }

    @Test
    fun `email errors are separated from generic failures`() {
        assertEquals(
            UiText.Res(R.string.account_security_email_code_invalid),
            accountSecurityErrorText(TdRequestError(400, "EMAIL_CODE_INVALID"))
        )
        assertEquals(
            UiText.Res(R.string.account_security_email_invalid),
            accountSecurityErrorText(TdRequestError(400, "EMAIL_ADDRESS_INVALID"))
        )
        assertEquals(
            UiText.Res(R.string.account_security_email_not_allowed),
            accountSecurityErrorText(TdRequestError(400, "EMAIL_NOT_ALLOWED"))
        )
        assertEquals(
            UiText.Res(R.string.account_security_action_failed),
            accountSecurityErrorText(TdRequestError(500, "Internal Server Error"))
        )
    }

    @Test
    fun `recent password change is explained without exposing server text`() {
        assertEquals(
            UiText.Res(R.string.account_security_password_too_fresh),
            accountSecurityErrorText(TdRequestError(400, "PASSWORD_TOO_FRESH_10"))
        )
    }
}
