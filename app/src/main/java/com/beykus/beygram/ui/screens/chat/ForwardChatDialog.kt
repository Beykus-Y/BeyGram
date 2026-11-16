package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import com.beykus.beygram.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.ui.components.BeyGramSheet
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.TextButton
import com.beykus.beygram.ui.components.sheetSurfaceColor
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MediaKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.screens.share.ShareRecipientBrowser
import com.beykus.beygram.ui.screens.share.rememberShareRecipientDirectory
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Один адресат пересылки: чат целиком ([topicId] 0) или тема форума. Сравнивается по id, а не по объекту чата,
 * потому что список чатов обновляется во время выбора.
 */
internal data class ForwardTarget(
    val chatId: Long,
    val title: String,
    val topicId: Long = 0L,
    val topicTitle: String? = null
)

/** Selection is keyed by chat and topic, independent of changing display titles. */
internal fun toggleForwardTarget(
    selected: List<ForwardTarget>, target: ForwardTarget, singleRecipient: Boolean
): List<ForwardTarget> {
    val matches: (ForwardTarget) -> Boolean = { it.chatId == target.chatId && it.topicId == target.topicId }
    return when {
        selected.any(matches) -> selected.filterNot(matches)
        singleRecipient -> listOf(target)
        else -> selected + target
    }
}

