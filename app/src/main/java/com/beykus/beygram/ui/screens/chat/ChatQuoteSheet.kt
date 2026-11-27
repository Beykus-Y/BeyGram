package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.emojiFontText
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance

@Stable
internal class ChatQuoteState {
    private data class Quote(
        val messageId: Long,
        val text: String,
        val position: Int,
        val restoreAfterRecreation: Boolean,
        val entities: List<MessageTextEntity>
    )
    private var quote by mutableStateOf<Quote?>(null)

    fun select(
        messageId: Long,
        text: String,
        position: Int,
        restoreAfterRecreation: Boolean = false,
        entities: List<MessageTextEntity> = emptyList()
    ) {
        quote = Quote(messageId, text, position, restoreAfterRecreation, entities)
    }

    fun textFor(replyId: Long?): String? = quote?.takeIf { it.messageId == replyId }?.text

    fun keepOnlyFor(replyId: Long?) {
        if (quote?.messageId != replyId) clear()
    }

    fun clear() {
        quote = null
    }

    fun applyTo(options: MessageSendOptions, replyId: Long?): MessageSendOptions {
        val current = quote
        if (current == null || current.messageId != replyId) return options
        return options.copy(replyQuote = current.text, replyQuotePosition = current.position, replyQuoteEntities = current.entities)
    }

    companion object {
        val Saver = listSaver<ChatQuoteState, Any>(
            save = { state ->
                val eligible = state.quote?.takeIf { it.restoreAfterRecreation }
                if (eligible == null) emptyList() else listOf(eligible.messageId, eligible.text, eligible.position, ArrayList(eligible.entities))
            },
            restore = { saved ->
                ChatQuoteState().apply {
                    val messageId = saved.getOrNull(0) as? Long
                    val text = saved.getOrNull(1) as? String
                    val position = saved.getOrNull(2) as? Int
                    if (messageId != null && text != null && position != null) {
                        val entities = (saved.getOrNull(3) as? List<*>)?.filterIsInstance<MessageTextEntity>().orEmpty()
                        select(messageId, text, position, restoreAfterRecreation = true, entities = entities)
                    }
                }
            }
        )
    }
}

@Composable
internal fun ChatReplyHeaderStrip(
    senderLabel: String,
    previewText: String,
    chatGlass: Boolean,
    sending: Boolean,
    onClear: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier
            .fillMaxWidth()
            .motionEntrance(MotionToken.BannerChange, 8.dp)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(styledShape(12.dp))
                .then(if (chatGlass) Modifier.background(palette.surface.copy(alpha = 0.35f)) else Modifier)
                .border(1.dp, palette.accent.copy(alpha = 0.45f), styledShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(senderLabel, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(emojiFontText(previewText), color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        }
        IconButton(enabled = !sending, onClick = onClear) {
            Icon(Icons.Rounded.Close, stringResource(R.string.chat_cancel_reply_description), tint = palette.textSecondary)
        }
    }
}
