package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.List
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

/**
 * Pins are listed newest first. The bar shows the pin the next tap opens. Each tap moves on to the next, older pin,
 * and after the oldest one the cycle starts again from the newest.
 */
@Composable
internal fun PinnedMessagesBar(chatId: Long, conversation: ConversationRepository, connected: Boolean, onOpen: (ChatMessage) -> Unit) {
    val version by remember(conversation, chatId) { conversation.observePinnedMessagesVersion(chatId) }.collectAsStateWithLifecycle()
    var messages by remember(chatId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var total by remember(chatId) { mutableIntStateOf(0) }
    var nextId by remember(chatId) { mutableLongStateOf(0L) }
    var loading by remember(chatId) { mutableStateOf(false) }
    var failed by remember(chatId) { mutableStateOf(false) }
    var attempt by remember(chatId) { mutableIntStateOf(0) }
    var expanded by remember(chatId) { mutableStateOf(false) }
    // Index into [messages] of the pin the bar shows. It can be one past the loaded pins; the next page loads then.
    var cursor by remember(chatId) { mutableIntStateOf(0) }
    // Reject an older pagination response if pins changed while the request was in flight.
    var generation by remember(chatId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val palette = beyGramPalette()
    LaunchedEffect(chatId, version, connected, attempt) {
        generation++
        loading = true
        failed = false
        try {
            conversation.pinnedMessages(chatId).onSuccess {
                messages = it.messages.distinctBy(ChatMessage::id)
                total = it.totalCount
                nextId = it.nextFromMessageId
                cursor = 0
            }.onFailure { failed = true }
        } finally { loading = false }
    }
    suspend fun loadMore() {
        if (loading || nextId == 0L) return
        val requestedId = nextId
        val requestedGeneration = generation
        loading = true
        failed = false
        try {
            conversation.pinnedMessages(chatId, requestedId).onSuccess {
                if (generation == requestedGeneration) {
                    messages = (messages + it.messages).distinctBy(ChatMessage::id)
                    total = it.totalCount
                    nextId = if (it.nextFromMessageId == requestedId) 0L else it.nextFromMessageId
                }
            }.onFailure { if (generation == requestedGeneration) failed = true }
        } finally { if (generation == requestedGeneration) loading = false }
    }
    fun openPinned(index: Int) {
        val target = messages.getOrNull(index) ?: return
        cursor = index + 1
        onOpen(target)
    }
    // The cycle has run past the loaded pins: load the next page, or start again from the newest pin when there is none.
    // While a page is still loading, the cycle waits for it; the arriving page changes the keys and resumes the effect.
    LaunchedEffect(cursor, messages.size, nextId) {
        if (cursor < messages.size) return@LaunchedEffect
        if (nextId != 0L) loadMore()
        if (cursor >= messages.size && !loading) cursor = 0
    }
    val shownIndex = cursor.takeIf { it < messages.size } ?: 0
    val shown = messages.getOrNull(shownIndex)
    if (shown != null) {
        Row(Modifier.fillMaxWidth().background(palette.surface), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { openPinned(shownIndex) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PushPin, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.chat_pinned_position, shownIndex + 1, total.coerceAtLeast(messages.size)), color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(pinnedPreview(shown), color = palette.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.List, stringResource(R.string.chat_pinned_list), tint = palette.accent) }
        }
        HorizontalDivider(color = palette.divider)
    } else if (failed) {
        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.chat_pinned_failed) + " · " + stringResource(R.string.chat_pinned_retry)) }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.chat_pinned_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).weight(1f, fill = false)) {
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        Column(Modifier.fillMaxWidth().clickable { expanded = false; openPinned(index) }.padding(vertical = 10.dp)) {
                            if (message.senderName.isNotBlank()) Text(message.senderName, color = palette.accentInk, fontSize = 13.sp)
                            Text(pinnedPreview(message), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(message.time, color = palette.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.chat_pinned_failed), color = palette.warningText)
                if (!loading && (nextId != 0L || failed)) TextButton(onClick = { if (failed) attempt++ else scope.launch { loadMore() } }) {
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
