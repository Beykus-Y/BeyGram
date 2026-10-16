package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import kotlinx.coroutines.launch

@Composable
internal fun MessageActionsDialog(message: ChatMessage, conversation: ConversationRepository, onClose: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit) {
    var capabilities by remember(message.id) { mutableStateOf<MessageCapabilities?>(null) }
    var error by remember(message.id) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var everyone by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    LaunchedEffect(message.chatId, message.id, attempt) {
        capabilities = null; error = null
        val result = conversation.messageCapabilities(message.chatId, message.id)
        capabilities = result.getOrNull()
        error = result.exceptionOrNull()?.message
    }
    val allowed = capabilities
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (deleting) "Удалить сообщение?" else "Сообщение") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(message.summary(), maxLines = 3, modifier = Modifier.padding(bottom = 12.dp))
            if (allowed == null && error == null || busy) CircularProgressIndicator(Modifier.size(24.dp))
            if (deleting && allowed != null) {
                if (allowed.canDeleteForSelf) DeleteChoice("Только у меня", !everyone, !busy) { everyone = false }
                if (allowed.canDeleteForAll) DeleteChoice("У всех участников", everyone, !busy) { everyone = true }
                Text("Удаление нельзя отменить.", modifier = Modifier.padding(top = 12.dp))
            } else if (allowed != null) {
                if (allowed.canReply) TextButton(onClick = { onClose(); onReply() }) { Text("Ответить") }
                if (allowed.canCopy && message.text.isNotBlank()) TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Сообщение", message.text)); onClose() }) { Text("Копировать текст") }
                if (allowed.canEdit) TextButton(onClick = { onClose(); onEdit() }) { Text(if (message.media == null) "Редактировать" else "Изменить подпись") }
                if (allowed.canDeleteForSelf || allowed.canDeleteForAll) TextButton(onClick = { everyone = !allowed.canDeleteForSelf; deleting = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                if (!allowed.canReply && !allowed.canCopy && !allowed.canEdit && !allowed.canDeleteForSelf && !allowed.canDeleteForAll) Text("Для этого сообщения нет доступных действий")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            if (allowed == null && error != null) TextButton(onClick = { attempt++ }) { Text("Повторить") }
        } },
        dismissButton = { TextButton(onClick = { if (deleting) { deleting = false; error = null } else onClose() }, enabled = !busy) { Text(if (deleting) "Назад" else "Закрыть") } },
        confirmButton = { if (deleting && allowed != null) TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; error = null
            try {
                val result = conversation.deleteMessage(message.chatId, message.id, if (everyone) DeleteMessageScope.ForEveryone else DeleteMessageScope.ForSelf)
                if (result.isSuccess) onClose() else error = result.exceptionOrNull()?.message
            } finally { busy = false }
        } }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } }
    )
}

@Composable
private fun DeleteChoice(text: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = onSelect, enabled = enabled)
        Text(text)
    }
}

@Composable
internal fun EditMessageDialog(message: ChatMessage, limit: Int, conversation: ConversationRepository, onClose: () -> Unit) {
    var text by rememberSaveable(message.chatId, message.id) { mutableStateOf(message.text) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val count = text.codePointCount(0, text.length)
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (message.media == null) "Редактировать сообщение" else "Редактировать подпись") },
        text = { Column {
            OutlinedTextField(text, { text = it }, enabled = !busy, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), isError = count > limit,
                supportingText = { Text("$count / $limit") })
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Отмена") } },
        confirmButton = { TextButton(enabled = !busy && text != message.text && count <= limit && (message.media != null || text.isNotBlank()), onClick = { scope.launch {
            busy = true; error = null
            try {
                val result = conversation.editMessage(message.chatId, message.id, text)
                if (result.isSuccess) onClose() else error = result.exceptionOrNull()?.message
            } finally { busy = false }
        } }) { Text("Сохранить") } }
    )
}

internal fun ChatMessage.summary(): String = text.ifBlank { media?.kind?.label() ?: "Сообщение" }.take(180)