/**
 * Полноэкранный выбор адресатов. Тап по строке только отмечает чат: ничего не уходит, пока не нажата кнопка «Переслать».
 * Форумы открывают список тем, где каждая тема тоже отмечается отдельно. Отправку выполняет [onSend] и возвращает
 * адресатов, которые не приняли сообщения; диалог остаётся открытым с ними, чтобы повтор не дублировал успехи.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForwardChatDialog(
    chats: List<ChatPreview>,
    busy: Boolean,
    error: String?,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    asCopy: Boolean = false,
    allowComment: Boolean = true,
    /** Telegram text limit in code points; a longer comment is refused here, never silently cut. */
    commentLimit: Int = Int.MAX_VALUE,
    loadTopics: (suspend (Long) -> Result<List<ForumTopic>?>)? = null,
    singleRecipient: Boolean = false,
    titleOverride: String? = null,
    previewText: String? = null,
    previewMedia: MessageMedia? = null,
    sendLabel: String? = null,
    onAsCopyChange: ((Boolean) -> Unit)? = null,
    removeCaption: Boolean = false,
    onRemoveCaptionChange: ((Boolean) -> Unit)? = null,
    retryBlocked: Boolean = false,
    recipientRepository: TelegramRepository? = null,
    candidatePolicy: (List<ChatPreview>) -> List<ChatPreview> = { it },
    onSend: suspend (List<ForwardTarget>, String) -> List<ForwardTarget>,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var topicQuery by remember { mutableStateOf("") }
    val targetSaver = remember {
        listSaver<List<ForwardTarget>, Any>(
            save = { targets -> targets.flatMap { listOf(it.chatId, it.title, it.topicId, it.topicTitle.orEmpty()) } },
            restore = { entries -> entries.chunked(4).map {
                ForwardTarget(it[0] as Long, it[1] as String, it[2] as Long, (it[3] as String).ifEmpty { null })
            } }
        )
    }
    var targets by rememberSaveable(stateSaver = targetSaver) { mutableStateOf(emptyList<ForwardTarget>()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    // If recreation interrupts an in-flight operation, don't offer a blind repeat.
    var attemptInFlight by rememberSaveable { mutableStateOf(false) }
    var topicChat by remember { mutableStateOf<ChatPreview?>(null) }
    var topics by remember { mutableStateOf<List<ForumTopic>>(emptyList()) }
    var loadingTopics by remember { mutableStateOf(false) }
    var topicError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val locked = busy || sending || loadingTopics
    val dismissLocked by rememberUpdatedState(locked)
    val interrupted = attemptInFlight && !sending
    val commentOverLimit = comment.codePointCount(0, comment.length) > commentLimit
    val directory = rememberShareRecipientDirectory(chats, recipientRepository, candidatePolicy)
    val writableIds = remember(directory.chats) { directory.chats.filter { it.canPost }.map { it.id }.toSet() }
    val unavailableTarget = targets.any { it.chatId !in writableIds }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { next -> next == SheetValue.Expanded || !dismissLocked }
    )

    fun isPicked(chatId: Long, topicId: Long) = targets.any { it.chatId == chatId && it.topicId == topicId }

    fun toggle(target: ForwardTarget) {
        if (locked || (attempted && !isPicked(target.chatId, target.topicId))) return
        targets = toggleForwardTarget(targets, target, singleRecipient)
    }

    fun backFromTopics() {
        if (locked) return
        topicChat = null
        topicQuery = ""
        topics = emptyList()
        topicError = null
    }

    fun chooseChat(chat: ChatPreview) {
        if (locked || attempted) return
        val loader = loadTopics
        if (loader == null || !chat.isGroup) {
            toggle(ForwardTarget(chat.id, chat.title))
            return
        }
        loadingTopics = true
        topicError = null
        scope.launch {
            val result = try { loader(chat.id) } finally { loadingTopics = false }
            result.onSuccess { loaded ->
                val writable = loaded.orEmpty().filter { it.canWrite }
                if (loaded == null) toggle(ForwardTarget(chat.id, chat.title))
                else {
                    topicChat = chat
                    topics = writable
                }
            }.onFailure {
                topicChat = chat
                topics = emptyList()
                topicError = it.userMessage(context, R.string.forward_topics_failed)
            }
        }
    }

    fun send() {
        if (locked || interrupted || retryBlocked || targets.isEmpty() || unavailableTarget || commentOverLimit) return
        attempted = true
        sending = true
        attemptInFlight = true
        scope.launch {
            val failed = try {
                onSend(targets, if (allowComment) comment.trim() else "").also { attemptInFlight = false }
            } finally {
                sending = false
            }
            if (failed.isEmpty()) onClose() else {
                targets = failed
                backFromTopics()
            }
        }
    }

    val chat = topicChat

    LaunchedEffect(targets.isNotEmpty(), query.isNotEmpty(), topicChat?.id, loadingTopics) {
        if (targets.isNotEmpty() || query.isNotEmpty() || topicChat != null || loadingTopics) {
            sheetState.expand()
        }
    }
    BeyGramSheet(
        onDismissRequest = { if (!locked) onClose() },
        sheetState = sheetState,
        scrollableContent = false,
        bottomPadding = 0.dp
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = sheetSurfaceColor(),
            topBar = {
                TopAppBar(
                    modifier = Modifier.topAppBarSurface(),
                    title = {
                        Text(
                            if (chat != null) stringResource(R.string.forward_choose_topic, chat.title)
                            else titleOverride ?: stringResource(if (asCopy) R.string.forward_title_copy else R.string.forward_title),
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (chat != null) backFromTopics() else if (!locked) onClose() },
                            enabled = !locked
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
                        }
                    },
                    actions = {
                        if (sheetState.currentValue != SheetValue.Expanded) IconButton(
                            onClick = { scope.launch { sheetState.expand() } }
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.share_expand_picker), tint = palette.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = sheetSurfaceColor())
                )
            },
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().background(sheetSurfaceColor()).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (targets.isNotEmpty()) {
                        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(targets, key = { "${it.chatId}:${it.topicId}" }) { target ->
                                Box(
                                    Modifier.widthIn(max = 180.dp).clip(styledShape(12.dp)).background(palette.accentSoft)
                                        .clickable(enabled = !locked, role = Role.Button) { toggle(target) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        target.topicTitle?.let { "${target.title} · $it" } ?: target.title,
                                        color = palette.accentInk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (onAsCopyChange != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.share_show_author), color = palette.textPrimary, modifier = Modifier.weight(1f))
                            Switch(checked = !asCopy, onCheckedChange = { onAsCopyChange(!it) }, enabled = !locked && !attempted)
                        }
                    }
                    if (onRemoveCaptionChange != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.share_remove_caption), color = palette.textPrimary, modifier = Modifier.weight(1f))
                            Switch(checked = removeCaption, onCheckedChange = onRemoveCaptionChange, enabled = !locked && !attempted)
                        }
                    }
                    if (allowComment) {
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            enabled = !locked,
                            placeholder = { Text(stringResource(R.string.forward_comment_hint)) },
                            maxLines = 3,
                            isError = commentOverLimit,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (commentOverLimit) Text(
                            stringResource(R.string.chat_truncated_notice, commentLimit),
                            color = palette.warningText, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (targets.isEmpty()) stringResource(R.string.forward_pick_hint)
                            else stringResource(R.string.forward_selected_count, targets.size),
                            color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { send() }, enabled = targets.isNotEmpty() && !locked && !interrupted && !retryBlocked && !unavailableTarget && !commentOverLimit) {
                            Text(sendLabel ?: stringResource(if (titleOverride != null) R.string.share_send_count else R.string.forward_send_count, targets.size))
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (chat == null && previewText != null) Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(styledShape(14.dp)).background(palette.accentSoft).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.share_preview_title), color = palette.accentInk, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        previewMedia?.let { ShareMediaThumbnail(it, observeFile) }
                        Text(previewText, color = palette.textPrimary, fontWeight = FontWeight.Medium,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                if (unavailableTarget) Text(
                    stringResource(R.string.share_unavailable_recipient), color = palette.warningText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).errorAnnouncement()
                )
                if (interrupted || retryBlocked) Text(
                    stringResource(R.string.share_interrupted_warning), color = palette.warningText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).errorAnnouncement()
                )
                if (asCopy && titleOverride == null && chat == null) Text(
                    stringResource(R.string.forward_copy_note),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                error?.let {
                    Text(it, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).errorAnnouncement())
                }
                topicError?.let {
                    Text(it, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).errorAnnouncement())
                }
                when {
                    loadingTopics -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) { CircularProgressIndicator() }
                    chat != null -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SearchField(topicQuery, { topicQuery = it }, stringResource(R.string.share_search_topics))
                        }
                        if (topicError != null) TextButton(onClick = { chooseChat(chat) }, enabled = !locked) {
                            Text(stringResource(R.string.common_retry))
                        }
                        val filteredTopics = topics.filter { it.title.contains(topicQuery.trim(), ignoreCase = true) }
                        LazyColumn(Modifier.weight(1f)) {
                        if (topics.isEmpty() && topicError == null) item(key = "empty") {
                            Text(stringResource(R.string.share_no_topics), color = palette.textSecondary,
                                modifier = Modifier.padding(16.dp))
                        }
                        if (topics.isNotEmpty() && filteredTopics.isEmpty()) item(key = "no-results") {
                            Text(stringResource(R.string.forward_no_results), color = palette.textSecondary, modifier = Modifier.padding(16.dp))
                        }
                        items(filteredTopics, key = { it.id }) { topic ->
                            ForwardRow(
                                title = topic.title,
                                selected = isPicked(chat.id, topic.id),
                                enabled = !locked,
                                onClick = { toggle(ForwardTarget(chat.id, chat.title, topic.id, topic.title)) },
                                leading = { TopicIcon(Icons.Rounded.ChatBubbleOutline) }
                            )
                        }
                        }
                    }
                    else -> {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SearchField(query, { if (!locked) query = it }, stringResource(R.string.forward_search_hint))
                        }
                        ShareRecipientBrowser(
                            directory = directory,
                            repository = recipientRepository,
                            query = query,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            modifier = Modifier.weight(1f),
                            enabled = !locked && !attempted,
                            selectedIds = targets.map { it.chatId }.toSet(),
                            onChoose = ::chooseChat
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            color = if (enabled) palette.textPrimary else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        SelectionMark(selected)
    }
}

