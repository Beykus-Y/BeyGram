package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

@Composable
internal fun InlineBotButtons(message: ChatMessage, conversation: ConversationRepository) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var busy by remember(message.id) { mutableStateOf(false) }
    var alert by remember(message.id) { mutableStateOf<String?>(null) }
    fun open(value: String) {
        val url = safeMessageUrl(value)
        if (url == null || runCatching { uri.openUri(url) }.isFailure) Toast.makeText(context, R.string.chat_link_failed, Toast.LENGTH_SHORT).show()
    }
    BotButtonRows(message.inlineKeyboard, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { button ->
        when (button.kind) {
            BotButtonKind.Url -> open(button.value)
            BotButtonKind.User -> open("tg://user?id=${button.value}")
            BotButtonKind.Copy -> {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(button.text, button.value))
                Toast.makeText(context, R.string.chat_bot_copied, Toast.LENGTH_SHORT).show()
            }
            BotButtonKind.Callback -> scope.launch {
                busy = true
                try {
                    conversation.botCallback(message.chatId, message.id, button.data).onSuccess {
                        if (it.text.isNotBlank()) {
                            if (it.showAlert) alert = it.text else Toast.makeText(context, it.text, Toast.LENGTH_LONG).show()
                        }
                        if (it.url.isNotBlank()) open(it.url)
                    }.onFailure { Toast.makeText(context, it.userMessage(context, R.string.chat_bot_failed), Toast.LENGTH_LONG).show() }
                } finally { busy = false }
            }
            else -> Toast.makeText(context, R.string.chat_bot_unsupported, Toast.LENGTH_LONG).show()
        }
    }
    if (alert != null) AlertDialog(onDismissRequest = { alert = null }, text = { Text(alert.orEmpty()) }, confirmButton = {
        TextButton(onClick = { alert = null }) { Text(stringResource(android.R.string.ok)) }
    })
}

@Composable
internal fun BotButtonRows(rows: List<List<BotButton>>, enabled: Boolean, modifier: Modifier = Modifier, onClick: (BotButton) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { buttons ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                buttons.forEach { button ->
                    FilledTonalButton(onClick = { onClick(button) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { Text(button.text) }
                }
            }
        }
    }
}

@Composable
internal fun BotReplyKeyboard(chatId: Long, keyboard: BotKeyboard?, conversation: ConversationRepository) {
    if (keyboard == null || keyboard.forceReply || keyboard.rows.isEmpty()) return
    var visible by remember(chatId, keyboard.messageId) { mutableStateOf(true) }
    var busy by remember(chatId, keyboard.messageId) { mutableStateOf(false) }
    val controller = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(keyboard.messageId) { controller?.hide() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        TextButton(onClick = {
            visible = !visible
            if (visible) controller?.hide() else controller?.show()
        }) { Text(stringResource(if (visible) R.string.chat_bot_hide else R.string.chat_bot_show)) }
        if (visible) BotButtonRows(keyboard.rows, !busy, Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) { button ->
            if (button.kind != BotButtonKind.Text) Toast.makeText(context, R.string.chat_bot_unsupported, Toast.LENGTH_LONG).show()
            else scope.launch {
                busy = true
                try {
                    conversation.sendMessage(chatId, button.text).onSuccess {
                        if (keyboard.oneTime) {
                            visible = false
                            conversation.dismissBotKeyboard(chatId, keyboard.messageId)
                        }
                    }.onFailure { Toast.makeText(context, it.userMessage(context, R.string.chat_bot_failed), Toast.LENGTH_LONG).show() }
                } finally { busy = false }
            }
        }
    }
}
