package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance

@Stable
internal class ChatQuoteState {
    private data class Quote(val messageId: Long, val text: String, val position: Int)
    private var quote by mutableStateOf<Quote?>(null)
    var pickerTarget by mutableStateOf<ChatMessage?>(null)

    fun select(messageId: Long, text: String, position: Int) {
        quote = Quote(messageId, text, position)
    }

    fun textFor(replyId: Long?): String? = quote?.takeIf { it.messageId == replyId }?.text

    fun keepOnlyFor(replyId: Long?) {
        if (quote?.messageId != replyId) clear()
    }

    fun clear() {
        quote = null
    }

    fun consumeInto(options: MessageSendOptions, replyId: Long?): MessageSendOptions {
        val current = quote
        clear()
        if (current == null || current.messageId != replyId) return options
        return options.copy(replyQuote = current.text, replyQuotePosition = current.position)
    }
}

@Composable
internal fun ChatQuoteHost(
    state: ChatQuoteState,
    onApplyQuote: (messageId: Long) -> Unit
) {
    val target = state.pickerTarget ?: return
    ChatQuoteSheet(
        sourceText = target.text,
        onQuoteSelected = { q, pos ->
            state.select(target.id, q, pos)
            state.pickerTarget = null
            onApplyQuote(target.id)
        },
        onDismiss = { state.pickerTarget = null }
    )
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
            Text(previewText, color = palette.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        }
        IconButton(enabled = !sending, onClick = onClear) {
            Icon(Icons.Rounded.Close, stringResource(R.string.chat_cancel_reply_description), tint = palette.textSecondary)
        }
    }
}

/** Splits a message into clean selectable phrase/sentence chips for 1-tap quoting. */
internal fun extractQuoteCandidates(fullText: String): List<String> {
    val trimmed = fullText.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parts = trimmed
        .split(Regex("""(?<=[.!?])\s+|\n+"""))
        .map { it.trim() }
        .filter { it.length >= 2 }
    return if (parts.size <= 1) listOf(trimmed) else parts.take(8)
}

/** Finds an exact quote's UTF-16 offset in the source message. */
internal fun quoteOffsetIn(source: String, quote: String): Int? =
    quote.takeUnless(String::isBlank)?.let { source.indexOf(it).takeIf { offset -> offset >= 0 } }

/**
 * Sheet for picking a specific fragment of a message to quote-reply via TDLib's InputTextQuote.
 * Offers 1-tap sentence/line chips plus direct trimming so the user can quote any exact phrase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatQuoteSheet(
    sourceText: String,
    onQuoteSelected: (quote: String, position: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = beyGramPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val candidates = remember(sourceText) { extractQuoteCandidates(sourceText) }
    var quote by remember(sourceText) { mutableStateOf(candidates.firstOrNull() ?: sourceText.trim()) }
    val selectedText = quote.trim()
    val selectedOffset = quoteOffsetIn(sourceText, selectedText)

    BeyGramSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrollableContent = false,
        containerColor = palette.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_quote_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (candidates.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    candidates.forEach { snippet ->
                        val selected = quote == snippet
                        val shape = styledShape(14.dp)
                        Box(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(shape)
                                .background(if (selected) palette.accent else palette.surface2, shape)
                                .border(1.dp, if (selected) palette.accent else palette.divider, shape)
                                .clickable(role = Role.Button) { quote = snippet }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = snippet,
                                color = if (selected) palette.onAccent else palette.textPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = quote,
                onValueChange = { quote = it.take(1024) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                label = { Text(stringResource(R.string.chat_quote_hint)) }
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel), color = palette.textSecondary)
                }
                TextButton(
                    onClick = {
                        if (selectedOffset != null) onQuoteSelected(selectedText, selectedOffset)
                    },
                    enabled = selectedOffset != null
                ) {
                    Text(stringResource(R.string.chat_quote_action), color = palette.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