@Composable
private fun TopicIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val palette = beyGramPalette()
    Icon(icon, null, tint = palette.accent, modifier = Modifier.size(44.dp).padding(10.dp))
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val palette = beyGramPalette()
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(palette.accent) else Modifier.border(1.5.dp, palette.textSecondary, CircleShape)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(16.dp))
    }
}

/** Local cached previews only: do not trigger a download or reveal a spoiler/self-destructing attachment. */
@Composable
private fun ShareMediaThumbnail(media: MessageMedia, observeFile: (Int) -> StateFlow<MediaFileState>) {
    if (media.hasSpoiler || media.isSecret || media.isSecretChat) return
    if (media.kind !in setOf(MediaKind.Photo, MediaKind.Video, MediaKind.Animation)) return
    val fileId = media.thumbnailFileId ?: media.fileId.takeIf { media.kind == MediaKind.Photo }
    val path = if (fileId != null) {
        val file by remember(fileId, observeFile) { observeFile(fileId) }.collectAsStateWithLifecycle()
        file.localPath
    } else null
    val tiny = media.minithumbnail
    val modifier = Modifier.size(56.dp).clip(styledShape(10.dp))
    when {
        path != null -> LocalMediaImage(path, modifier, maxDimension = 160, animate = false)
        tiny != null -> MinithumbnailImage(tiny, modifier)
    }
}
