package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.InlineBotResult
import com.beykus.beygram.domain.model.InlineBotResultKind
import com.beykus.beygram.domain.model.InlineBotResults
import com.beykus.beygram.domain.model.BotWebAppSource
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.mergeInlineBotResults
import com.beykus.beygram.domain.model.nextInlineQueryOffset
import com.beykus.beygram.domain.model.parseInlineQuery
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Inline mode for a draft that starts with `@bot query`. The panel stays above the composer, debounces queries and
 * sends only through TDLib, so a stale or cancelled request can never race a newer one. It is hidden while editing,
 * preparing attachments or in a read-only chat.
 */
@Composable
internal fun BotInlineResultsPanel(
    chatId: Long,
    text: String,
    active: Boolean,
    replyToMessageId: Long?,
    forumTopicId: Long,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onOpenWebApp: (url: String, botUserId: Long, messageId: Long, source: BotWebAppSource, buttonText: String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onSent: (String) -> Unit
) {
    val parsed = remember(text) { parseInlineQuery(text) }
    if (!active || parsed == null) return
    val context = LocalContext.current
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    // State belongs to one query: a changed query immediately hides the previous page instead of allowing a stale tap.
    var results by remember(chatId, parsed.username, parsed.query) { mutableStateOf<InlineBotResults?>(null) }
    var loading by remember(chatId, parsed.username, parsed.query) { mutableStateOf(false) }
    var loadingMore by remember(chatId, parsed.username, parsed.query) { mutableStateOf(false) }
    var sending by remember(chatId, parsed.username, parsed.query) { mutableStateOf(false) }
    var sendingResultId by remember(chatId, parsed.username, parsed.query) { mutableStateOf<String?>(null) }
    var loadError by remember(chatId, parsed.username, parsed.query) { mutableStateOf<String?>(null) }
    var pageError by remember(chatId, parsed.username, parsed.query) { mutableStateOf<String?>(null) }
    var sendError by remember(chatId, parsed.username, parsed.query) { mutableStateOf<String?>(null) }
    var attempt by remember(chatId) { mutableIntStateOf(0) }
    // Every new query owns a generation; late pages from a previous query are ignored instead of being appended.
    var generation by remember(chatId, parsed.username) { mutableIntStateOf(0) }
    // If the draft changes while TDLib is still sending, this panel leaves composition and must release the composer.
    DisposableEffect(Unit) { onDispose { onSendingChange(false) } }

    LaunchedEffect(chatId, parsed.username, parsed.query, active, attempt) {
        if (!active) return@LaunchedEffect
        val token = ++generation
        loading = true
        loadingMore = false
        loadError = null
        pageError = null
        sendError = null
        results = null
        delay(INLINE_QUERY_DEBOUNCE_MS)
        val loaded = conversation.inlineBotQuery(chatId, parsed.username, parsed.query)
        if (generation != token) return@LaunchedEffect
        loading = false
        // A null page means the username is not an inline bot: nothing is shown, and no error is raised.
        loaded.onSuccess { results = it }
            .onFailure { loadError = it.userMessage(context, R.string.chat_inline_error) }
    }

    fun loadMore() {
        val current = results ?: return
        val offset = current.nextOffset
        if (offset.isBlank() || loading || loadingMore || sending) return
        val token = generation
        val username = parsed.username
        val query = parsed.query
        loadingMore = true
        pageError = null
        scope.launch {
            try {
                val page = conversation.inlineBotQuery(chatId, username, query, offset)
                if (generation != token) return@launch
                page.onSuccess { next ->
                    if (next != null) results = next.copy(
                        results = mergeInlineBotResults(current.results, next.results),
                        nextOffset = nextInlineQueryOffset(offset, next.nextOffset),
                        buttonText = next.buttonText.ifBlank { current.buttonText },
                        buttonWebAppUrl = next.buttonWebAppUrl.ifBlank { current.buttonWebAppUrl }
                    )
                }.onFailure { pageError = it.userMessage(context, R.string.chat_inline_error) }
            } finally {
                if (generation == token) loadingMore = false
            }
        }
    }

    fun send(result: InlineBotResult) {
        val answer = results ?: return
        if (sending) return
        // The composer also observes this flag, so a second tap cannot send the literal "@bot query" alongside the result.
        sending = true
        sendingResultId = result.id
        onSendingChange(true)
        scope.launch {
            sendError = null
            try {
                conversation.sendInlineBotResult(chatId, result.queryId.takeIf { it != 0L } ?: answer.queryId, result.id, replyToMessageId, forumTopicId)
                    .onSuccess { results = null; onSent(text) }
                    .onFailure { sendError = it.userMessage(context, R.string.chat_inline_send_failed) }
            } finally {
                sending = false
                sendingResultId = null
                onSendingChange(false)
            }
        }
    }

    // Idle with no results and no error means the draft is not an inline bot: the panel stays out of the layout.
    if (loading || results != null || loadError != null) Column(Modifier.fillMaxWidth().background(palette.surface)) {
        HorizontalDivider(color = palette.divider)
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Code, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("@${parsed.username}", color = palette.accentInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
        }
        loadError?.let { failure ->
            Column(Modifier.padding(start = 14.dp, end = 8.dp, bottom = 6.dp)) {
                Text(failure, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                TextButton(onClick = { attempt++ }, enabled = !sending) { Text(stringResource(R.string.common_retry), color = palette.accent) }
            }
        }
        sendError?.let { failure ->
            Text(
                failure,
                color = palette.warningText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp).errorAnnouncement()
            )
        }
        val answer = results
        if (answer != null && answer.buttonWebAppUrl.isNotBlank()) {
            val openLabel = stringResource(R.string.chat_inline_open_webapp)
            TextButton(
                onClick = { onOpenWebApp(answer.buttonWebAppUrl, answer.botUserId, 0L, BotWebAppSource.InlineQueryButton, answer.buttonText) },
                enabled = !sending,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Text(answer.buttonText.ifBlank { openLabel }, color = palette.accent)
            }
        }
        when {
            loadError != null -> Unit
            answer == null && loading -> Text(
                stringResource(R.string.chat_inline_loading),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp)
            )
            answer != null && answer.results.isEmpty() -> Text(
                stringResource(R.string.chat_inline_empty),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp)
            )
            answer != null -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 236.dp)) {
                items(answer.results, key = { it.id }) { result ->
                    InlineResultRow(
                        result = result,
                        enabled = !sending,
                        showProgress = sendingResultId == result.id,
                        observeFile = observeFile,
                        onDownloadFile = onDownloadFile,
                        onClick = { send(result) }
                    )
                    HorizontalDivider(color = palette.divider.copy(alpha = 0.5f))
                }
            }
        }
        answer?.let { page ->
            pageError?.let { failure ->
                Text(
                    failure,
                    color = palette.warningText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp).errorAnnouncement()
                )
            }
            if (page.nextOffset.isNotBlank()) TextButton(
                onClick = ::loadMore,
                enabled = !loading && !loadingMore && !sending,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                if (loadingMore) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = palette.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.chat_inline_load_more), color = palette.accent)
            }
        }
    }
}

