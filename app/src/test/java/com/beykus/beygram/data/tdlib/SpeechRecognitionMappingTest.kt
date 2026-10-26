package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.SpeechRecognitionException
import com.beykus.beygram.domain.model.SpeechRecognitionFailure
import com.beykus.beygram.domain.model.SpeechRecognitionFailureReason
import com.beykus.beygram.domain.model.SpeechRecognitionTrial
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechRecognitionMappingTest {
    private val trial = SpeechRecognitionTrial(weeklyCount = 3, leftCount = 0,
        maxMediaDurationSeconds = 60, nextResetDateSeconds = 4_000L)

    @Test
    fun `sync refusals map to terminal and retryable reasons`() {
        assertEquals(SpeechRecognitionFailure(
            SpeechRecognitionFailureReason.QuotaExhausted, resetDateSeconds = 4_000L,
            freeAttemptsAtFailure = 0),
            classifySpeechRecognitionError(400, "PREMIUM_ACCOUNT_REQUIRED", trial, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.PremiumRequired),
            classifySpeechRecognitionError(400, "PREMIUM_ACCOUNT_REQUIRED", null, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.PremiumRequired,
            freeAttemptsAtFailure = 2),
            classifySpeechRecognitionError(400, "PREMIUM_ACCOUNT_REQUIRED", trial.copy(leftCount = 2),
                nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(
            SpeechRecognitionFailureReason.DurationTooLong, maxMediaDurationSeconds = 60),
            classifySpeechRecognitionError(400, "MSG_VOICE_TOO_LONG", trial, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(
            SpeechRecognitionFailureReason.DurationTooLong, maxMediaDurationSeconds = 60,
            premiumAtFailure = true),
            classifySpeechRecognitionError(400, "MSG_VOICE_TOO_LONG", trial, nowSeconds = 1_000L,
                isPremiumAccount = true))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.FloodWait),
            classifySpeechRecognitionError(429, "FLOOD_WAIT_12", trial, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.FloodWait),
            classifySpeechRecognitionError(400, "FLOOD_WAIT_12", trial, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.Network),
            classifySpeechRecognitionError(500, "Request aborted", trial, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.SecretChat),
            classifySpeechRecognitionError(400, "Chat is secret", trial, nowSeconds = 1_000L))
        assertEquals(SpeechRecognitionFailure(SpeechRecognitionFailureReason.Other),
            classifySpeechRecognitionError(400, "SPEECH_RECOGNITION_FAILED", trial, nowSeconds = 1_000L))
        val permanent = classifySpeechRecognitionError(400, "Message can't be transcribed", trial, nowSeconds = 1_000L)
        assertEquals(SpeechRecognitionFailureReason.Other, permanent.reason)
        assertFalse(permanent.isRetryable)
    }

    @Test
    fun `sync request failure is carried as a structured exception`() = runBlocking {
        val result = speechRecognitionResult({ trial.copy(nextResetDateSeconds = 2_000_000_000L) }) {
            throw TdRequestError(400, "PREMIUM_ACCOUNT_REQUIRED")
        }
        val failure = (result.exceptionOrNull() as SpeechRecognitionException).failure
        assertEquals(SpeechRecognitionFailureReason.QuotaExhausted, failure.reason)
        assertEquals(2_000_000_000L, failure.resetDateSeconds)
    }

    @Test
    fun `async result error uses the same quota and duration mapping`() {
        val quota = TdApi.SpeechRecognitionResultError(TdApi.Error(400, "PREMIUM_ACCOUNT_REQUIRED"))
            .toTranscriptionSnapshot(trial, nowSeconds = 1_000L)
        assertEquals(TranscriptionSnapshot(error = SpeechRecognitionFailure(
            SpeechRecognitionFailureReason.QuotaExhausted, resetDateSeconds = 4_000L,
            freeAttemptsAtFailure = 0)), quota)

        val tooLong = TdApi.SpeechRecognitionResultError(TdApi.Error(400, "MSG_VOICE_TOO_LONG"))
            .toTranscriptionSnapshot(trial, nowSeconds = 1_000L)
        assertEquals(TranscriptionSnapshot(error = SpeechRecognitionFailure(
            SpeechRecognitionFailureReason.DurationTooLong, maxMediaDurationSeconds = 60)), tooLong)

        val flood = TdApi.SpeechRecognitionResultError(TdApi.Error(429, "FLOOD_WAIT_5"))
            .toTranscriptionSnapshot(trial, nowSeconds = 1_000L)
        assertEquals(SpeechRecognitionFailureReason.FloodWait, flood.error?.reason)
    }

    @Test
    fun `voice media mapper keeps the structured asynchronous failure`() {
        val file = TdApi.File().apply {
            id = 7
            size = 10
            local = TdApi.LocalFile()
            remote = TdApi.RemoteFile()
        }
        val voice = TdApi.VoiceNote(12, byteArrayOf(), "audio/ogg",
            TdApi.SpeechRecognitionResultError(TdApi.Error(400, "PREMIUM_ACCOUNT_REQUIRED")), file)
        val futureTrial = trial.copy(nextResetDateSeconds = 2_000_000_000L)
        val content = TdApi.MessageVoiceNote().apply { voiceNote = voice }
        val mapper = TdMediaMapper(MediaFileStore(), { futureTrial })
        val media = mapper.media(content, freshSpeechError = true)!!
        assertEquals(SpeechRecognitionFailureReason.QuotaExhausted, media.transcriptionError?.reason)
        assertEquals(2_000_000_000L, media.transcriptionError?.resetDateSeconds)
        assertEquals(0, media.transcriptionError?.freeAttemptsAtFailure ?: -1)

        // An unchanged result from history keeps the original snapshot instead of restamping eligibility.
        val reloaded = mapper.media(content)!!
        assertEquals(media.transcriptionError, reloaded.transcriptionError)

        // With no live snapshot available, history must not invent the current account eligibility.
        val historical = TdMediaMapper(MediaFileStore(), { futureTrial }).media(content)!!
        assertEquals(SpeechRecognitionFailureReason.QuotaExhausted, historical.transcriptionError?.reason)
        assertNull(historical.transcriptionError?.freeAttemptsAtFailure)
        assertFalse(historical.transcriptionError!!.premiumAtFailure)
    }

    @Test
    fun `quota state is isolated per repository account and cleared on logout`() {
        val first = SpeechRecognitionTrialStore()
        val second = SpeechRecognitionTrialStore()
        first.update(TdApi.UpdateSpeechRecognitionTrial(60, 3, 2, 4_000))
        assertEquals(SpeechRecognitionTrial(3, 2, 60, 4_000L), first.state.value)
        assertNull(second.state.value)

        first.clear()
        assertNull(first.state.value)
        assertNull(second.state.value)
    }
}
