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
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.resolve

/**
 * Speech recognition state for a voice message or video note. The button is only rendered when [onRequest] is not null,
 * so secret chats and media that already have a result never expose it.
 */
@Composable
internal fun TranscriptionSection(
    media: MessageMedia,
    requestPending: Boolean,
    requestError: String?,
    rated: Boolean,
    onRequest: (() -> Unit)?,
    onRate: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    val serverPending = media.transcriptionPending
    val text = media.transcriptionText
    val serverError = media.transcriptionError?.resolve()
    val pending = serverPending != null || (requestPending && text.isBlank() && serverError == null)
    val request = onRequest
    val rate = onRate
    val error = when {
        text.isNotBlank() || serverPending != null -> null
        serverError != null -> serverError
        else -> requestError
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
        error != null -> Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(error, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
            if (request != null) TextButton(onClick = request) { Text(stringResource(R.string.common_retry), color = palette.accent) }
        }
        request != null -> TextButton(onClick = request, modifier = modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Rounded.Subtitles, null, tint = palette.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.transcription_action), color = palette.accent, fontSize = 13.sp)
        }
    }
}
