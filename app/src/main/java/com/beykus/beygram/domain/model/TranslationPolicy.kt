package com.beykus.beygram.domain.model

/**
 * Pure rules for translation and speech recognition. The UI uses these rules to hide actions; the repository repeats
 * the secret-chat check before calling TDLib so a future caller cannot bypass it.
 */
object TranslationPolicy {
    /**
     * Only text messages and media captions can be translated, and a secret chat is never allowed: TDLib would need to
     * send the message text to Telegram's translation service.
     */
    fun canTranslate(isSecretChat: Boolean, hasText: Boolean): Boolean = !isSecretChat && hasText

    /**
     * Secret chats never send voice content to the server-side recognition service. A known free quota does not
     * hide the action: Telegram may still allow a recognition (for example in a boosted supergroup), and the
     * server is the only authority on the remaining attempts.
     */
    fun canTranscribe(isSecretChat: Boolean, media: MessageMedia?): Boolean =
        !isSecretChat && media != null &&
            (media.kind == MediaKind.Voice || media.kind == MediaKind.VideoNote) &&
            !media.hasTranscription && !media.isTranscribing

    /**
     * Quota, Premium, duration and secret-chat refusals cannot be fixed by sending the same request again.
     * Flood wait, network and unknown failures may be transient, so those keep the Retry action.
     */
    fun canRetryTranscription(failure: SpeechRecognitionFailure?): Boolean = failure?.isRetryable == true

    /**
     * A stored quota/Premium/duration refusal must not hide the action after the account becomes eligible again.
     * Only a change since the refusal was created invalidates it: otherwise a fresh server refusal would be
     * suppressed by the very cached quota that preceded it. The next request remains the authoritative check.
     */
    fun isTranscriptionRefusalLifted(
        failure: SpeechRecognitionFailure?,
        trial: SpeechRecognitionTrial?,
        isPremiumAccount: Boolean
    ): Boolean {
        if (failure == null) return false
        return when (failure.reason) {
            SpeechRecognitionFailureReason.QuotaExhausted,
            SpeechRecognitionFailureReason.PremiumRequired -> {
                if (isPremiumAccount && !failure.premiumAtFailure) {
                    true
                } else {
                    val currentAttempts = trial?.takeIf { it.weeklyCount > 0 }?.leftCount ?: 0
                    currentAttempts > (failure.freeAttemptsAtFailure ?: 0)
                }
            }
            // A duration refusal tied to the free-account limit no longer applies after a Premium upgrade.
            SpeechRecognitionFailureReason.DurationTooLong -> {
                val freeDurationLimit = failure.maxMediaDurationSeconds
                    ?: trial?.maxMediaDurationSeconds?.takeIf { it > 0 }
                isPremiumAccount && !failure.premiumAtFailure && freeDurationLimit != null
            }
            else -> false
        }
    }
}
