package com.beykus.beygram.domain.repository

import com.beykus.beygram.domain.model.CloudPasswordState

/**
 * Account-wide security operations stored on Telegram's servers.
 *
 * Phone-number changes are intentionally absent. TDLib documents
 * {@code phoneNumberCodeTypeChange} as available to official Android and iOS applications only,
 * so BeyGram's third-party API credentials cannot safely complete that flow. The UI explains this
 * limitation instead of sending a code to a number that Telegram will refuse to attach.
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
     * Irreversibly deletes the selected Telegram account on the server. [password] is the cloud
     * password, or an empty string when two-step verification is disabled. Local session cleanup
     * belongs to the caller and must run only after this succeeds.
     */
    suspend fun deleteTelegramAccount(password: String): Result<Unit>
}
