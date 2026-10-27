package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.SpeechRecognitionFailure
import com.beykus.beygram.domain.model.SpeechRecognitionFailureReason
import com.beykus.beygram.domain.model.SpeechRecognitionQuotaHint
import com.beykus.beygram.domain.model.SpeechRecognitionTrial
import com.beykus.beygram.domain.model.TranslationPolicy
import com.beykus.beygram.domain.model.quotaHintFor
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette

/**
 * Account-level facts needed by the transcription section. A known quota only changes the hint under the
 * action; it never hides the action from a non-Premium account.
 */
internal data class SpeechRecognitionUi(
    val trial: SpeechRecognitionTrial? = null,
    val isPremiumAccount: Boolean = true,
    val onPremium: (() -> Unit)? = null
)

/**
 * Speech recognition state for a voice message or video note. The button is only rendered when [onRequest] is not null,
 * so secret chats and media that already have a result never expose it.
 */
@Composable
internal fun TranscriptionSection(
    media: MessageMedia,
    requestPending: Boolean,
    requestError: SpeechRecognitionFailure?,
    rated: Boolean,
    onRequest: (() -> Unit)?,
    onRate: ((Boolean) -> Unit)?,
    ui: SpeechRecognitionUi = SpeechRecognitionUi(),
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val serverPending = media.transcriptionPending
    val text = media.transcriptionText
    val serverFailure = media.transcriptionError
    val request = onRequest
    val rate = onRate
    val failure = when {
        text.isNotBlank() || serverPending != null -> null
        // A new local attempt owns the section until TDLib replaces the previous error result.
        requestPending -> null
        // A fresh synchronous refusal is newer than any server result that existed before the attempt.
        else -> requestError ?: serverFailure
    }
    // A quota error stops being terminal once Telegram reports new attempts or the account becomes Premium.
    val shownFailure = if (TranslationPolicy.isTranscriptionRefusalLifted(failure, ui.trial, ui.isPremiumAccount)) {
        null
    } else {
        failure
    }
    val pending = serverPending != null || (requestPending && text.isBlank() && shownFailure == null)
    // The hint is secondary to the action and is only useful while the action is still available.
    val quotaHint = if (request != null && text.isBlank() && serverPending == null && shownFailure == null) {
        ui.trial.quotaHintFor(ui.isPremiumAccount, System.currentTimeMillis() / 1000L)
    } else {
        null
    }
    when {
        pending -> Row(
            modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), color = palette.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                serverPending?.takeIf { it.isNotBlank() } ?: stringResource(R.string.transcription_pending),
                color = palette.textSecondary, fontSize = 13.sp
            )
        }
        text.isNotBlank() -> Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(text, color = palette.messageText, fontSize = 14.sp, lineHeight = 19.sp)
            if (rate != null && !rated) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { rate(true) }) {
                        Icon(Icons.Rounded.ThumbUp, stringResource(R.string.transcription_rate_good), tint = palette.textSecondary,
                            modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { rate(false) }) {
                        Icon(Icons.Rounded.ThumbDown, stringResource(R.string.transcription_rate_bad), tint = palette.textSecondary,
                            modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        shownFailure != null -> Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(speechRecognitionFailureText(shownFailure, ui.trial, ui.isPremiumAccount), color = palette.danger,
                fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (request != null && TranslationPolicy.canRetryTranscription(shownFailure)) {
                    TextButton(onClick = request) { Text(stringResource(R.string.common_retry), color = palette.accent) }
                }
                val premium = ui.onPremium
                if (premium != null && !ui.isPremiumAccount && shownFailure.reason.isQuotaOrPremium()) {
                    TextButton(onClick = premium) {
                        Text(stringResource(R.string.profile_badge_premium), color = palette.accent)
                    }
                }
            }
        }
        request != null -> Column(modifier) {
            TextButton(onClick = request, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Rounded.Subtitles, null, tint = palette.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.transcription_action), color = palette.accent, fontSize = 13.sp)
            }
            if (quotaHint != null) {
                Text(
                    speechRecognitionQuotaHintText(quotaHint),
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                )
                val premium = ui.onPremium
                if (premium != null && quotaHint is SpeechRecognitionQuotaHint.Exhausted) {
                    TextButton(onClick = premium) {
                        Text(stringResource(R.string.profile_badge_premium), color = palette.accent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun SpeechRecognitionFailureReason.isQuotaOrPremium(): Boolean =
    this == SpeechRecognitionFailureReason.QuotaExhausted || this == SpeechRecognitionFailureReason.PremiumRequired

@Composable
private fun speechRecognitionFailureText(
    failure: SpeechRecognitionFailure,
    trial: SpeechRecognitionTrial?,
    isPremiumAccount: Boolean
): String {
    val reset = failure.resetDateSeconds
        ?: trial?.nextResetDateSeconds?.takeIf { it > System.currentTimeMillis() / 1000L }
    return when (failure.reason) {
        SpeechRecognitionFailureReason.QuotaExhausted -> if (reset != null) {
            stringResource(R.string.err_transcription_quota_reset, formatTranscriptionResetDate(reset))
        } else {
            stringResource(R.string.err_transcription_quota)
        }
        SpeechRecognitionFailureReason.PremiumRequired -> stringResource(R.string.err_transcription_premium)
        SpeechRecognitionFailureReason.DurationTooLong -> {
            val maxDuration = if (isPremiumAccount) null else {
                failure.maxMediaDurationSeconds ?: trial?.maxMediaDurationSeconds?.takeIf { it > 0 }
            }
            if (maxDuration != null) {
                stringResource(R.string.err_transcription_too_long_limit, formatTranscriptionDuration(maxDuration))
            } else {
                stringResource(R.string.err_transcription_too_long)
            }
        }
        SpeechRecognitionFailureReason.FloodWait -> stringResource(R.string.err_tdlib_flood_wait)
        SpeechRecognitionFailureReason.Network -> stringResource(R.string.err_transcription_network)
        SpeechRecognitionFailureReason.SecretChat -> stringResource(R.string.err_transcription_secret_chat)
        SpeechRecognitionFailureReason.Other -> stringResource(R.string.err_transcription_failed)
    }
}

@Composable
private fun speechRecognitionQuotaHintText(hint: SpeechRecognitionQuotaHint): String {
    val reset = hint.resetDateSeconds
    return when (hint) {
        is SpeechRecognitionQuotaHint.Available -> if (reset != null) {
            stringResource(R.string.transcription_quota_left_reset, hint.left, hint.total,
                formatTranscriptionResetDate(reset))
        } else {
            stringResource(R.string.transcription_quota_left, hint.left, hint.total)
        }
        is SpeechRecognitionQuotaHint.Exhausted -> if (reset != null) {
            stringResource(R.string.transcription_quota_empty_reset, formatTranscriptionResetDate(reset))
        } else {
            stringResource(R.string.transcription_quota_empty)
        }
    }
}
