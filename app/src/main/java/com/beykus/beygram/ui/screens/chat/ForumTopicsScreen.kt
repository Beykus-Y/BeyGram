package com.beykus.beygram.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.ui.components.beyGramPalette

/** Preview queries are limited to mounted rows; one transient failure can be retried explicitly. */
private sealed interface TopicPreviewState {
    data class Loaded(val text: String?) : TopicPreviewState
    data object Unavailable : TopicPreviewState
}

/** Pure list layout: only the pinned group is moved ahead of other Telegram-ordered topics. */
internal fun orderedForumTopics(topics: List<ForumTopic>): List<ForumTopic> {
    val unique = topics.distinctBy(ForumTopic::id)
    return unique.filter(ForumTopic::isPinned) + unique.filterNot(ForumTopic::isPinned)
}

/** A responsive forum shell, shown before TDLib delivers its first topic page.
 * Never keep an un-navigable fullscreen spinner over a slow network. */
@Composable
fun ForumTopicsLoadingScreen(chatTitle: String, onBack: () -> Unit) {
    val palette = beyGramPalette()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Text(chatTitle, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(palette.surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(palette.accentSoft))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(7.dp)).background(palette.surface2))
                        Box(Modifier.fillMaxWidth(0.9f).height(10.dp).clip(RoundedCornerShape(7.dp)).background(palette.surface2))
                    }
                }
            }
        }
    }
}

