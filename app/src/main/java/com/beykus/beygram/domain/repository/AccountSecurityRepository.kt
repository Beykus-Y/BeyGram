package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.CloudPasswordState
import com.beykus.beygram.domain.model.PhoneChangeCode

/**
 * Account-wide security operations stored on Telegram's servers.
 *
 * Phone-number changes use Telegram's two-step code flow. TDLib documents
 * {@code phoneNumberCodeTypeChange} as intended for official Android and iOS applications, so
 * Telegram may refuse the first request for BeyGram's credentials. That refusal arrives as a
 * failure with a plain explanation; the flow is never retried through another method.
 */
interface AccountSecurityRepository {
    /** Reads TDLib's cloud-password state, including the masked hint and recovery-email flags. */
    suspend fun cloudPasswordState(): Result<CloudPasswordState>

    /**
     * Sets, changes, or removes the cloud password. TDLib applies the change only after checking
     * [currentPassword]; an empty [newPassword] disables two-step verification.
     */
    suspend fun setCloudPassword(
        currentPassword: String,
        newPassword: String,
        hint: String
    ): Result<CloudPasswordState>

    /** Starts a recovery-email change. TDLib returns the pending, masked address and code info. */
    suspend fun setRecoveryEmail(currentPassword: String, email: String): Result<CloudPasswordState>

    /** Confirms the code sent to the new recovery address. */
    suspend fun confirmRecoveryEmail(code: String): Result<CloudPasswordState>

    /** Requests a fresh recovery-email code without changing the pending address. */
    suspend fun resendRecoveryEmailCode(): Result<CloudPasswordState>

    /** Abandons an unconfirmed recovery-email change. */
    suspend fun cancelRecoveryEmailChange(): Result<CloudPasswordState>

    /**
     * Asks Telegram to send a code to [phoneNumber], the new number, for changing the account's
     * phone. Returns the delivery details the dialog explains. Telegram may refuse the request.
     */
    suspend fun sendPhoneChangeCode(phoneNumber: String): Result<PhoneChangeCode>

    /** Asks Telegram to deliver the pending phone-change code again, by another route when one exists. */
    suspend fun resendPhoneChangeCode(): Result<PhoneChangeCode>

    /**
     * Confirms the phone-change code. Success means Telegram has already switched the account to the
     * new number; the displayed number then follows from the regular profile updates.
     */
    suspend fun checkPhoneChangeCode(code: String): Result<Unit>

    /**
     * Irreversibly deletes the selected Telegram account on the server. [password] is the cloud
     * password, or an empty string when two-step verification is disabled. Local session cleanup
     * belongs to the caller and must run only after this succeeds.
     */
    suspend fun deleteTelegramAccount(password: String): Result<Unit>
}
