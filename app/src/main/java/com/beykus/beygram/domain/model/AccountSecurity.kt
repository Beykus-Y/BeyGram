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

/** Where Telegram sent the code for a phone-number change, so the dialog can say where to look. */
enum class PhoneCodeDelivery {
    /** A message inside Telegram, on a device that is already signed in with the number. */
    TelegramApp,
    Sms,
    Call,
    FlashCall,
    MissedCall,
    Fragment,
    Firebase,
    Other
}

/**
 * A phone-number change waiting for its code. The code goes to [phoneNumber], which is the NEW
 * number. [codeLength] is 0 when Telegram does not state it; [canResend] is true while Telegram
 * offers another delivery route.
 */
data class PhoneChangeCode(
    val phoneNumber: String,
    val delivery: PhoneCodeDelivery,
    val codeLength: Int,
    val canResend: Boolean,
    val timeoutSeconds: Int
)

private const val MAX_PHONE_INPUT_LENGTH = 20
private const val MIN_PHONE_DIGITS = 7
private const val MAX_PHONE_DIGITS = 15

/**
 * Keeps one leading '+' and the ASCII digits, dropping spaces, brackets and dashes as the user types.
 * The result is capped at [MAX_PHONE_INPUT_LENGTH] characters, the plus included.
 */
fun normalizePhoneNumberInput(raw: String): String {
    val trimmed = raw.trim()
    val prefix = if (trimmed.startsWith('+')) "+" else ""
    val digits = trimmed.filter { it in '0'..'9' }
    return (prefix + digits).take(MAX_PHONE_INPUT_LENGTH)
}

/**
 * International numbers have 7 to 15 digits. This only stops an obviously incomplete number before
 * the request; Telegram's answer to the request is the authority.
 */
fun isPlausiblePhoneNumber(value: String): Boolean {
    val digits = normalizePhoneNumberInput(value).count { it in '0'..'9' }
    return digits in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS
}
