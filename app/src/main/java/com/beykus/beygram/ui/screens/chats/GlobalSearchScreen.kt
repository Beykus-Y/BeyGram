package com.beykus.beygram.ui.screens.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.GlobalChatType
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.GlobalSearchPeriod
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.domain.search.appendDistinctBy
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date

private const val SEARCH_DEBOUNCE_MILLIS = 400L

/**
 * Hoisted across GlobalSearch → Chat → Back, so paging and filters are not
 * recreated whenever the navigation branch leaves composition.
 */
class GlobalSearchSession(initialQuery: String) {
    val query = mutableStateOf(initialQuery)
    val filter = mutableStateOf(GlobalSearchFilter())
    val hits = mutableStateOf<List<ChatMessage>>(emptyList())
    val chatContexts = mutableStateOf<Map<Long, ChatPreview>>(emptyMap())
    val nextOffset = mutableStateOf("")
    val loadingMore = mutableStateOf(false)
    val moreFailed = mutableStateOf(false)
    val pagingJob = mutableStateOf<Job?>(null)
    val searching = mutableStateOf(false)
    val failed = mutableStateOf(false)
    val retryVersion = mutableStateOf(0)
    var completedRequest: Pair<String, GlobalSearchFilter>? = null

    companion object {
        // Only primitive enum identifiers enter Android saved state. Search pages are
        // deliberately reloaded after process recreation; filters are not lost.
        val stateSaver = androidx.compose.runtime.saveable.listSaver<GlobalSearchSession, String>(
            save = { state ->
                listOf(
                    state.query.value,
                    state.filter.value.kind?.name.orEmpty(),
                    state.filter.value.chatType?.name.orEmpty(),
                    state.filter.value.period.name
                )
            },
            restore = { saved ->
                GlobalSearchSession(saved.getOrElse(0) { "" }).also { state ->
                    state.filter.value = GlobalSearchFilter(
                        kind = SharedMediaKind.entries.firstOrNull { it.name == saved.getOrNull(1) },
                        chatType = GlobalChatType.entries.firstOrNull { it.name == saved.getOrNull(2) },
                        period = GlobalSearchPeriod.entries.firstOrNull { it.name == saved.getOrNull(3) }
                            ?: GlobalSearchPeriod.Any
                    )
                }
            }
        )
    }

    fun reset(newQuery: String) {
        pagingJob.value?.cancel()
        query.value = newQuery
        filter.value = GlobalSearchFilter()
        hits.value = emptyList()
        chatContexts.value = emptyMap()
        nextOffset.value = ""
        loadingMore.value = false
        moreFailed.value = false
        searching.value = false
        failed.value = false
        completedRequest = null
        retryVersion.value++
    }
}

/**
 * Поиск сообщений по всем чатам аккаунта. Каждое изменение текста перезапускает LaunchedEffect,
 * поэтому запрос устаревшего текста отменяется и его результат не попадает в выдачу.
 */
