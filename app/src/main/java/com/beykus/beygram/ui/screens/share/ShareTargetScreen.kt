package com.beykus.beygram.ui.screens.share

import android.content.Context
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.OutgoingMedia
import com.beykus.beygram.share.IncomingShareParser
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран «Отправить в BeyGram»: сначала выбор чата, потом предпросмотр содержимого с явной кнопкой отправки.
 * Ничего не уходит без нажатия «Отправить»; текст и подпись можно поправить, файлы можно убрать.
 * Файлы копируются в приватную папку приложения, пока экран открыт; при закрытии непосланные копии удаляются.
 * Пока идёт отправка, системный «Назад» не закрывает экран: сообщение может уже уйти.
 */
@Composable
fun ShareTargetScreen(
    requestId: Long,
    text: String,
    uris: List<Uri>,
    chats: List<ChatPreview>,
    sendText: suspend (chatId: Long, text: String) -> Result<Unit>,
    sendMedia: suspend (chatId: Long, media: List<OutgoingMedia>, caption: String) -> Result<Unit>,
    onSent: (chatId: Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val importer = remember(context) { OutgoingMediaImporter(context) }
    val appScope = remember(context) { (context.applicationContext as BeyGramApplication).applicationScope }
    val scope = rememberCoroutineScope()

    var targetId by rememberSaveable { mutableStateOf(0L) }
    var draft by rememberSaveable(requestId) { mutableStateOf(text) }
    var query by rememberSaveable { mutableStateOf("") }
    var sending by remember(requestId) { mutableStateOf(false) }
    var staging by remember(requestId) { mutableStateOf(uris.isNotEmpty()) }
    var staged by remember(requestId) { mutableStateOf<List<OutgoingMedia>>(emptyList()) }
    var error by remember(requestId) { mutableStateOf<UiText?>(null) }
    val target = chats.firstOrNull { it.id == targetId }
    val tooLong = draft.codePointCount(0, draft.length) > IncomingShareParser.MAX_TEXT_LENGTH
    val hasFiles = uris.isNotEmpty()
    // С файлами отправлять можно только когда все они подготовлены, иначе текст уйдёт без них.
    val canSend = !tooLong && !sending && !staging && if (hasFiles) staged.isNotEmpty() else draft.isNotBlank()

    // Файлы уходят по одному, а группы из 2–10 фото и видео — альбомом. Подпись уходит с первой отправленной частью.
    suspend fun sendStaged(chatId: Long): Result<Unit> {
        val album = staged.size in 2..IncomingShareParser.MAX_FILES && staged.all { it.kind == MediaKind.Photo || it.kind == MediaKind.Video }
        val batches = if (album) listOf(staged) else staged.map { listOf(it) }
        for (batch in batches) {
            val result = sendMedia(chatId, batch, draft.trim())
            if (result.isFailure) return result
            staged = staged.filterNot { item -> batch.any { it.path == item.path } }
            draft = ""
        }
        return Result.success(Unit)
    }
    BackHandler(enabled = sending) {}

    LaunchedEffect(requestId) {
        if (uris.isEmpty()) return@LaunchedEffect
        if (uris.size > IncomingShareParser.MAX_FILES) {
            error = UiText.Res(R.string.composer_too_many_attachments)
            staging = false
            return@LaunchedEffect
        }
        val items = mutableListOf<OutgoingMedia>()
        try {
            for (uri in uris) {
                // Фото и видео уходят как медиа, остальное как документ.
                val asDocument = withContext(Dispatchers.IO) { !isPhotoOrVideo(context, uri) }
                items += importer.prepare(uri, asDocument)
            }
            staged = items
        } catch (cancelled: CancellationException) {
            // Внутри отменённой корутины удаление не дождётся, поэтому оно идёт в общем scope.
            appScope.launch { items.forEach { importer.discard(it) } }
            throw cancelled
        } catch (failure: Exception) {
            appScope.launch { items.forEach { importer.discard(it) } }
            error = UiText.Res(R.string.composer_prepare_failed)
        } finally {
            staging = false
        }
    }

    // Непосланные файлы удаляем, когда экран закрыли; во время отправки их ещё читает TDLib, их не трогаем.
    DisposableEffect(requestId) {
        onDispose {
            if (!sending) {
                val leftover = staged
                appScope.launch { leftover.forEach { importer.discard(it) } }
            }
        }
    }

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

            if (target == null) {
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (staging) Text(stringResource(R.string.share_preparing), style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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
                    staged.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = !sending,
                                onClick = {
                                    staged = staged.filterNot { it.path == item.path }
                                    appScope.launch { importer.discard(item) }
                                }
                            ) { Text(stringResource(R.string.common_delete)) }
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(if (hasFiles) R.string.share_caption_placeholder else R.string.share_text_placeholder)) },
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
                        enabled = canSend && target != null,
                        onClick = {
                            sending = true
                            error = null
                            scope.launch {
                                val result = if (hasFiles) sendStaged(target.id) else sendText(target.id, draft.trim())
                                result
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
