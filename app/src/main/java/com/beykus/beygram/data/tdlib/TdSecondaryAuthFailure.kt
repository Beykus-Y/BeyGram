package com.beykus.beygram.data.tdlib

/**
 * A non-secret projection of a TDLib authorization error. The original TDLib
 * message is kept only in memory for classification and is never persisted.
 */
internal class TdSecondaryAuthException(
    val tdlibCode: Int,
    val tdlibMessage: String
) : IllegalStateException("TDLib account authorization failed (code $tdlibCode)")

/** User-actionable sign-in failure categories shown by the shared account switcher. */
internal enum class TdSecondaryAuthFailure {
    INVALID_PHONE,
    INVALID_CODE,
    CODE_EXPIRED,
    INVALID_PASSWORD,
    FLOOD_WAIT,
    UNKNOWN
}

/**
 * TDLib reports most credential problems as HTTP-like 400 errors with a stable
 * symbolic suffix. Classification is intentionally conservative: anything not
 * recognised stays UNKNOWN instead of claiming that a secret was wrong.
 */
internal fun tdSecondaryAuthFailure(tdlibMessage: String): TdSecondaryAuthFailure {
    val message = tdlibMessage.uppercase()
    return when {
        "PHONE_NUMBER_INVALID" in message -> TdSecondaryAuthFailure.INVALID_PHONE
        "PHONE_CODE_INVALID" in message -> TdSecondaryAuthFailure.INVALID_CODE
        "PHONE_CODE_EXPIRED" in message -> TdSecondaryAuthFailure.CODE_EXPIRED
        "PASSWORD_HASH_INVALID" in message -> TdSecondaryAuthFailure.INVALID_PASSWORD
        "FLOOD_WAIT" in message -> TdSecondaryAuthFailure.FLOOD_WAIT
        else -> TdSecondaryAuthFailure.UNKNOWN
    }
}
