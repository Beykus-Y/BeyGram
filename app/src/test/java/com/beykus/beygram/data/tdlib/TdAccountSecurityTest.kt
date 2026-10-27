package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.PhoneChangeCode
import com.beykus.beygram.domain.model.PhoneCodeDelivery
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

    @Test
    fun `sms code keeps its length and reports that another route exists`() {
        assertEquals(
            PhoneChangeCode("+79990000000", PhoneCodeDelivery.Sms, 5, true, 60),
            TdApi.AuthenticationCodeInfo(
                "+79990000000",
                TdApi.AuthenticationCodeTypeSms(5),
                TdApi.AuthenticationCodeTypeCall(5),
                60
            ).toPhoneChangeCode()
        )
    }

    @Test
    fun `telegram message without a next route cannot be resent`() {
        val code = TdApi.AuthenticationCodeInfo(
            "+79990000000",
            TdApi.AuthenticationCodeTypeTelegramMessage(6),
            null,
            0
        ).toPhoneChangeCode()

        assertEquals(PhoneCodeDelivery.TelegramApp, code.delivery)
        assertEquals(6, code.codeLength)
        assertEquals(false, code.canResend)
    }

    @Test
    fun `flash call and sms word routes have no stated length`() {
        assertEquals(
            PhoneChangeCode("+79990000000", PhoneCodeDelivery.FlashCall, 0, false, 30),
            TdApi.AuthenticationCodeInfo(
                "+79990000000",
                TdApi.AuthenticationCodeTypeFlashCall("pattern"),
                null,
                30
            ).toPhoneChangeCode()
        )
        assertEquals(
            PhoneCodeDelivery.SmsWord,
            TdApi.AuthenticationCodeInfo(
                "+79990000000",
                TdApi.AuthenticationCodeTypeSmsWord("a"),
                null,
                0
            ).toPhoneChangeCode().delivery
        )
    }

    @Test
    fun `missing code route is reported as other delivery`() {
        assertEquals(
            PhoneCodeDelivery.Other,
            TdApi.AuthenticationCodeInfo("+79990000000", null, null, 0).toPhoneChangeCode().delivery
        )
    }

    @Test
    fun `unexplained send refusal is explained as a refused change`() {
        assertEquals(
            UiText.Res(R.string.account_security_phone_change_refused),
            phoneChangeSendErrorText(TdRequestError(500, "Internal Server Error"))
        )
    }

    @Test
    fun `local failure on the send step keeps the generic message`() {
        assertEquals(
            UiText.Res(R.string.account_security_action_failed),
            phoneChangeSendErrorText(IllegalStateException("socket closed"))
        )
    }

    @Test
    fun `known phone errors keep their own messages on the send step`() {
        assertEquals(
            UiText.Res(R.string.account_security_phone_occupied),
            phoneChangeSendErrorText(TdRequestError(400, "PHONE_NUMBER_OCCUPIED"))
        )
        assertEquals(
            UiText.Res(R.string.err_tdlib_flood_wait),
            phoneChangeSendErrorText(TdRequestError(420, "FLOOD_WAIT_120"))
        )
    }

    @Test
    fun `banned and flooded numbers have their own messages`() {
        assertEquals(
            UiText.Res(R.string.account_security_phone_banned),
            accountSecurityErrorText(TdRequestError(400, "PHONE_NUMBER_BANNED"))
        )
        assertEquals(
            UiText.Res(R.string.err_tdlib_flood_wait),
            accountSecurityErrorText(TdRequestError(400, "PHONE_NUMBER_FLOOD"))
        )
    }

    @Test
    fun `empty phone code uses the invalid code message`() {
        assertEquals(
            UiText.Res(R.string.err_auth_code_invalid),
            accountSecurityErrorText(TdRequestError(400, "PHONE_CODE_EMPTY"))
        )
    }
}
