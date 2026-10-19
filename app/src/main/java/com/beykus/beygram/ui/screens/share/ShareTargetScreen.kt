package com.beykus.beygram.ui.screens.share

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.share.IncomingShareParser
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.launch

/**
 * Экран «Отправить в BeyGram»: сначала выбор чата, потом предпросмотр текста с явной кнопкой отправки.
 * Ничего не уходит без нажатия «Отправить»; текст можно поправить перед отправкой.
 * Пока идёт отправка, системный «Назад» не закрывает экран: сообщение может уже уйти.
 */
@Composable
fun ShareTargetScreen(
    text: String,
    fileUnsupported: Boolean,
    chats: List<ChatPreview>,
    sendText: suspend (chatId: Long, text: String) -> Result<Unit>,
    onSent: (chatId: Long) -> Unit,
    onCancel: () -> Unit
) {
    var targetId by rememberSaveable { mutableStateOf(0L) }
    var draft by rememberSaveable(text) { mutableStateOf(text) }
    var query by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val scope = rememberCoroutineScope()
    val target = chats.firstOrNull { it.id == targetId }
    val tooLong = draft.codePointCount(0, draft.length) > IncomingShareParser.MAX_TEXT_LENGTH
    BackHandler(enabled = sending) {}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        if (target != null) { targetId = 0L; error = null } else onCancel()
                    }
                ) {
                    Text(stringResource(if (target != null) R.string.common_back else R.string.common_cancel))
                }
                Text(
                    text = stringResource(R.string.share_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (fileUnsupported) {
                Text(stringResource(R.string.share_file_unsupported), style = MaterialTheme.typography.bodyLarge)
            } else if (target == null) {
                Text(
                    text = text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.share_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val shown = chats.filter { it.canPost && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.share_no_chats), style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(shown, key = { it.id }) { chat ->
                        Text(
                            text = chat.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { targetId = chat.id; error = null }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.share_send_to, target.title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.share_text_placeholder)) },
                        enabled = !sending,
                        minLines = 3,
                        isError = tooLong,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tooLong) {
                        Text(stringResource(R.string.share_text_too_long, IncomingShareParser.MAX_TEXT_LENGTH), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Button(
                        enabled = draft.isNotBlank() && !tooLong && !sending,
                        onClick = {
                            sending = true
                            error = null
                            scope.launch {
                                sendText(target.id, draft.trim())
                                    .onSuccess { onSent(target.id) }
                                    .onFailure {
                                        error = (it as? UserMessageException)?.text ?: UiText.Res(R.string.share_send_failed)
                                        sending = false
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.share_send))
                    }
                }
            }
        }
    }
}