/** Consistent BeyGram topic list, preserving TDLib action and paging behaviour. */
@Composable
fun ForumTopicsScreen(
    chatId: Long,
    chatTitle: String,
    topics: List<ForumTopic>,
    loadPreview: suspend (Long) -> Result<String?>,
    canCreate: Boolean,
    busy: Boolean,
    loadingTopics: Boolean = false,
    loadError: Boolean = false,
    onRetryLoad: () -> Unit = {},
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
    val topicPreviews = remember(chatId) { mutableStateMapOf<Long, TopicPreviewState>() }
    val ordered = remember(topics) { orderedForumTopics(topics) }
    val pinned = remember(ordered) { ordered.filter(ForumTopic::isPinned) }
    val regular = remember(ordered) { ordered.filterNot(ForumTopic::isPinned) }
    val savedPosition: ChatScrollPositionViewModel = viewModel(key = "forum-scroll-$chatId")
    val listState = rememberLazyListState(savedPosition.firstVisibleItemIndex, savedPosition.firstVisibleItemScrollOffset)
    DisposableEffect(listState) {
        onDispose {
            savedPosition.firstVisibleItemIndex = listState.firstVisibleItemIndex
            savedPosition.firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().background(palette.surface).padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(chatTitle, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.forum_topics_count, ordered.size), color = palette.textSecondary, fontSize = 13.sp)
            }
            if (canCreate) IconButton(enabled = !busy, onClick = { name = ""; editing = null; creating = true }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.forum_create), tint = palette.accent)
            }
        }
        HorizontalDivider(color = palette.divider)
        if (busy || loadingTopics) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        if (loadError) Row(
            Modifier.fillMaxWidth().background(palette.surface2).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.forum_topics_partial_load_failed),
                color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = !loadingTopics, onClick = onRetryLoad) {
                Text(stringResource(R.string.common_retry), color = palette.accent)
            }
        }
        if (actionError != null) {
            Text(actionError, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (ordered.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Forum, contentDescription = null, tint = palette.accent,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(palette.accentSoft).padding(12.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.forum_topics_empty), color = palette.textPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.forum_topics_empty_hint), color = palette.textSecondary,
                    textAlign = TextAlign.Center, fontSize = 14.sp)
                if (canCreate) {
                    Spacer(Modifier.height(20.dp))
                    TextButton(enabled = !busy, onClick = { name = ""; editing = null; creating = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.accent)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.forum_create), color = palette.accent)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (pinned.isNotEmpty()) item(key = "pinned-topic-heading") {
                    ForumSectionTitle(stringResource(R.string.forum_topics_pinned_section), pinned.size)
                }
                items(pinned, key = { "forum-topic:${it.id}" }) { topic ->
                    ForumTopicItem(
                        chatId, topic, topicPreviews[topic.id], loadPreview,
                        onRetryPreview = { topicPreviews.remove(topic.id) },
                        onPreviewResult = { topicPreviews[topic.id] = it },
                        busy = busy, onTopic = onTopic,
                        onRename = { name = topic.title; editing = topic },
                        onClosed = { onClosed(topic, !topic.isClosed) },
                        onPinned = { onPinned(topic, !topic.isPinned) },
                        onDelete = { deleting = topic }
                    )
                }
                if (regular.isNotEmpty()) item(key = "regular-topic-heading") {
                    ForumSectionTitle(stringResource(R.string.forum_topics_regular_section), regular.size)
                }
                items(regular, key = { "forum-topic:${it.id}" }) { topic ->
                    ForumTopicItem(
                        chatId, topic, topicPreviews[topic.id], loadPreview,
                        onRetryPreview = { topicPreviews.remove(topic.id) },
                        onPreviewResult = { topicPreviews[topic.id] = it },
                        busy = busy, onTopic = onTopic,
                        onRename = { name = topic.title; editing = topic },
                        onClosed = { onClosed(topic, !topic.isClosed) },
                        onPinned = { onPinned(topic, !topic.isPinned) },
                        onDelete = { deleting = topic }
                    )
                }
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


@Composable
private fun ForumSectionTitle(title: String, count: Int) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Text(count.toString(), color = palette.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ForumTopicItem(
    chatId: Long,
    topic: ForumTopic,
    preview: TopicPreviewState?,
    loadPreview: suspend (Long) -> Result<String?>,
    onRetryPreview: () -> Unit,
    onPreviewResult: (TopicPreviewState) -> Unit,
    busy: Boolean,
    onTopic: (ForumTopic) -> Unit,
    onRename: () -> Unit,
    onClosed: () -> Unit,
    onPinned: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = beyGramPalette()
    // A failed preview stays visible until the user retries; never retry indefinitely.
    LaunchedEffect(chatId, topic.id, topic.lastMessagePreview, topic.isClosed, preview) {
        if (!topic.isClosed && topic.lastMessagePreview.isBlank() && preview == null) {
            val result = loadPreview(topic.id)
            onPreviewResult(result.fold(
                onSuccess = { TopicPreviewState.Loaded(it) },
                onFailure = { TopicPreviewState.Unavailable }
            ))
        }
    }
    val pinnedDescription = stringResource(R.string.forum_topic_pinned)
    val closedDescription = stringResource(R.string.forum_topic_closed)
    val regularDescription = stringResource(R.string.forum_topic_regular)
    val topicStatus = when {
        topic.isPinned && topic.isClosed -> "$pinnedDescription, $closedDescription"
        topic.isPinned -> pinnedDescription
        topic.isClosed -> closedDescription
        else -> regularDescription
    }
    val unreadDescription = if (topic.unreadCount > 0)
        stringResource(R.string.forum_topic_unread_count, topic.unreadCount) else null
    val topicStateDescription = listOfNotNull(topicStatus, unreadDescription).joinToString(", ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
            .clickable(enabled = !busy) { onTopic(topic) }
            .semantics { stateDescription = topicStateDescription }
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (topic.isPinned) Icons.Rounded.PushPin else Icons.Rounded.Forum,
                contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp)
            )
            if (topic.isClosed) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.textSecondary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(14.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(topic.title, color = palette.textPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = when {
                topic.lastMessagePreview.isNotBlank() -> topic.lastMessagePreview
                preview is TopicPreviewState.Loaded && preview.text == null -> stringResource(R.string.forum_topic_no_messages)
                preview is TopicPreviewState.Loaded && !preview.text.isNullOrBlank() -> preview.text.orEmpty()
                preview is TopicPreviewState.Loaded -> stringResource(R.string.forum_topic_recent_activity)
                topic.isClosed -> stringResource(R.string.forum_topic_closed)
                preview is TopicPreviewState.Unavailable -> stringResource(R.string.forum_topic_preview_unavailable)
                else -> stringResource(R.string.chat_history_loading)
            }
            Text(subtitle, color = palette.textSecondary, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            if (topic.isClosed && topic.lastMessagePreview.isNotBlank()) {
                Text(closedDescription, color = palette.textSecondary, fontSize = 11.sp)
            }
            if (preview is TopicPreviewState.Unavailable && !topic.isClosed && topic.lastMessagePreview.isBlank()) {
                TextButton(onClick = onRetryPreview, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.common_retry), color = palette.accent, fontSize = 12.sp)
                }
            }
        }
        if (topic.unreadCount > 0) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(palette.accent)
                .clearAndSetSemantics {}.padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center) {
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
                        DropdownMenuItem(text = { Text(stringResource(R.string.forum_rename)) },
                            onClick = { expanded = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(if (topic.isClosed) R.string.forum_reopen else R.string.forum_close)) },
                            onClick = { expanded = false; onClosed() })
                        if (topic.id != 1L) DropdownMenuItem(text = { Text(stringResource(R.string.forum_delete)) },
                            onClick = { expanded = false; onDelete() })
                    }
                    if (topic.canPin) DropdownMenuItem(
                        text = { Text(stringResource(if (topic.isPinned) R.string.forum_unpin else R.string.forum_pin)) },
                        onClick = { expanded = false; onPinned() })
                }
            }
        }
    }
}
