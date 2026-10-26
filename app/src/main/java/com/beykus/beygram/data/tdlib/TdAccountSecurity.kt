package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.CloudPasswordState
import com.beykus.beygram.domain.model.PhoneChangeCode
import com.beykus.beygram.domain.model.PhoneCodeDelivery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.drinkless.tdlib.TdApi

/** TDLib part of account security. It is a separate collaborator so the repository's huge chat
 * surface never mixes with password and recovery-email requests.
 *
 * Passwords exist only as method arguments and TDLib request fields. They are never stored in a
 * field, StateFlow, preference, or diagnostic event.
 */
internal class TdAccountSecurity(private val requests: TdRequests) {
    suspend fun passwordState(): Result<CloudPasswordState> = securityResult {
        requests(TdApi.GetPasswordState()).toCloudPasswordState()
    }

    suspend fun setPassword(
        currentPassword: String,
        newPassword: String,
        hint: String
    ): Result<CloudPasswordState> = securityResult {
        requests(
            TdApi.SetPassword(
                currentPassword,
                newPassword,
                hint.trim(),
                false,
                ""
            )
        ).toCloudPasswordState()
    }

    suspend fun setRecoveryEmail(
        currentPassword: String,
        email: String
    ): Result<CloudPasswordState> = securityResult {
        requests(TdApi.SetRecoveryEmailAddress(currentPassword, email.trim())).toCloudPasswordState()
    }

    suspend fun confirmRecoveryEmail(code: String): Result<CloudPasswordState> = securityResult {
        requests(TdApi.CheckRecoveryEmailAddressCode(code.trim())).toCloudPasswordState()
    }

    suspend fun resendRecoveryEmailCode(): Result<CloudPasswordState> = securityResult {
        requests(TdApi.ResendRecoveryEmailAddressCode()).toCloudPasswordState()
    }

    suspend fun cancelRecoveryEmailChange(): Result<CloudPasswordState> = securityResult {
        requests(TdApi.CancelRecoveryEmailAddressVerification()).toCloudPasswordState()
    }

    /**
     * Starts a phone-number change: TDLib sends the code to [phoneNumber], the new number. Telegram
     * documents this request for official apps only, so an unexplained refusal gets its own message.
     */
    suspend fun sendPhoneChangeCode(phoneNumber: String): Result<PhoneChangeCode> =
        securityResult(::phoneChangeSendErrorText) {
            requests(
                TdApi.SendPhoneNumberCode(
                    phoneNumber,
                    TdApi.PhoneNumberAuthenticationSettings(
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        emptyArray<String>()
                    ),
                    TdApi.PhoneNumberCodeTypeChange()
                )
            ).toPhoneChangeCode()
        }

    /** The user asked for the resend, so TDLib gets that reason explicitly instead of a null reason object. */
    suspend fun resendPhoneChangeCode(): Result<PhoneChangeCode> = securityResult {
        requests(TdApi.ResendPhoneNumberCode(TdApi.ResendCodeReasonUserRequest())).toPhoneChangeCode()
    }

    /**
     * Confirms the phone-change code with a bounded wait. Unlike account deletion, a late success
     * needs no local cleanup, because the profile follows TDLib's user updates on its own.
     */
    suspend fun checkPhoneChangeCode(code: String): Result<Unit> = securityResult {
        requests(TdApi.CheckPhoneNumberCode(code.trim()))
        Unit
    }

    suspend fun deleteAccount(password: String): Result<Unit> = securityResult {
        requests.awaitResponse(TdApi.DeleteAccount("", password))
        Unit
    }

    private suspend fun <T> securityResult(
        errorText: (Exception) -> UiText = ::accountSecurityErrorText,
        block: suspend () -> T
    ): Result<T> = try {
        Result.success(block())
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(UserMessageException(UiText.Res(R.string.err_tdlib_request_timeout), timeout))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: UserMessageException) {
        Result.failure(error)
    } catch (error: Exception) {
        Result.failure(UserMessageException(errorText(error), error))
    }
}

/** TDLib's PasswordState never contains a password; only the hint and masked addresses are read. */
internal fun TdApi.PasswordState.toCloudPasswordState(): CloudPasswordState = CloudPasswordState(
    hasPassword = hasPassword,
    passwordHint = passwordHint.orEmpty(),
    hasRecoveryEmailAddress = hasRecoveryEmailAddress,
    pendingRecoveryEmailPattern = recoveryEmailAddressCodeInfo?.emailAddressPattern
        ?.takeIf { it.isNotBlank() },
    loginEmailAddressPattern = loginEmailAddressPattern?.takeIf { it.isNotBlank() }
)

