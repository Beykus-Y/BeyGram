package com.beykus.beygram.ui.screens.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.GlobalChatType
import com.beykus.beygram.domain.model.GlobalSearchPage
import com.beykus.beygram.domain.model.GlobalSearchPeriod
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.SharedMediaKind
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.domain.search.GlobalSearchRequest
import com.beykus.beygram.domain.search.appendDistinctBy
import com.beykus.beygram.domain.search.isStaleGlobalSearchRequest
import com.beykus.beygram.domain.search.mergeGlobalChatResults
import com.beykus.beygram.domain.search.mergeGlobalContactResults
import com.beykus.beygram.ui.components.Avatar
import com.beykus.beygram.ui.components.ChatAvatar
import com.beykus.beygram.ui.components.SearchField
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date

private const val SEARCH_DEBOUNCE_MILLIS = 400L

/**
 * Unified global search: local/server chats and contacts, public chats, then message hits.
 * Each source owns its loading/error/paging state and every request is guarded by the query and
 * session generation, so a late answer from an old query/account never reaches the screen.
 */
@Composable
fun GlobalSearchScreen(
    initialQuery: String,
    conversation: ConversationRepository,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onOpenChat: (ChatPreview) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenPublicChat: (ChatPreview) -> Unit,
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
    val generation = session.generation.value
    // A completed source is only rendered for the query/generation that produced it. This prevents
    // a single frame with old rows after the query changes and before the debounced source clears.
    val chatItems = session.chats.items.value
        .takeIf { session.chats.completedQuery == query && session.chats.completedGeneration == generation }
        .orEmpty()
    val contactItems = session.contacts.items.value
        .takeIf { session.contacts.completedQuery == query && session.contacts.completedGeneration == generation }
        .orEmpty()
    val publicChatItems = session.publicChats.items.value
        .takeIf { session.publicChats.completedQuery == query && session.publicChats.completedGeneration == generation }
        .orEmpty()
    val visibleHits = hits.takeIf {
        session.completedRequest == (query to filter) && session.completedGeneration == generation
    }.orEmpty()
    BackHandler(onBack = onBack)

    // Message filters and offset paging are unchanged; only the request guard was extended to the
    // session generation so account switches cannot publish an old page.
    LaunchedEffect(query, filter, retryVersion, generation) {
        pagingJob?.cancel()
        pagingJob = null
        loadingMore = false
        moreFailed = false
        val requestedQuery = query
        val requestedFilter = filter
        val requestedGeneration = generation
        val request = requestedQuery to requestedFilter
        val completed = session.completedRequest == request && session.completedGeneration == requestedGeneration
        if (completed && !failed) return@LaunchedEffect
        if (!completed) {
            hits = emptyList()
            chatContexts = emptyMap()
            nextOffset = ""
        }
        failed = false
        if (requestedQuery.isBlank()) {
            searching = false
            return@LaunchedEffect
        }
        val isCurrent = {
            !isStaleGlobalSearchRequest(
                GlobalSearchRequest(requestedQuery, requestedGeneration),
                query,
                session.generation.value
            ) && filter == requestedFilter
        }
        searching = true
        try {
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (!isCurrent()) return@LaunchedEffect
            conversation.searchGlobalMessages(requestedQuery, "", requestedFilter)
                .onSuccess { page ->
                    if (isCurrent()) {
                        hits = page.messages
                        chatContexts = page.chats
                        nextOffset = page.nextOffset
                    }
                }
                .onFailure { if (isCurrent()) failed = true }
            if (isCurrent()) {
                session.completedRequest = request
                session.completedGeneration = requestedGeneration
            }
        } finally {
            if (isCurrent()) searching = false
        }
    }

    LaunchedEffect(query, generation, session.chats.retryVersion.value) {
        searchGlobalSource(
            query = query,
            generation = generation,
            source = session.chats,
            currentQuery = { query },
            currentGeneration = { session.generation.value },
            merge = ::mergeGlobalChatResults,
            search = { text, limit -> conversation.searchGlobalChats(text, limit) }
        )
    }
    LaunchedEffect(query, generation, session.contacts.retryVersion.value) {
        searchGlobalSource(
            query = query,
            generation = generation,
            source = session.contacts,
            currentQuery = { query },
            currentGeneration = { session.generation.value },
            merge = ::mergeGlobalContactResults,
            search = { text, limit -> conversation.searchGlobalContacts(text, limit) }
        )
    }
    LaunchedEffect(query, generation, session.publicChats.retryVersion.value) {
        searchGlobalSource(
            query = query,
            generation = generation,
            source = session.publicChats,
            currentQuery = { query },
            currentGeneration = { session.generation.value },
            merge = ::mergeGlobalChatResults,
            search = { text, _ -> conversation.searchGlobalPublicChats(text) }
        )
    }

    fun <T> loadMore(
        source: GlobalSearchSource<T>,
        search: suspend (String, Int) -> Result<GlobalSearchPage<T>>,
        merge: (List<T>, List<T>) -> List<T>
    ) {
        if (!source.canLoadMore()) return
        val requestedQuery = query
        val requestedGeneration = session.generation.value
        val requestedLimit = source.nextLimit()
        val isCurrent = {
            !isStaleGlobalSearchRequest(
                GlobalSearchRequest(requestedQuery, requestedGeneration),
                query,
                session.generation.value
            )
        }
        source.loadingMore.value = true
        source.moreFailed.value = false
        source.moreJob?.cancel()
        source.moreJob = scope.launch {
            try {
                search(requestedQuery, requestedLimit)
                    .onSuccess { page ->
                        if (isCurrent()) {
                            source.items.value = merge(source.items.value, page.items)
                            source.limit.value = requestedLimit
                            source.hasMore.value = maxOf(page.totalCount, source.items.value.size) > requestedLimit
                        }
                    }
                    .onFailure { if (isCurrent()) source.moreFailed.value = true }
            } finally {
                if (isCurrent()) source.loadingMore.value = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        ProfileToolbar(stringResource(R.string.global_search_title), onBack)
        SearchField(query = query, onChange = { query = it }, placeholder = stringResource(R.string.chats_search_placeholder))
        if (query.isBlank()) {
            Text(
                stringResource(R.string.global_search_idle),
                color = palette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item("chats-header") {
                    SearchSectionHeader(
                        title = stringResource(R.string.global_search_section_chats),
                        count = chatItems.size,
                        loading = session.chats.searching.value,
                        failed = session.chats.failed.value,
                        onRetry = { session.chats.retryVersion.value++ }
                    )
                }
                if (chatItems.isEmpty()) {
                    item("chats-state") {
                        SearchSourceState(
                            loading = session.chats.searching.value,
                            failed = session.chats.failed.value,
                            emptyText = stringResource(R.string.global_search_section_empty_chats),
                            failedText = stringResource(R.string.global_search_source_failed, stringResource(R.string.global_search_section_chats))
                        )
                    }
                } else {
                    items(chatItems, key = { "chat:${it.id}" }) { chat ->
                        GlobalChatRow(chat, observeFile, downloadFile) { onOpenChat(chat) }
                    }
                    if (session.chats.hasMore.value && session.chats.supportsMore) {
                        item("chats-more") {
                            SearchMoreRow(
                                loading = session.chats.loadingMore.value,
                                failed = session.chats.moreFailed.value,
                                onClick = { loadMore(session.chats, conversation::searchGlobalChats, ::mergeGlobalChatResults) }
                            )
                        }
                    }
                }

                item("contacts-header") {
                    SearchSectionHeader(
                        title = stringResource(R.string.global_search_section_contacts),
                        count = contactItems.size,
                        loading = session.contacts.searching.value,
                        failed = session.contacts.failed.value,
                        onRetry = { session.contacts.retryVersion.value++ }
                    )
                }
                if (contactItems.isEmpty()) {
                    item("contacts-state") {
                        SearchSourceState(
                            loading = session.contacts.searching.value,
                            failed = session.contacts.failed.value,
                            emptyText = stringResource(R.string.global_search_section_empty_contacts),
                            failedText = stringResource(R.string.global_search_source_failed, stringResource(R.string.global_search_section_contacts))
                        )
                    }
                } else {
                    items(contactItems, key = { "contact:${it.userId}" }) { contact ->
                        GlobalContactRow(contact, observeFile, downloadFile) { onOpenProfile(contact.userId) }
                    }
                    if (session.contacts.hasMore.value) {
                        item("contacts-more") {
                            SearchMoreRow(
                                loading = session.contacts.loadingMore.value,
                                failed = session.contacts.moreFailed.value,
                                onClick = { loadMore(session.contacts, conversation::searchGlobalContacts, ::mergeGlobalContactResults) }
                            )
                        }
                    }
                }

                item("public-header") {
                    SearchSectionHeader(
                        title = stringResource(R.string.global_search_section_public),
                        count = publicChatItems.size,
                        loading = session.publicChats.searching.value,
                        failed = session.publicChats.failed.value,
                        onRetry = { session.publicChats.retryVersion.value++ }
                    )
                }
                if (publicChatItems.isEmpty()) {
                    item("public-state") {
                        SearchSourceState(
                            loading = session.publicChats.searching.value,
                            failed = session.publicChats.failed.value,
                            emptyText = stringResource(R.string.global_search_section_empty_public),
                            failedText = stringResource(R.string.global_search_source_failed, stringResource(R.string.global_search_section_public))
                        )
                    }
                } else {
                    items(publicChatItems, key = { "public:${it.id}" }) { chat ->
                        GlobalChatRow(chat, observeFile, downloadFile) { onOpenPublicChat(chat) }
                    }
                }

                item("messages-header") {
                    SearchSectionHeader(
                        title = stringResource(R.string.global_search_section_messages),
                        count = visibleHits.size,
                        loading = searching,
                        failed = failed,
                        onRetry = { retryVersion++ }
                    )
                }
                item("messages-filters") {
                    Column(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
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
                    }
                }
                if (visibleHits.isEmpty()) {
                    item("messages-state") {
                        SearchSourceState(
                            loading = searching,
                            failed = failed,
                            emptyText = stringResource(R.string.global_search_empty),
                            failedText = stringResource(R.string.global_search_failed)
                        )
                    }
                } else {
                    items(visibleHits, key = { "message:${it.chatId}:${it.id}" }) { message ->
                        GlobalMessageRow(
                            message = message,
                            chat = chatContexts[message.chatId],
                            dateFormat = dateFormat,
                            observeFile = observeFile,
                            downloadFile = downloadFile,
                            onClick = { onOpenMessage(message.chatId, message.forumTopicId, message.id) }
                        )
                    }
                    if (nextOffset.isNotEmpty()) {
                        item("messages-more") {
                            Column(Modifier.fillMaxWidth()) {
                                TextButton(
                                    enabled = !loadingMore,
                                    onClick = {
                                        if (loadingMore) return@TextButton
                                        loadingMore = true
                                        moreFailed = false
                                        val requestedQuery = query
                                        val requestedFilter = filter
                                        val requestedGeneration = session.generation.value
                                        val requestedOffset = nextOffset
                                        val isCurrent = {
                                            query == requestedQuery &&
                                                filter == requestedFilter &&
                                                session.generation.value == requestedGeneration
                                        }
                                        pagingJob?.cancel()
                                        pagingJob = scope.launch {
                                            try {
                                                conversation.searchGlobalMessages(requestedQuery, requestedOffset, requestedFilter)
                                                    .onSuccess { page ->
                                                        if (isCurrent()) {
                                                            hits = appendDistinctBy(hits, page.messages) { it.chatId to it.id }
                                                            chatContexts = chatContexts + page.chats
                                                            nextOffset = page.nextOffset
                                                        }
                                                    }
                                                    .onFailure { if (isCurrent()) moreFailed = true }
                                            } finally {
                                                if (isCurrent()) loadingMore = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) { Text(stringResource(R.string.global_search_more)) }
                                if (moreFailed) {
                                    Text(
                                        stringResource(R.string.global_search_more_failed),
                                        color = palette.textSecondary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Runs one independent source. A source keeps its result through Chat → Back; the query/generation
 * pair is the stale-request guard and also invalidates a late answer after an account switch.
 */
private suspend fun <T> searchGlobalSource(
    query: String,
    generation: Long,
    source: GlobalSearchSource<T>,
    currentQuery: () -> String,
    currentGeneration: () -> Long,
    merge: (List<T>, List<T>) -> List<T>,
    search: suspend (String, Int) -> Result<GlobalSearchPage<T>>
) {
    val isCurrent = {
        !isStaleGlobalSearchRequest(
            GlobalSearchRequest(query, generation),
            currentQuery(),
            currentGeneration()
        )
    }
    if (source.completedQuery == query && source.completedGeneration == generation && !source.failed.value) return
    if (source.completedQuery != query || source.completedGeneration != generation) source.clearForNewQuery()
    source.moreJob?.cancel()
    source.moreJob = null
    source.loadingMore.value = false
    source.failed.value = false
    if (query.isBlank()) {
        source.searching.value = false
        return
    }
    source.searching.value = true
    try {
        delay(SEARCH_DEBOUNCE_MILLIS)
        if (!isCurrent()) return
        search(query, source.limit.value)
            .onSuccess { page ->
                if (isCurrent()) {
                    source.items.value = merge(source.items.value, page.items)
                    source.hasMore.value = maxOf(page.totalCount, source.items.value.size) > source.limit.value
                }
            }
            .onFailure { if (isCurrent()) source.failed.value = true }
        if (isCurrent()) {
            source.completedQuery = query
            source.completedGeneration = generation
        }
    } finally {
        if (isCurrent()) source.searching.value = false
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.global_search_section_count, title, count),
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = palette.accent,
                strokeWidth = 1.5.dp
            )
        }
        if (failed) {
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.common_retry), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SearchSourceState(
    loading: Boolean,
    failed: Boolean,
    emptyText: String,
    failedText: String
) {
    val palette = beyGramPalette()
    Text(
        when {
            loading -> stringResource(R.string.global_search_loading)
            failed -> failedText
            else -> emptyText
        },
        color = palette.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SearchMoreRow(loading: Boolean, failed: Boolean, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            enabled = !loading,
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) { Text(stringResource(R.string.global_search_more)) }
        if (failed) {
            Text(
                stringResource(R.string.global_search_more_failed),
                color = palette.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun GlobalChatRow(
    chat: ChatPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(chat, 42.dp, observeFile, downloadFile)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                chat.title,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(chatTypeLabel(chat)),
                color = palette.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GlobalContactRow(
    contact: ContactPreview,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatar(
            contact.initials,
            Color(contact.avatarColorArgb),
            42.dp,
            contact.photo,
            observeFile,
            downloadFile
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                contact.title.ifBlank { stringResource(R.string.user_name_unknown) },
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contact.subtitle.isNotBlank()) {
                Text(
                    contact.subtitle,
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GlobalMessageRow(
    message: ChatMessage,
    chat: ChatPreview?,
    dateFormat: DateFormat,
    observeFile: (Int) -> StateFlow<MediaFileState>,
    downloadFile: (Int) -> Unit,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Text(stringResource(chatTypeLabel(chat)), color = palette.textSecondary, fontSize = 11.sp)
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

private fun chatTypeLabel(chat: ChatPreview): Int = when {
    chat.isChannel -> R.string.global_search_chat_channel
    chat.isGroup -> R.string.global_search_chat_group
    else -> R.string.global_search_chat_private
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
