package com.beykus.beygram.ui.screens.share

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beykus.beygram.BeyGramApplication
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.share.IncomingShareParser
import com.beykus.beygram.share.ShareSession
import com.beykus.beygram.ui.text.resolve

/**
 * Offer the user's recent writable conversations, not alphabetical suggestions that bury
 * the intended chat. Saved Messages has a separate quick action and is never duplicated.
 */
internal fun shareTargetCandidates(
    chats: List<ChatPreview>, query: String, excludeSaved: Boolean = false
): List<ChatPreview> {
    val needle = query.trim()
    return chats.asSequence()
        .filter { it.canPost && it.title.isNotBlank() }
        .filter { !excludeSaved || !it.isSavedMessages }
        .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<ChatPreview> { it.isSavedMessages }
                .thenByDescending { it.order > 0L }
                .thenByDescending { it.order }
                .thenByDescending { it.archiveOrder }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        ).toList()
}

/**
 * Экран «Отправить в BeyGram»: выбор чата, в группе с темами — выбор темы, потом предпросмотр с явной кнопкой отправки.
 * Ничего не уходит без нажатия «Отправить»; текст и подпись можно поправить, файлы можно убрать.
 * В группе с темами сообщение не уйдёт в General молча: тема выбирается обязательно.
 * Копирование и отправка живут в [ShareSession], поэтому поворот экрана их не прерывает и не дублирует отправку.
 * Пока идёт отправка, системный «Назад» не закрывает экран.
 */
@Composable
internal fun ShareTargetScreen(
    requestId: Long,
    initialChatId: Long = 0L,
    text: String,
    uris: List<Uri>,
    session: ShareSession,
    chats: List<ChatPreview>,
    captionLimit: Int,
    loadTopics: suspend (chatId: Long) -> Result<List<ForumTopic>?>,
    sendText: suspend (chatId: Long, topicId: Long, text: String) -> Result<Unit>,
    sendMedia: suspend (chatId: Long, topicId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>,
    onSent: (chatId: Long, topicId: Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val appScope = remember(context) { (context.applicationContext as BeyGramApplication).applicationScope }
    val importer = remember(context, session.accountId) { OutgoingMediaImporter(context, session.accountId) }

    var targetId by rememberSaveable(requestId, session.accountId) { mutableStateOf(0L) }
    var shortcutApplied by rememberSaveable(requestId, session.accountId) { mutableStateOf(false) }
    // TDLib chat list arrives asynchronously on a cold start: apply the Direct Share
    // target only when it becomes an actual writable chat. Never override user choice.
    LaunchedEffect(initialChatId, chats) {
        if (!shortcutApplied && targetId == 0L && initialChatId != 0L &&
            chats.any { it.id == initialChatId && it.canPost }) {
            targetId = initialChatId
            shortcutApplied = true
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    // Тема группы с темами; 0 — чат без тем, тема не нужна.
    var topicId by rememberSaveable(requestId, session.accountId) { mutableStateOf(0L) }
    var topics by remember(requestId, session.accountId) { mutableStateOf<List<ForumTopic>?>(null) }
    var topicsReady by remember(requestId, session.accountId) { mutableStateOf(false) }
    var topicsError by remember(requestId, session.accountId) { mutableStateOf<UiText?>(null) }
    var topicsAttempt by remember(requestId, session.accountId) { mutableIntStateOf(0) }
    val target = chats.firstOrNull { it.id == targetId }
    val hasFiles = uris.isNotEmpty()
    // С файлами подпись идёт в её лимите; без файлов — лимит текста сообщения.
    val limit = if (session.staged.isNotEmpty()) captionLimit else IncomingShareParser.MAX_TEXT_LENGTH
    val tooLong = session.draft.codePointCount(0, session.draft.length) > limit
    // Без готовых файлов текст уйдёт, только если файлов не было или пользователь убрал их все сам; при сбое подготовки не отправляем.
    val canSend = !session.sending && !session.staging &&
        (session.staged.isNotEmpty() || (session.draft.isNotBlank() && (!hasFiles || session.error == null)))

    LaunchedEffect(session) { session.startStaging(context, appScope) }
    // Загружаем темы выбранного чата: null означает, что это не группа с темами и тема не нужна. После сбоя загрузка повторяется.
    LaunchedEffect(targetId, topicsAttempt) {
        topicsReady = false
        topics = null
        topicsError = null
        if (targetId == 0L) return@LaunchedEffect
        loadTopics(targetId)
            // A closed topic the user can't post to is not a share destination.
            .onSuccess { loaded -> topics = loaded?.filter { it.canWrite }; topicsReady = true }
            .onFailure { topicsError = (it as? UserMessageException)?.text ?: UiText.Res(R.string.share_topics_failed) }
    }
    // Отправка могла закончиться, пока экрана не было (поворот): тогда открываем чат, куда ушло сообщение.
    LaunchedEffect(session.sent) { if (session.sent) onSent(session.sentChatId, topicId) }

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
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(text = stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(if (target == null) R.string.share_pick_destination else R.string.share_review_before_send),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (target == null) {
                // A concise preview gives context before choosing a recipient; never sends anything on selection.
                if (text.isNotEmpty() || hasFiles) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.share_preview_title), style = MaterialTheme.typography.labelLarge)
                            if (text.isNotEmpty()) Text(
                                text = text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                            )
                            if (hasFiles) Text(
                                stringResource(R.string.share_files_count, uris.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (session.staging) Text(stringResource(R.string.share_preparing), style = MaterialTheme.typography.bodyMedium)
                session.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (session.stagingFailed) TextButton(onClick = { session.retryStaging(context, appScope) }) { Text(stringResource(R.string.common_retry)) }
                val quickSaved = if (query.isBlank()) chats.firstOrNull { it.isSavedMessages && it.canPost } else null
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.share_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val shown = shareTargetCandidates(chats, query, excludeSaved = quickSaved != null)
                if (shown.isEmpty() && quickSaved == null) {
                    Text(stringResource(R.string.share_no_chats), style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (quickSaved != null) {
                        item(key = "quick-saved") {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        targetId = quickSaved.id
                                        topicId = 0L
                                        session.error = null
                                    }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("★", color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.titleMedium)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.share_saved_quick_action),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(R.string.share_saved_target),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    if (shown.isNotEmpty() && query.isBlank()) {
                        item(key = "recent-heading") {
                            Text(stringResource(R.string.share_recent_destinations),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    items(shown, key = { it.id }) { chat ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { targetId = chat.id; topicId = 0L; session.error = null }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (chat.isSavedMessages) palette.accentSoft else MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (chat.isSavedMessages) "★" else chat.initials.take(2).ifBlank { chat.title.take(1) },
                                    color = if (chat.isSavedMessages) palette.accentInk else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(if (chat.isSavedMessages) R.string.share_saved_target else R.string.share_select_chat),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 62.dp))
                    }
                }
            } else if (!topicsReady) {
                Text(stringResource(R.string.share_send_to, target.title), style = MaterialTheme.typography.titleSmall)
                topicsError?.let {
                    Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { topicsAttempt++ }) { Text(stringResource(R.string.common_retry)) }
                }
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.share_send_review_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                        isError = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tooLong) {
                        Text(stringResource(R.string.share_truncated_notice, limit), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    session.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    if (session.stagingFailed) TextButton(onClick = { session.retryStaging(context, appScope) }) { Text(stringResource(R.string.common_retry)) }
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