/**
 * Maps TDLib's code route to the delivery the dialog explains. Routes without a length, and routes
 * this file does not know, keep length 0 or [PhoneCodeDelivery.Other]; the code itself is never shown.
 */
internal fun TdApi.AuthenticationCodeInfo.toPhoneChangeCode(): PhoneChangeCode {
    val (delivery, length) = when (val route = type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> PhoneCodeDelivery.TelegramApp to route.length
        is TdApi.AuthenticationCodeTypeSms -> PhoneCodeDelivery.Sms to route.length
        is TdApi.AuthenticationCodeTypeSmsWord, is TdApi.AuthenticationCodeTypeSmsPhrase ->
            PhoneCodeDelivery.Sms to 0
        is TdApi.AuthenticationCodeTypeCall -> PhoneCodeDelivery.Call to route.length
        is TdApi.AuthenticationCodeTypeFlashCall -> PhoneCodeDelivery.FlashCall to 0
        is TdApi.AuthenticationCodeTypeMissedCall -> PhoneCodeDelivery.MissedCall to route.length
        is TdApi.AuthenticationCodeTypeFragment -> PhoneCodeDelivery.Fragment to route.length
        is TdApi.AuthenticationCodeTypeFirebaseAndroid -> PhoneCodeDelivery.Firebase to route.length
        is TdApi.AuthenticationCodeTypeFirebaseIos -> PhoneCodeDelivery.Firebase to route.length
        else -> PhoneCodeDelivery.Other to 0
    }
    return PhoneChangeCode(
        phoneNumber = phoneNumber.orEmpty(),
        delivery = delivery,
        codeLength = length,
        canResend = nextType != null,
        timeoutSeconds = timeout
    )
}

/**
 * The send step is the one Telegram reserves for official apps, so a failure this file does not
 * recognise most likely means the request itself was refused. That case gets its own explanation
 * instead of the generic failure text; every known server reason keeps its specific message.
 */
internal fun phoneChangeSendErrorText(error: Exception): UiText {
    val text = accountSecurityErrorText(error)
    // Only a server answer may mean "refused"; local and network failures keep the generic text.
    return if (error is TdRequestError && text == UiText.Res(R.string.account_security_action_failed)) {
        UiText.Res(R.string.account_security_phone_change_refused)
    } else {
        text
    }
}

/** Known server errors get a specific message; unknown details are never shown to the user. */
internal fun accountSecurityErrorText(error: Exception): UiText {
    val text = error.message.orEmpty()
    val code = (error as? TdRequestError)?.code
    return when {
        text.contains("FLOOD_WAIT") || text.contains("Too Many Requests") || code == 429 ->
            UiText.Res(R.string.err_tdlib_flood_wait)
        text.contains("PASSWORD_HASH_INVALID") -> UiText.Res(R.string.err_auth_password_invalid)
        text.contains("PASSWORD_MISSING") -> UiText.Res(R.string.account_security_password_missing)
        text.contains("PASSWORD_TOO_FRESH") || text.contains("SESSION_TOO_FRESH") ->
            UiText.Res(R.string.account_security_password_too_fresh)
        text.contains("EMAIL_CODE_INVALID") ||
            text.contains("EMAIL_ADDRESS_CONFIRMATION_EXPIRED") ->
            UiText.Res(R.string.account_security_email_code_invalid)
        text.contains("EMAIL_INVALID") || text.contains("EMAIL_ADDRESS_INVALID") ->
            UiText.Res(R.string.account_security_email_invalid)
        text.contains("EMAIL_NOT_ALLOWED") -> UiText.Res(R.string.account_security_email_not_allowed)
        text.contains("PHONE_CODE_INVALID") || text.contains("PHONE_CODE_EMPTY") ->
            UiText.Res(R.string.err_auth_code_invalid)
        text.contains("PHONE_CODE_EXPIRED") -> UiText.Res(R.string.err_auth_code_expired)
        text.contains("PHONE_NUMBER_INVALID") -> UiText.Res(R.string.err_phone_invalid)
        text.contains("PHONE_NUMBER_OCCUPIED") -> UiText.Res(R.string.account_security_phone_occupied)
        text.contains("PHONE_NUMBER_BANNED") -> UiText.Res(R.string.account_security_phone_banned)
        text.contains("PHONE_NUMBER_FLOOD") -> UiText.Res(R.string.err_tdlib_flood_wait)
        else -> UiText.Res(R.string.account_security_action_failed)
    }
}
