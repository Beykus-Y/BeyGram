package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import kotlinx.coroutines.flow.StateFlow

/** A separate history window keeps distant replies from creating gaps in the main pagination. */
@Composable
internal fun MessageContextDialog(chatId: Long, targetId: Long, conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onClose: () -> Unit) {
    var context by remember(chatId, targetId) { mutableStateOf<MessageContext?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    val palette = beyGramPalette()
    val list = rememberLazyListState()
    LaunchedEffect(chatId, targetId, attempt) {
        context = null; error = null
        val result = conversation.messageContext(chatId, targetId)
        context = result.getOrNull(); error = result.exceptionOrNull()?.message
    }
    LaunchedEffect(context) {
        context?.messages?.indexOfFirst { it.id == targetId }?.takeIf { it >= 0 }?.let { list.scrollToItem(it) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().background(palette.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(context?.title ?: "Оригинал ответа", color = palette.textPrimary)
                    Text("Сообщение и контекст", color = palette.textSecondary)
                }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
            when {
                error != null -> Column(Modifier.padding(24.dp)) {
                    Text(error.orEmpty(), color = palette.warningText)
                    TextButton(onClick = { attempt++ }) { Text("Повторить") }
                }
                context == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(context!!.messages, key = { it.id }) { message ->
                        MessageBubble(message, observeFile, downloadFile, highlighted = message.id == targetId, onOpen = { selected = message })
                    }
                }
            }
        }
    }
    selected?.let { message -> message.media?.let { media -> MediaViewer(media, observeFile, downloadFile,
        onViewed = { conversation.openMessageContent(message.chatId, message.id) }, onClose = { selected = null }) } }
}
