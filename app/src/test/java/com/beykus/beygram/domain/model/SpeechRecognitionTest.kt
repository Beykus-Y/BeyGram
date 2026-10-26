package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionTest {
    private fun media(kind: MediaKind = MediaKind.Voice, text: String = "", pending: String? = null) =
        MessageMedia(kind, 1, transcriptionText = text, transcriptionPending = pending)

    @Test
    fun `known quota reports left attempts and only a future reset date`() {
        val trial = SpeechRecognitionTrial(weeklyCount = 3, leftCount = 2, maxMediaDurationSeconds = 60,
            nextResetDateSeconds = 2_000L)
        assertEquals(SpeechRecognitionQuotaHint.Available(2, 3, 2_000L), trial.quotaHint(nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionQuotaHint.Available(2, 3, null),
            trial.copy(nextResetDateSeconds = 500L).quotaHint(nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionQuotaHint.Available(2, 3, null),
            trial.copy(nextResetDateSeconds = null).quotaHint(nowSeconds = 1_000L))
    }

    @Test
    fun `zero left attempts is an exhausted quota not missing data`() {
        val trial = SpeechRecognitionTrial(weeklyCount = 4, leftCount = 0, maxMediaDurationSeconds = 30,
            nextResetDateSeconds = 3_000L)
        assertEquals(SpeechRecognitionQuotaHint.Exhausted(3_000L), trial.quotaHint(nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionQuotaHint.Exhausted(null),
            trial.copy(nextResetDateSeconds = 0L).quotaHint(nowSeconds = 1_000L))
    }

    @Test
    fun `quota hint never invents data when Telegram reports no free quota`() {
        assertNull(SpeechRecognitionTrial(0, 0, 0, null).quotaHint(nowSeconds = 1_000L))
        val hint = SpeechRecognitionTrial(5, 3, 0, null).quotaHint(nowSeconds = 1_000L)
        assertEquals(SpeechRecognitionQuotaHint.Available(3, 5, null), hint)
    }

    @Test
    fun `premium accounts never see a free quota hint`() {
        val trial = SpeechRecognitionTrial(3, 2, 60, 2_000L)
        assertEquals(SpeechRecognitionQuotaHint.Available(2, 3, 2_000L),
            trial.quotaHintFor(isPremiumAccount = false, nowSeconds = 1_000L))
        assertNull(trial.quotaHintFor(isPremiumAccount = true, nowSeconds = 1_000L))
    }

    @Test
    fun `a quota refusal becomes stale only after eligibility actually grows`() {
        val quota = SpeechRecognitionFailure(SpeechRecognitionFailureReason.QuotaExhausted)
        val available = SpeechRecognitionTrial(weeklyCount = 3, leftCount = 2, maxMediaDurationSeconds = 60)
        assertTrue(TranslationPolicy.isTranscriptionRefusalLifted(quota, available, isPremiumAccount = false))
        assertFalse(TranslationPolicy.isTranscriptionRefusalLifted(quota,
            available.copy(leftCount = 0), isPremiumAccount = false))
        // A fresh refusal made while the cached quota still has attempts must not be hidden immediately.
        assertFalse(TranslationPolicy.isTranscriptionRefusalLifted(
            quota.copy(freeAttemptsAtFailure = 2), available, isPremiumAccount = false))
        assertTrue(TranslationPolicy.isTranscriptionRefusalLifted(
            quota.copy(freeAttemptsAtFailure = 0), available, isPremiumAccount = false))
        assertTrue(TranslationPolicy.isTranscriptionRefusalLifted(
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.PremiumRequired),
            available.copy(leftCount = 0), isPremiumAccount = true))
        // A free-duration refusal stops applying after a Premium upgrade.
        assertTrue(TranslationPolicy.isTranscriptionRefusalLifted(
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.DurationTooLong, maxMediaDurationSeconds = 60),
            available, isPremiumAccount = true))
        assertFalse(TranslationPolicy.isTranscriptionRefusalLifted(
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.DurationTooLong, maxMediaDurationSeconds = 60),
            available, isPremiumAccount = false))
        // A new duration refusal created *after* the upgrade is not lifted again.
        assertFalse(TranslationPolicy.isTranscriptionRefusalLifted(
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.DurationTooLong,
                maxMediaDurationSeconds = 60, premiumAtFailure = true),
            available, isPremiumAccount = true))
        assertFalse(TranslationPolicy.isTranscriptionRefusalLifted(
            SpeechRecognitionFailure(SpeechRecognitionFailureReason.Network), available, isPremiumAccount = false))
        assertFalse(TranslationPolicy.isTranscriptionRefusalLifted(null, available, isPremiumAccount = true))
    }

    @Test
    fun `quota and premium refusals are terminal while flood network and unknown keep retry`() {
        listOf(
            SpeechRecognitionFailureReason.QuotaExhausted,
            SpeechRecognitionFailureReason.PremiumRequired,
            SpeechRecognitionFailureReason.DurationTooLong,
            SpeechRecognitionFailureReason.SecretChat
        ).forEach { reason ->
            assertFalse(reason.name, SpeechRecognitionFailure(reason).isRetryable)
            assertFalse(TranslationPolicy.canRetryTranscription(SpeechRecognitionFailure(reason)))
        }
        listOf(
            SpeechRecognitionFailureReason.FloodWait,
            SpeechRecognitionFailureReason.Network,
            SpeechRecognitionFailureReason.Other
        ).forEach { reason ->
            assertTrue(reason.name, SpeechRecognitionFailure(reason).isRetryable)
            assertTrue(TranslationPolicy.canRetryTranscription(SpeechRecognitionFailure(reason)))
        }
        assertFalse(TranslationPolicy.canRetryTranscription(null))
    }

    @Test
    fun `free quota never hides the transcribe action and secret chats stay excluded`() {
        // A non-Premium account with an exhausted trial still sees the action: only the server can refuse it.
        assertTrue(TranslationPolicy.canTranscribe(isSecretChat = false, media = media()))
        assertTrue(TranslationPolicy.canTranscribe(isSecretChat = false, media = media(MediaKind.VideoNote)))
        assertFalse(TranslationPolicy.canTranscribe(isSecretChat = true, media = media()))
        assertFalse(TranslationPolicy.canTranscribe(isSecretChat = false, media = media(text = "already transcribed")))
        assertFalse(TranslationPolicy.canTranscribe(isSecretChat = false, media = media(pending = "partial")))
        assertFalse(TranslationPolicy.canTranscribe(isSecretChat = false, media = null))
    }
}