@Composable
private fun InlineResultRow(
    result: InlineBotResult,
    enabled: Boolean,
    showProgress: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val fallbackTitle = stringResource(result.kind.labelRes())
    val title = result.title.ifBlank { fallbackTitle }
    Row(
        Modifier.fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.chat_inline_send_result, title),
                onClick = onClick
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InlineResultThumbnail(result, observeFile, onDownloadFile)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result.description.isNotBlank()) {
                Text(result.description, color = palette.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showProgress) CircularProgressIndicator(Modifier.size(18.dp), color = palette.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun InlineResultThumbnail(
    result: InlineBotResult,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    onDownloadFile: (Int) -> Unit
) {
    val fileId = result.thumbnailFileId
    if (fileId == null) {
        InlineResultThumbnailFallback(result.kind)
        return
    }
    val file by remember(fileId) { observeFile(fileId) }.collectAsStateWithLifecycle()
    LaunchedEffect(fileId, file.localPath) {
        if (file.localPath == null && !file.isDownloading && file.error == null) onDownloadFile(fileId)
    }
    val path = file.localPath
    if (path == null) InlineResultThumbnailFallback(result.kind)
    else LocalMediaImage(path, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), maxDimension = 128)
}

@Composable
private fun InlineResultThumbnailFallback(kind: InlineBotResultKind) {
    val palette = beyGramPalette()
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(palette.surface2),
        contentAlignment = Alignment.Center
    ) { Icon(kind.icon(), contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(21.dp)) }
}

private fun InlineBotResultKind.icon(): ImageVector = when (this) {
    InlineBotResultKind.Article -> Icons.Rounded.Description
    InlineBotResultKind.Photo, InlineBotResultKind.Sticker -> Icons.Rounded.Image
    InlineBotResultKind.Video -> Icons.Rounded.Videocam
    InlineBotResultKind.Animation -> Icons.Rounded.PlayArrow
    InlineBotResultKind.Audio -> Icons.Rounded.MusicNote
    InlineBotResultKind.Document -> Icons.Rounded.InsertDriveFile
    InlineBotResultKind.Voice -> Icons.Rounded.Mic
    InlineBotResultKind.Contact -> Icons.Rounded.Contacts
    InlineBotResultKind.Location, InlineBotResultKind.Venue -> Icons.Rounded.Place
    InlineBotResultKind.Game -> Icons.Rounded.SportsEsports
    InlineBotResultKind.Other -> Icons.Rounded.Apps
}

private fun InlineBotResultKind.labelRes(): Int = when (this) {
    InlineBotResultKind.Article -> R.string.chat_inline_kind_article
    InlineBotResultKind.Photo -> R.string.chat_inline_kind_photo
    InlineBotResultKind.Video -> R.string.chat_inline_kind_video
    InlineBotResultKind.Animation -> R.string.chat_inline_kind_animation
    InlineBotResultKind.Audio -> R.string.chat_inline_kind_audio
    InlineBotResultKind.Document -> R.string.chat_inline_kind_document
    InlineBotResultKind.Voice -> R.string.chat_inline_kind_voice
    InlineBotResultKind.Sticker -> R.string.chat_inline_kind_sticker
    InlineBotResultKind.Contact -> R.string.chat_inline_kind_contact
    InlineBotResultKind.Location -> R.string.chat_inline_kind_location
    InlineBotResultKind.Venue -> R.string.chat_inline_kind_venue
    InlineBotResultKind.Game -> R.string.chat_inline_kind_game
    InlineBotResultKind.Other -> R.string.chat_inline_kind_other
}

private const val INLINE_QUERY_DEBOUNCE_MS = 350L
