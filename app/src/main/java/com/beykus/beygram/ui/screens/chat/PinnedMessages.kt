package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette

/** Which pin is relevant to the message window currently visible in chat history. */
internal fun currentPinnedIndex(pins: List<ChatMessage>, visibleOldestMessageId: Long?): Int {
    if (pins.isEmpty()) return -1
    if (visibleOldestMessageId == null || visibleOldestMessageId <= 0L) return 0
    // Telegram's message IDs grow with time. Prefer the closest pinned message at/before the viewport.
    return pins.indexOfFirst { it.id <= visibleOldestMessageId }.takeIf { it >= 0 } ?: pins.lastIndex
}

/**
 * A pinned bar is a link to the shown message, not a 'next pin' button.
 * The shown preview follows the history viewport; tapping it jumps to that message.
 */
@Composable
internal fun PinnedMessagesBar(
    chatId: Long,
    conversation: ConversationRepository,
    connected: Boolean,
    onOpen: (ChatMessage) -> Unit,
    visibleOldestMessageId: Long? = null
) {
    val version by remember(conversation, chatId) { conversation.observePinnedMessagesVersion(chatId) }.collectAsStateWithLifecycle()
    var pins by remember(chatId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var total by remember(chatId) { mutableIntStateOf(0) }
    var loading by remember(chatId) { mutableStateOf(false) }
    var failed by remember(chatId) { mutableStateOf(false) }
    var retry by remember(chatId) { mutableIntStateOf(0) }
    val palette = beyGramPalette()

    LaunchedEffect(chatId, version, connected, retry) {
        loading = true
        failed = false
        conversation.pinnedMessages(chatId).onSuccess { page ->
            pins = page.messages.distinctBy(ChatMessage::id).sortedByDescending(ChatMessage::id)
            total = page.totalCount
        }.onFailure { failed = true }
        loading = false
    }
    val index = currentPinnedIndex(pins, visibleOldestMessageId)
    val shown = pins.getOrNull(index)
    if (shown != null) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).clickable { onOpen(shown) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.PushPin, null, tint = palette.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_pinned_position, index + 1, total.coerceAtLeast(pins.size)),
                    color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(pinnedPreview(shown), color = palette.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        HorizontalDivider(color = palette.divider)
    } else if (failed) {
        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_pinned_retry)) }
    }
}

@Composable
private fun pinnedPreview(message: ChatMessage): String {
    val context = LocalContext.current
    return message.text.ifBlank { message.media?.kind?.label(context) ?: stringResource(R.string.chat_pinned_message) }
}
