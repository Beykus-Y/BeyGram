package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.List
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
import kotlinx.coroutines.launch

/** Which pin is relevant to the message window currently visible in chat history. */
internal fun currentPinnedIndex(pins: List<ChatMessage>, visibleOldestMessageId: Long?): Int {
    if (pins.isEmpty()) return -1
    if (visibleOldestMessageId == null || visibleOldestMessageId <= 0L) return 0
    // Telegram's message IDs grow with time. Prefer the closest pinned message at/before the viewport.
    return pins.indexOfFirst { it.id <= visibleOldestMessageId }.takeIf { it >= 0 } ?: pins.lastIndex
}

/**
 * True when the viewport is older than every loaded pin, so the pin it needs is on a page that is not loaded yet.
 * Pins are listed newest first, so the next page holds older pins only.
 */
internal fun needsOlderPinnedPage(pins: List<ChatMessage>, visibleOldestMessageId: Long?): Boolean =
    pins.isNotEmpty() && visibleOldestMessageId != null && visibleOldestMessageId > 0L && pins.none { it.id <= visibleOldestMessageId }

/**
 * A pinned bar is a link to the shown message, not a 'next pin' button.
 * The shown preview follows the history viewport; tapping it jumps to that message. The list button shows every pin.
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
    var nextId by remember(chatId) { mutableLongStateOf(0L) }
    var loading by remember(chatId) { mutableStateOf(false) }
    var failed by remember(chatId) { mutableStateOf(false) }
    var retry by remember(chatId) { mutableIntStateOf(0) }
    var expanded by remember(chatId) { mutableStateOf(false) }
    // A page requested before the pins were reloaded must not be added to the new list.
    var generation by remember(chatId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val palette = beyGramPalette()

    LaunchedEffect(chatId, version, connected, retry) {
        generation++
        val requestedGeneration = generation
        loading = true
        failed = false
        try {
            conversation.pinnedMessages(chatId).onSuccess { page ->
                pins = page.messages.distinctBy(ChatMessage::id).sortedByDescending(ChatMessage::id)
                total = page.totalCount
                nextId = page.nextFromMessageId
            }.onFailure { failed = true }
        } finally { if (generation == requestedGeneration) loading = false }
    }
    // Adds the next, older page. A call while another page loads, or with no page left, does nothing.
    suspend fun loadMore() {
        if (loading || nextId == 0L) return
        val requestedId = nextId
        val requestedGeneration = generation
        loading = true
        failed = false
        try {
            conversation.pinnedMessages(chatId, requestedId).onSuccess { page ->
                if (generation == requestedGeneration) {
                    pins = (pins + page.messages).distinctBy(ChatMessage::id).sortedByDescending(ChatMessage::id)
                    total = page.totalCount
                    nextId = if (page.nextFromMessageId == requestedId) 0L else page.nextFromMessageId
                }
            }.onFailure { if (generation == requestedGeneration) failed = true }
        } finally { if (generation == requestedGeneration) loading = false }
    }
    // The shown pin follows the viewport, so an older viewport needs the page that holds its pin.
    val needsOlder = needsOlderPinnedPage(pins, visibleOldestMessageId)
    LaunchedEffect(needsOlder, nextId) {
        if (needsOlder) scope.launch { loadMore() }
    }
    val index = currentPinnedIndex(pins, visibleOldestMessageId)
    val shown = pins.getOrNull(index)
    if (needsOlder && failed) {
        TextButton(onClick = { scope.launch { loadMore() } }) { Text(stringResource(R.string.chat_pinned_failed) + " · " + stringResource(R.string.chat_pinned_retry)) }
    } else if (shown != null) {
        Row(Modifier.fillMaxWidth().background(palette.surface), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { onOpen(shown) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PushPin, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_pinned_position, index + 1, total.coerceAtLeast(pins.size)),
                        color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(pinnedPreview(shown), color = palette.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.List, stringResource(R.string.chat_pinned_list), tint = palette.accent) }
        }
        HorizontalDivider(color = palette.divider)
    } else if (failed) {
        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_pinned_retry)) }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.chat_pinned_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).weight(1f, fill = false)) {
                    items(pins, key = { it.id }) { message ->
                        Column(Modifier.fillMaxWidth().clickable { expanded = false; onOpen(message) }.padding(vertical = 10.dp)) {
                            if (message.senderName.isNotBlank()) Text(message.senderName, color = palette.accentInk, fontSize = 13.sp)
                            Text(pinnedPreview(message), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(message.time, color = palette.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.chat_pinned_failed), color = MaterialTheme.colorScheme.error)
                if (!loading && (nextId != 0L || failed)) TextButton(onClick = { scope.launch { loadMore() } }) {
                    Text(stringResource(if (failed) R.string.chat_pinned_retry else R.string.chat_pinned_more))
                }
            }
        },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.common_close)) } }
    )
}

@Composable
private fun pinnedPreview(message: ChatMessage): String {
    val context = LocalContext.current
    return message.text.ifBlank { message.media?.kind?.label(context) ?: stringResource(R.string.chat_pinned_message) }
}
