package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.ui.components.beyGramPalette

/** The origin is separate from the sender of this copy; unresolved or hidden metadata never impersonates either. */
@Composable
internal fun ForwardedMessageHeader(message: ChatMessage) {
    if (!message.isForwarded) return
    val label = if (message.forwardedFromName.isNotBlank()) {
        stringResource(R.string.chat_forwarded_from, message.forwardedFromName)
    } else {
        stringResource(R.string.chat_message_forwarded)
    }
    val palette = beyGramPalette()
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val textColor = message.forwardedAccentColor?.authorColor(dark, palette.surface, palette.accentInk) ?: palette.accentInk
    Text(
        text = label,
        color = textColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = LocalBubbleMetrics.current.textInset).padding(top = 4.dp),
    )
}
