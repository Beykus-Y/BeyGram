package com.beykus.beygram.domain.model

/**
 * Server-side speech recognition parameters for an account without Telegram Premium.
 * Values come from TDLib's UpdateSpeechRecognitionTrial and are never guessed locally.
 */
data class SpeechRecognitionTrial(
    /** Total free recognitions per week; zero means that free recognition is unavailable. */
    val weeklyCount: Int,
    /** Attempts left in the current week. Zero is a real answer, unlike no data at all. */
    val leftCount: Int,
    /** Maximum media duration for a free recognition, in seconds; zero means no known limit. */
    val maxMediaDurationSeconds: Int,
    /** Unix timestamp of the next weekly reset, or null when TDLib has not reported it. */
    val nextResetDateSeconds: Long? = null
) {
    /**
     * What the UI may say about the current quota without inventing numbers or dates.
     * Null means that there is nothing useful to show (no free quota configured or no data yet).
     */
    fun quotaHint(nowSeconds: Long): SpeechRecognitionQuotaHint? {
        if (weeklyCount <= 0) return null
        val reset = nextResetDateSeconds?.takeIf { it > nowSeconds }
        return if (leftCount > 0) {
            SpeechRecognitionQuotaHint.Available(leftCount.coerceAtMost(weeklyCount), weeklyCount, reset)
        } else {
            SpeechRecognitionQuotaHint.Exhausted(reset)
        }
    }
}

sealed interface SpeechRecognitionQuotaHint {
    val resetDateSeconds: Long?

    data class Available(
        val left: Int,
        val total: Int,
        override val resetDateSeconds: Long?
    ) : SpeechRecognitionQuotaHint

    data class Exhausted(override val resetDateSeconds: Long?) : SpeechRecognitionQuotaHint
}

/** Premium accounts have no free-quota limit, so they never see a remaining-attempts hint. */
fun SpeechRecognitionTrial?.quotaHintFor(
    isPremiumAccount: Boolean,
    nowSeconds: Long
): SpeechRecognitionQuotaHint? = if (isPremiumAccount) null else this?.quotaHint(nowSeconds)

/** Why TDLib refused a recognition request, after friendly presentation rules are applied. */
enum class SpeechRecognitionFailureReason {
    QuotaExhausted,
    PremiumRequired,
    DurationTooLong,
    FloodWait,
    Network,
    SecretChat,
    Other
}

/**
 * A recognition failure that the UI can explain. Unlike a raw TDLib error, it exposes only a reason and
 * the server-provided quota facts; service codes never reach the screen.
 */
data class SpeechRecognitionFailure(
    val reason: SpeechRecognitionFailureReason,
    val resetDateSeconds: Long? = null,
    val maxMediaDurationSeconds: Int? = null,
    /** Free attempts Telegram had already reported when this refusal was created; null means no quota data. */
    val freeAttemptsAtFailure: Int? = null,
    /** Premium state when the refusal was created, so a later upgrade can invalidate only an older refusal. */
    val premiumAtFailure: Boolean = false,
    /** Usually derived from [reason]; a known permanent "other" refusal disables Retry explicitly. */
    val retryable: Boolean = reason == SpeechRecognitionFailureReason.FloodWait ||
        reason == SpeechRecognitionFailureReason.Network ||
        reason == SpeechRecognitionFailureReason.Other
) {
    /** Repeating the same request can only help for transient reasons. */
    val isRetryable: Boolean get() = retryable
}

/** Carries a structured failure through Result without exposing TDLib or raw error codes to the UI. */
class SpeechRecognitionException(
    val failure: SpeechRecognitionFailure,
    cause: Throwable? = null
) : RuntimeException("Speech recognition failed: ${failure.reason}", cause)
