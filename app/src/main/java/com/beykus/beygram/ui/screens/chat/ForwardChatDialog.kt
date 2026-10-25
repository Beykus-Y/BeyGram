package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.SavedMessagesAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/**
 * Выбор чата для пересылки. Если [loadTopics] находит форум, диалог сначала предлагает тему: общая тема передаётся
 * как thread id 0, остальные — своим TDLib forumTopicId. Во время отправки список не нажимается и диалог не закрывается.
 */
@Composable
internal fun ForwardChatDialog(
    chats: List<ChatPreview>,
    busy: Boolean,
    error: String?,
    asCopy: Boolean = false,
    loadTopics: (suspend (Long) -> Result<List<ForumTopic>?>)? = null,
    onPick: (ChatPreview, Long) -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var topicTarget by remember { mutableStateOf<ChatPreview?>(null) }
    var topics by remember { mutableStateOf<List<ForumTopic>>(emptyList()) }
    var loadingTopics by remember { mutableStateOf(false) }
    var topicError by remember { mutableStateOf<String?>(null) }

    fun chooseChat(chat: ChatPreview) {
        val loader = loadTopics
        if (loader == null || !chat.isGroup) {
            onPick(chat, 0L)
            return
        }
        scope.launch {
            loadingTopics = true
            topicError = null
            val result = loader(chat.id)
            loadingTopics = false
            result.onSuccess { loaded ->
                if (loaded.isNullOrEmpty()) onPick(chat, 0L)
                else {
                    topicTarget = chat
                    topics = loaded.filter { it.canWrite }
                    if (topics.isEmpty()) onPick(chat, 0L)
                }
            }.onFailure {
                topicTarget = chat
                topics = emptyList()
                topicError = it.userMessage(context, R.string.forward_topics_failed)
            }
        }
    }

    fun backToChats() {
        if (busy) return
        topicTarget = null
        topics = emptyList()
        topicError = null
    }

    val target = topicTarget
    AlertDialog(
        onDismissRequest = { if (!busy) { if (target != null) backToChats() else onClose() } },
        title = {
            Text(
                if (target != null) stringResource(R.string.forward_choose_topic, target.title)
                else stringResource(if (asCopy) R.string.forward_title_copy else R.string.forward_title),
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                if (asCopy) Text(
                    stringResource(R.string.forward_copy_note),
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                error?.let {
                    Text(it, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp).errorAnnouncement())
                }
                topicError?.let {
                    Text(it, color = palette.warningText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp).errorAnnouncement())
                }
                when {
                    loadingTopics -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) { CircularProgressIndicator() }
                    target != null -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        item(key = "general") {
                            TopicRow(stringResource(R.string.forward_topic_general), icon = Icons.Rounded.Forum, enabled = !busy) {
                                onPick(target, 0L)
                            }
                        }
                        items(topics, key = { it.id }) { topic ->
                            TopicRow(topic.title, icon = Icons.Rounded.ChatBubbleOutline, enabled = !busy && topic.canWrite) {
                                onPick(target, topic.id)
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable(enabled = !busy) { chooseChat(chat) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (chat.isSavedMessages) SavedMessagesAvatar(40.dp, Modifier.clearAndSetSemantics {})
                                else Avatar(chat.initials, Color(chat.avatarColorArgb), 40.dp, Modifier.clearAndSetSemantics {})
                                Spacer(Modifier.width(12.dp))
                                Text(chat.title, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (target != null) backToChats() else onClose() }, enabled = !busy) {
                Text(stringResource(if (target != null) R.string.common_back else R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun TopicRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) palette.accent else palette.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = if (enabled) palette.textPrimary else palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
