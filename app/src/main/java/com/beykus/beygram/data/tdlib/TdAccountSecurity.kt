package com.beykus.beygram.data.tdlib

import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.CloudPasswordState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.drinkless.tdlib.TdApi

/** The server may need longer than a normal read when it erases an account. */
private const val ACCOUNT_DELETE_TIMEOUT_MS = 45_000L

/**
 * TDLib part of account security. It is a separate collaborator so the repository's huge chat
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

    suspend fun deleteAccount(password: String): Result<Unit> = securityResult {
        requests(TdApi.DeleteAccount("", password), ACCOUNT_DELETE_TIMEOUT_MS)
        Unit
    }

    private suspend fun <T> securityResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(UserMessageException(UiText.Res(R.string.err_tdlib_request_timeout), timeout))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: UserMessageException) {
        Result.failure(error)
    } catch (error: Exception) {
        Result.failure(UserMessageException(accountSecurityErrorText(error), error))
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
        text.contains("PHONE_CODE_INVALID") -> UiText.Res(R.string.err_auth_code_invalid)
        text.contains("PHONE_CODE_EXPIRED") -> UiText.Res(R.string.err_auth_code_expired)
        text.contains("PHONE_NUMBER_INVALID") -> UiText.Res(R.string.err_phone_invalid)
        text.contains("PHONE_NUMBER_OCCUPIED") -> UiText.Res(R.string.account_security_phone_occupied)
        else -> UiText.Res(R.string.account_security_action_failed)
    }
}
