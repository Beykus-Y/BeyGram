package com.beykus.beygram.ui.screens.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.beykus.beygram.domain.model.GlobalChatType
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.GlobalSearchPeriod
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.domain.search.appendDistinctBy
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.MessageContextDialog
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

private const val SEARCH_DEBOUNCE_MILLIS = 400L

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
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    // Фильтры сбрасываются при повороте экрана вместе с выдачей: они не сохраняются через rememberSaveable.
    var filter by remember { mutableStateOf(GlobalSearchFilter()) }
    var hits by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var nextOffset by remember { mutableStateOf("") }
    var loadingMore by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var pagingJob by remember { mutableStateOf<Job?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<ChatMessage?>(null) }
    BackHandler(onBack = onBack)

    LaunchedEffect(query, filter) {
        // Догрузка прошлого запроса или фильтра не должна дописаться к выдаче нового.
        pagingJob?.cancel()
        pagingJob = null
        loadingMore = false
        moreFailed = false
        hits = emptyList()
        nextOffset = ""
        failed = false
        if (query.isBlank()) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MILLIS)
        searching = true
        conversation.searchGlobalMessages(query, "", filter)
            .onSuccess { page ->
                hits = page.messages
                nextOffset = page.nextOffset
            }
            .onFailure { failed = true }
        searching = false
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
            all = stringResource(R.string.global_search_filter_any),
            onSelect = { filter = filter.copy(kind = it) }
        )
        SearchChips(
            options = GlobalChatType.entries.map { it to stringResource(chatTypeLabel(it)) },
            selected = filter.chatType,
            all = stringResource(R.string.global_search_filter_any),
            onSelect = { filter = filter.copy(chatType = it) }
        )
        SearchChips(
            options = GlobalSearchPeriod.entries.filter { it != GlobalSearchPeriod.Any }
                .map { it to stringResource(periodLabel(it)) },
            selected = filter.period.takeIf { it != GlobalSearchPeriod.Any },
            all = stringResource(R.string.global_search_filter_any),
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
        LazyColumn(Modifier.fillMaxWidth()) {
            items(hits, key = { "${it.chatId}:${it.id}" }) { message ->
                Column(
                    Modifier.fillMaxWidth().clickable { opened = message }.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    val preview = message.text.ifBlank { if (message.media != null) stringResource(R.string.global_search_attachment) else "" }
                    Text(preview, color = palette.textPrimary, fontSize = 15.sp, maxLines = 2)
                    Text(message.time, color = palette.textSecondary, fontSize = 12.sp)
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

    opened?.let { message ->
        MessageContextDialog(
            chatId = message.chatId,
            targetId = message.id,
            conversation = conversation,
            observeFile = observeFile,
            downloadFile = downloadFile,
            onClose = { opened = null }
        )
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
    GlobalSearchPeriod.Any -> R.string.global_search_filter_any
    GlobalSearchPeriod.Week -> R.string.global_search_filter_week
    GlobalSearchPeriod.Month -> R.string.global_search_filter_month
}