@Composable
fun GlobalSearchScreen(
    initialQuery: String,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenMessage: (chatId: Long, forumTopicId: Long, messageId: Long) -> Unit,
    onBack: () -> Unit,
    session: GlobalSearchSession = remember { GlobalSearchSession(initialQuery) }
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentLocale = context.resources.configuration.locales[0]
    val dateFormat = remember(currentLocale) { DateFormat.getDateInstance(DateFormat.MEDIUM, currentLocale) }
    var query by session.query
    var filter by session.filter
    var hits by session.hits
    var chatContexts by session.chatContexts
    var nextOffset by session.nextOffset
    var loadingMore by session.loadingMore
    var moreFailed by session.moreFailed
    var pagingJob by session.pagingJob
    var searching by session.searching
    var failed by session.failed
    var retryVersion by session.retryVersion
    BackHandler(onBack = onBack)

    LaunchedEffect(query, filter, retryVersion) {
        pagingJob?.cancel()
        pagingJob = null
        loadingMore = false
        moreFailed = false
        val request = query to filter
        if (request == session.completedRequest && !failed) return@LaunchedEffect
        if (session.completedRequest != request) {
            hits = emptyList()
            chatContexts = emptyMap()
            nextOffset = ""
        }
        failed = false
        if (query.isBlank()) { searching = false; return@LaunchedEffect }
        searching = true
        try {
            delay(SEARCH_DEBOUNCE_MILLIS)
            conversation.searchGlobalMessages(query, "", filter)
                .onSuccess { page ->
                    hits = page.messages
                    chatContexts = page.chats
                    nextOffset = page.nextOffset
                }
                .onFailure { failed = true }
            session.completedRequest = request
        } finally {
            searching = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        ProfileToolbar(stringResource(R.string.global_search_title), onBack)
        SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.chats_search_placeholder))
        SearchChips(
            options = SharedMediaKind.entries.map { it to stringResource(kindLabel(it)) },
            selected = filter.kind,
            all = stringResource(R.string.global_search_filter_all_types),
            onSelect = { filter = filter.copy(kind = it) }
        )
        SearchChips(
            options = GlobalChatType.entries.map { it to stringResource(chatTypeLabel(it)) },
            selected = filter.chatType,
            all = stringResource(R.string.global_search_filter_all_chats),
            onSelect = { filter = filter.copy(chatType = it) }
        )
        SearchChips(
            options = GlobalSearchPeriod.entries.filter { it != GlobalSearchPeriod.Any }
                .map { it to stringResource(periodLabel(it)) },
            selected = filter.period.takeIf { it != GlobalSearchPeriod.Any },
            all = stringResource(R.string.global_search_filter_any_period),
            onSelect = { filter = filter.copy(period = it ?: GlobalSearchPeriod.Any) }
        )
        val empty = when {
            query.isBlank() -> stringResource(R.string.global_search_idle)
            searching -> stringResource(R.string.global_search_loading)
            failed -> stringResource(R.string.global_search_failed)
            hits.isEmpty() -> stringResource(R.string.global_search_empty)
            else -> null
        }
        if (empty != null) {
            Text(empty, color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
        }
        if (failed && query.isNotBlank()) {
            TextButton(onClick = { retryVersion++ }, enabled = !searching) {
                Text(stringResource(R.string.common_retry))
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(hits, key = { "${it.chatId}:${it.id}" }) { message ->
                val chat = chatContexts[message.chatId]
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onOpenMessage(message.chatId, message.forumTopicId, message.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (chat != null) {
                        ChatAvatar(chat, 42.dp, observeFile, downloadFile)
                    } else {
                        Avatar("?", palette.accent, 42.dp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                chat?.title ?: stringResource(R.string.global_search_chat_unknown),
                                color = palette.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            if (chat != null) {
                                val typeLabel = when {
                                    chat.isChannel -> R.string.global_search_chat_channel
                                    chat.isGroup -> R.string.global_search_chat_group
                                    else -> R.string.global_search_chat_private
                                }
                                Text(stringResource(typeLabel), color = palette.textSecondary, fontSize = 11.sp)
                            }
                        }
                        if (message.senderName.isNotBlank()) {
                            Text(message.senderName, color = palette.accentInk, fontSize = 12.sp, maxLines = 1)
                        }
                        val preview = message.text.ifBlank {
                            if (message.media != null) stringResource(R.string.global_search_attachment) else ""
                        }
                        Text(preview, color = palette.textPrimary, fontSize = 14.sp, maxLines = 2)
                        val date = if (message.dateSeconds > 0) dateFormat.format(Date(message.dateSeconds * 1000L)) else ""
                        val timestamp = listOf(date, message.time).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(timestamp, color = palette.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            if (nextOffset.isNotEmpty() && hits.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            if (loadingMore) return@TextButton
                            loadingMore = true
                            moreFailed = false
                            val requestedQuery = query
                            val requestedFilter = filter
                            pagingJob = scope.launch {
                                conversation.searchGlobalMessages(requestedQuery, nextOffset, requestedFilter)
                                    .onSuccess { page ->
                                        hits = appendDistinctBy(hits, page.messages) { it.chatId to it.id }
                                        chatContexts = chatContexts + page.chats
                                        nextOffset = page.nextOffset
                                    }
                                    .onFailure { moreFailed = true }
                                loadingMore = false
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) { Text(stringResource(R.string.global_search_more)) }
                    if (moreFailed) {
                        Text(stringResource(R.string.global_search_more_failed), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }

}

@Composable
private fun <T> SearchChips(options: List<Pair<T, String>>, selected: T?, all: String, onSelect: (T?) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { FilterChip(title = all, selected = selected == null, count = null, onClick = { onSelect(null) }) }
        items(options) { (option, label) ->
            FilterChip(title = label, selected = selected == option, count = null, onClick = { onSelect(option) })
        }
    }
}

private fun kindLabel(kind: SharedMediaKind): Int = when (kind) {
    SharedMediaKind.Media -> R.string.global_search_filter_media
    SharedMediaKind.Files -> R.string.global_search_filter_files
    SharedMediaKind.Links -> R.string.global_search_filter_links
    SharedMediaKind.Voice -> R.string.global_search_filter_voice
    SharedMediaKind.Music -> R.string.global_search_filter_music
}

private fun chatTypeLabel(type: GlobalChatType): Int = when (type) {
    GlobalChatType.Private -> R.string.global_search_filter_private
    GlobalChatType.Group -> R.string.global_search_filter_group
    GlobalChatType.Channel -> R.string.global_search_filter_channel
}

private fun periodLabel(period: GlobalSearchPeriod): Int = when (period) {
    GlobalSearchPeriod.Any -> R.string.global_search_filter_any_period
    GlobalSearchPeriod.Week -> R.string.global_search_filter_week
    GlobalSearchPeriod.Month -> R.string.global_search_filter_month
}
