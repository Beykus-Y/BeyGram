package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.ui.components.beyGramPalette

/** Topic actions are permission-gated in the UI; TDLib checks the rights again on the server. */
@Composable
fun ForumTopicsScreen(
    chatTitle: String,
    topics: List<ForumTopic>,
    canCreate: Boolean,
    busy: Boolean,
    actionError: String?,
    onBack: () -> Unit,
    onTopic: (ForumTopic) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (ForumTopic, String) -> Unit,
    onClosed: (ForumTopic, Boolean) -> Unit,
    onPinned: (ForumTopic, Boolean) -> Unit,
    onDelete: (ForumTopic) -> Unit
) {
    val palette = beyGramPalette()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ForumTopic?>(null) }
    var deleting by remember { mutableStateOf<ForumTopic?>(null) }
    var name by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(chatTitle, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.forum_topics_subtitle), color = palette.textSecondary, fontSize = 13.sp)
            }
            if (canCreate) IconButton(enabled = !busy, onClick = { name = ""; editing = null; creating = true }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.forum_create), tint = palette.accent)
            }
        }
        HorizontalDivider(color = palette.divider)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (actionError != null) Text(actionError,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        if (topics.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.forum_topics_empty), color = palette.textSecondary)
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(topics.distinctBy(ForumTopic::id), key = { "forum-topic:${it.id}" }) { topic ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { onTopic(topic) }
                        .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(palette.accentSoft),
                        contentAlignment = Alignment.Center
                    ) { Text(if (topic.isPinned) "📌" else "💬", fontSize = 22.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(topic.title, color = palette.textPrimary, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subtitle = when {
                            topic.isClosed -> stringResource(R.string.forum_topic_closed)
                            topic.lastMessagePreview.isNotBlank() -> topic.lastMessagePreview
                            else -> stringResource(R.string.forum_topic_history_hint)
                        }
                        Text(subtitle, color = palette.textSecondary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (topic.unreadCount > 0) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(palette.accent)
                            .padding(horizontal = 9.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(topic.unreadCount.toString(), color = palette.onAccent, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    if (topic.canManage || topic.canPin) {
                        var expanded by remember(topic.id) { mutableStateOf(false) }
                        Box {
                            IconButton(enabled = !busy, onClick = { expanded = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(R.string.forum_manage), tint = palette.textSecondary)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                if (topic.canManage) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.forum_rename)) }, onClick = {
                                        expanded = false; name = topic.title; editing = topic
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(if (topic.isClosed) R.string.forum_reopen else R.string.forum_close)) },
                                        onClick = { expanded = false; onClosed(topic, !topic.isClosed) })
                                    if (topic.id != 1L) DropdownMenuItem(text = { Text(stringResource(R.string.forum_delete)) },
                                        onClick = { expanded = false; deleting = topic })
                                }
                                if (topic.canPin) DropdownMenuItem(text = { Text(stringResource(if (topic.isPinned) R.string.forum_unpin else R.string.forum_pin)) },
                                    onClick = { expanded = false; onPinned(topic, !topic.isPinned) })
                            }
                        }
                    }
                }
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 77.dp))
            }
        }
    }

    if (creating || editing != null) AlertDialog(
        onDismissRequest = { creating = false; editing = null },
        title = { Text(stringResource(if (creating) R.string.forum_create else R.string.forum_rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
            label = { Text(stringResource(R.string.forum_topic_name)) }, supportingText = { Text("${name.length}/128") }) },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && name.codePointCount(0, name.length) <= 128, onClick = {
                if (creating) onCreate(name.trim()) else editing?.let { onRename(it, name.trim()) }
                creating = false; editing = null
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text(stringResource(R.string.common_cancel)) } }
    )

    deleting?.let { topic ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.forum_delete)) },
            text = { Text(stringResource(R.string.forum_delete_confirm, topic.title)) },
            confirmButton = { TextButton(enabled = !busy, onClick = { deleting = null; onDelete(topic) }) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
