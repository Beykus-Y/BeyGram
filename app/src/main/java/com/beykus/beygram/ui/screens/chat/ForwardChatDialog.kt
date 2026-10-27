package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.ChatAvatar
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
    onSend: suspend (List<ForwardTarget>, String) -> List<ForwardTarget>,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generalTopic = stringResource(R.string.forward_topic_general)
    var query by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<ForwardTarget>>(emptyList()) }
    var topicChat by remember { mutableStateOf<ChatPreview?>(null) }
    var topics by remember { mutableStateOf<List<ForumTopic>>(emptyList()) }
    var loadingTopics by remember { mutableStateOf(false) }
    var topicError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val locked = busy || sending || loadingTopics
    val commentOverLimit = comment.codePointCount(0, comment.length) > commentLimit

    fun isPicked(chatId: Long, topicId: Long) = targets.any { it.chatId == chatId && it.topicId == topicId }

    fun toggle(target: ForwardTarget) {
        if (locked) return
        targets = if (targets.any { it.chatId == target.chatId && it.topicId == target.topicId }) {
            targets.filterNot { it.chatId == target.chatId && it.topicId == target.topicId }
        } else targets + target
    }

    fun backFromTopics() {
        if (locked) return
        topicChat = null
        topics = emptyList()
        topicError = null
    }

    fun chooseChat(chat: ChatPreview) {
        val loader = loadTopics
        if (loader == null || !chat.isGroup) {
            toggle(ForwardTarget(chat.id, chat.title))
            return
        }
        scope.launch {
            loadingTopics = true
            topicError = null
            val result = loader(chat.id)
            loadingTopics = false
            result.onSuccess { loaded ->
                val writable = loaded.orEmpty().filter { it.canWrite }
                if (writable.isEmpty()) toggle(ForwardTarget(chat.id, chat.title))
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
        if (locked || targets.isEmpty() || commentOverLimit) return
        scope.launch {
            sending = true
            val failed = try {
                onSend(targets, if (allowComment) comment.trim() else "")
            } finally {
                sending = false
            }
            if (failed.isEmpty()) onClose() else targets = failed
        }
    }

    val chat = topicChat
    val visibleChats = remember(chats, query) {
        val needle = query.trim()
        if (needle.isEmpty()) chats else chats.filter { it.title.contains(needle, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = { if (chat != null) backFromTopics() else if (!locked) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = palette.screen,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (chat != null) stringResource(R.string.forward_choose_topic, chat.title)
                            else stringResource(if (asCopy) R.string.forward_title_copy else R.string.forward_title),
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
                )
            },
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (targets.isNotEmpty()) {
                        LazyColumn(Modifier.heightIn(max = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(targets, key = { "${it.chatId}:${it.topicId}" }) { target ->
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.accentSoft)
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
                        Button(onClick = { send() }, enabled = targets.isNotEmpty() && !locked && !commentOverLimit) {
                            Text(stringResource(R.string.forward_send_count, targets.size))
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (asCopy && chat == null) Text(
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
                    chat != null -> LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "general") {
                            ForwardRow(
                                title = generalTopic,
                                selected = isPicked(chat.id, 0L),
                                enabled = !locked,
                                onClick = { toggle(ForwardTarget(chat.id, chat.title, 0L, generalTopic)) },
                                leading = { TopicIcon(Icons.Rounded.Forum) }
                            )
                        }
                        items(topics, key = { it.id }) { topic ->
                            ForwardRow(
                                title = topic.title,
                                selected = isPicked(chat.id, topic.id),
                                enabled = !locked,
                                onClick = { toggle(ForwardTarget(chat.id, chat.title, topic.id, topic.title)) },
                                leading = { TopicIcon(Icons.Rounded.ChatBubbleOutline) }
                            )
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.forward_search_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (visibleChats.isEmpty()) Text(
                            stringResource(R.string.forward_no_results),
                            color = palette.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            items(visibleChats, key = { it.id }) { item ->
                                ForwardRow(
                                    title = item.title,
                                    selected = targets.any { it.chatId == item.id },
                                    enabled = !locked,
                                    onClick = { chooseChat(item) },
                                    leading = { ChatAvatar(item, 44.dp, observeFile, downloadFile, Modifier.clearAndSetSemantics {}) }
                                )
                            }
                        }
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
