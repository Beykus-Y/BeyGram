package com.beykus.beygram.ui.screens.share

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.share.IncomingShareParser
import com.beykus.beygram.share.ShareSessions
import com.beykus.beygram.ui.text.resolve

/**
 * Экран «Отправить в BeyGram»: выбор чата, в группе с темами — выбор темы, потом предпросмотр с явной кнопкой отправки.
 * Ничего не уходит без нажатия «Отправить»; текст и подпись можно поправить, файлы можно убрать.
 * В группе с темами сообщение не уйдёт в General молча: тема выбирается обязательно.
 * Копирование и отправка живут в [ShareSessions], поэтому поворот экрана их не прерывает и не дублирует отправку.
 * Пока идёт отправка, системный «Назад» не закрывает экран.
 */
@Composable
fun ShareTargetScreen(
    requestId: Long,
    text: String,
    uris: List<Uri>,
    chats: List<ChatPreview>,
    captionLimit: Int,
    loadTopics: suspend (chatId: Long) -> Result<List<ForumTopic>?>,
    sendText: suspend (chatId: Long, topicId: Long, text: String) -> Result<Unit>,
    sendMedia: suspend (chatId: Long, topicId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>,
    onSent: (chatId: Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val appScope = remember(context) { (context.applicationContext as BeyGramApplication).applicationScope }
    val importer = remember(context) { OutgoingMediaImporter(context) }
    val session = remember(requestId) { ShareSessions.of(context, requestId, uris, text) }

    var targetId by rememberSaveable(requestId) { mutableStateOf(0L) }
    var query by rememberSaveable { mutableStateOf("") }
    // Тема группы с темами; 0 — чат без тем, тема не нужна.
    var topicId by rememberSaveable(requestId) { mutableStateOf(0L) }
    var topics by remember(requestId) { mutableStateOf<List<ForumTopic>?>(null) }
    var topicsReady by remember(requestId) { mutableStateOf(false) }
    var topicsError by remember(requestId) { mutableStateOf<UiText?>(null) }
    val target = chats.firstOrNull { it.id == targetId }
    val hasFiles = uris.isNotEmpty()
    // С файлами подпись идёт в её лимите; без файлов — лимит текста сообщения.
    val limit = if (session.staged.isNotEmpty()) captionLimit else IncomingShareParser.MAX_TEXT_LENGTH
    val tooLong = session.draft.codePointCount(0, session.draft.length) > limit
    // Без готовых файлов текст уйдёт, только если файлов не было или пользователь убрал их все сам; при сбое подготовки не отправляем.
    val canSend = !tooLong && !session.sending && !session.staging &&
        (session.staged.isNotEmpty() || (session.draft.isNotBlank() && (!hasFiles || session.error == null)))
    BackHandler(enabled = session.sending) {}

    LaunchedEffect(session) { session.startStaging(context, appScope) }
    // Загружаем темы выбранного чата: null означает, что это не группа с темами и тема не нужна.
    LaunchedEffect(targetId) {
        topicsReady = false
        topics = null
        topicsError = null
        if (targetId == 0L) return@LaunchedEffect
        loadTopics(targetId)
            .onSuccess { loaded -> topics = loaded; topicsReady = true }
            .onFailure { topicsError = (it as? UserMessageException)?.text ?: UiText.Res(R.string.share_topics_failed) }
    }
    // Отправка могла закончиться, пока экрана не было (поворот): тогда открываем чат, куда ушло сообщение.
    LaunchedEffect(session.sent) { if (session.sent) onSent(session.sentChatId) }

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
                    enabled = !session.sending,
                    onClick = {
                        if (target != null) { targetId = 0L; topicId = 0L; session.error = null } else onCancel()
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

            if (target == null) {
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (session.staging) Text(stringResource(R.string.share_preparing), style = MaterialTheme.typography.bodyMedium)
                session.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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
                                .clickable { targetId = chat.id; topicId = 0L; session.error = null }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
            } else if (!topicsReady) {
                Text(stringResource(R.string.share_send_to, target.title), style = MaterialTheme.typography.titleSmall)
                topicsError?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (topicsError == null) Text(stringResource(R.string.share_loading), style = MaterialTheme.typography.bodyMedium)
            } else if (topics != null && topicId == 0L) {
                Text(stringResource(R.string.share_choose_topic, target.title), style = MaterialTheme.typography.titleSmall)
                if (topics.isNullOrEmpty()) Text(stringResource(R.string.share_no_topics), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(topics.orEmpty(), key = { it.id }) { topic ->
                        Text(
                            text = topic.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { topicId = topic.id; session.error = null }
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
                    session.staged.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = !session.sending,
                                onClick = { session.removeStaged(item, importer, appScope) }
                            ) { Text(stringResource(R.string.common_delete)) }
                        }
                    }
                    OutlinedTextField(
                        value = session.draft,
                        onValueChange = { session.draft = it },
                        placeholder = { Text(stringResource(if (hasFiles) R.string.share_caption_placeholder else R.string.share_text_placeholder)) },
                        enabled = !session.sending,
                        minLines = 3,
                        isError = tooLong,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tooLong) {
                        Text(stringResource(R.string.share_text_too_long, limit), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    session.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Button(
                        enabled = canSend && target != null,
                        onClick = { session.send(target.id, topicId, appScope, sendText, sendMedia) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.share_send))
                    }
                }
            }
        }
    }
}
