package com.beykus.beygram.domain.model

/**
 * Server-side state of Telegram's two-step verification.
 *
 * This model deliberately carries no password, code, or full recovery address. TDLib only
 * exposes whether a recovery address exists and, while a change is pending, its masked pattern.
 */
data class CloudPasswordState(
    val hasPassword: Boolean = false,
    val passwordHint: String = "",
    val hasRecoveryEmailAddress: Boolean = false,
    /** Masked address from TDLib while a new recovery email waits for its confirmation code. */
    val pendingRecoveryEmailPattern: String? = null,
    /** Masked login email configured by Telegram, if any. It is never a secret. */
    val loginEmailAddressPattern: String? = null
)

/** Client-side checks before asking TDLib to set a cloud password. */
enum class NewPasswordValidation {
    Empty,
    TooShort,
    Mismatch,
    Valid
}

/**
 * Telegram enforces its own password policy on the server. The local minimum only prevents an
 * obvious mistake before a network round trip; it never replaces TDLib's answer.
 */
fun validateNewPassword(
    newPassword: String,
    confirmation: String,
    minimumLength: Int = 4
): NewPasswordValidation = when {
    newPassword.isEmpty() -> NewPasswordValidation.Empty
    newPassword.length < minimumLength -> NewPasswordValidation.TooShort
    newPassword != confirmation -> NewPasswordValidation.Mismatch
    else -> NewPasswordValidation.Valid
}

/** Recovery addresses are validated by Telegram; trimming is the only local normalisation. */
fun normalizeRecoveryEmail(value: String): String = value.trim()

/**
 * A deliberately permissive check. The authoritative validation belongs to Telegram, so this
 * only catches an address that cannot be an email at all.
 */
fun isValidRecoveryEmail(value: String): Boolean {
    val email = normalizeRecoveryEmail(value)
    if (email.isEmpty() || email.any { it.isWhitespace() }) return false
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@') || at == email.lastIndex) return false
    val domain = email.substring(at + 1)
    return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

/**
 * Typed confirmation for destructive account deletion. Case and surrounding spaces are ignored,
 * but the word itself must match exactly, so a stray tap cannot delete the server account.
 */
fun matchesDeletionConfirmation(input: String, expectedWord: String): Boolean =
    input.trim().equals(expectedWord.trim(), ignoreCase = true)
