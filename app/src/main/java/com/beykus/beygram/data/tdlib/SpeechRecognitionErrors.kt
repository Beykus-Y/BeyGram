package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.SpeechRecognitionFailure
import com.beykus.beygram.domain.model.SpeechRecognitionFailureReason
import com.beykus.beygram.domain.model.SpeechRecognitionTrial

/**
 * Maps TDLib's recognition refusals to reasons the UI can explain. Both the immediate RecognizeSpeech
 * response and the asynchronous SpeechRecognitionResultError use this function, so the two channels
 * never disagree about what the user should do next.
 */
internal fun classifySpeechRecognitionError(
    code: Int?,
    message: String?,
    trial: SpeechRecognitionTrial?,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
    isPremiumAccount: Boolean = false,
    snapshotEligibility: Boolean = true
): SpeechRecognitionFailure {
    val text = message.orEmpty()
    val reset = trial?.nextResetDateSeconds?.takeIf { it > nowSeconds }
    val premiumAtFailure = snapshotEligibility && isPremiumAccount
    return when {
        code == 429 || text.contains("FLOOD_WAIT") ->
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.FloodWait)
        text.contains("MSG_VOICE_TOO_LONG") ->
            SpeechRecognitionFailure(
                SpeechRecognitionFailureReason.DurationTooLong,
                maxMediaDurationSeconds = trial?.maxMediaDurationSeconds?.takeIf { it > 0 },
                premiumAtFailure = premiumAtFailure
            )
        text.contains("PREMIUM_ACCOUNT_REQUIRED") ->
            // TDLib uses this refusal both for an exhausted free quota and for a Premium-only account.
            // Only Telegram's left_count can tell them apart; without it, prefer the honest Premium reason.
            if (trial != null && trial.leftCount <= 0) {
                SpeechRecognitionFailure(
                    SpeechRecognitionFailureReason.QuotaExhausted,
                    resetDateSeconds = reset,
                    freeAttemptsAtFailure = if (snapshotEligibility) trial.freeAttempts() else null,
                    premiumAtFailure = premiumAtFailure
                )
            } else {
                SpeechRecognitionFailure(
                    SpeechRecognitionFailureReason.PremiumRequired,
                    freeAttemptsAtFailure = if (snapshotEligibility) trial?.freeAttempts() else null,
                    premiumAtFailure = premiumAtFailure
                )
            }
        text.contains("SECRET", ignoreCase = true) || text.contains("CHAT_TYPE_INVALID") ->
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.SecretChat)
        isNetworkFailure(code, text) ->
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.Network)
        else -> SpeechRecognitionFailure(
            SpeechRecognitionFailureReason.Other,
            retryable = !text.isPermanentRecognitionRefusal()
        )
    }
}

private fun String.isPermanentRecognitionRefusal(): Boolean =
    contains("Message can't be transcribed", ignoreCase = true) ||
        contains("Message not found", ignoreCase = true) ||
        contains("MESSAGE_ID_INVALID")

/** Telegram's free-attempt count only matters when a free weekly quota actually exists. */
private fun SpeechRecognitionTrial.freeAttempts(): Int? =
    if (weeklyCount > 0) leftCount else null

private fun isNetworkFailure(code: Int?, message: String): Boolean {
    if (code == 502 || code == 503 || code == 504) return true
    if (code != null && code != 0 && code != 500) return false
    return listOf("connection", "network", "timeout", "request aborted", "unavailable")
        .any { message.contains(it, ignoreCase = true) }
}
