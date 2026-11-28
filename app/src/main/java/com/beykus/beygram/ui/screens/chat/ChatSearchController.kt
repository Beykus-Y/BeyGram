package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatSearchFilter
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.domain.search.appendDistinctBy
import com.beykus.beygram.domain.search.isCurrentChatSearch
import com.beykus.beygram.domain.search.nextChatSearchCursor
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
internal class ChatSearchController(
    private val chatId: Long,
    private val forumTopicId: Long,
    private val conversation: ConversationRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val cancelActiveJump: () -> Unit,
    private val onJumpToMessage: (messageId: Long, failureRes: Int) -> Unit,
    private val onError: (String) -> Unit,
    searchingState: MutableState<Boolean>,
    searchQueryState: MutableState<String>,
    searchFilterState: MutableState<ChatSearchFilter>,
    searchResultsState: MutableState<List<ChatMessage>>,
    searchNextIdState: MutableLongState,
    searchTotalCountState: MutableIntState,
    searchGenerationState: MutableIntState,
    searchBusyState: MutableState<Boolean>,
    searchErrorState: MutableState<String?>,
    searchRefreshingState: MutableState<Boolean>,
    searchRetryState: MutableIntState,
    searchMatchSessionState: MutableState<ChatSearchMatchSession>,
    searchMatchGenerationState: MutableIntState
) {
    var searching by searchingState
    var searchQuery by searchQueryState
    var searchFilter by searchFilterState
    var searchResults by searchResultsState
    var searchNextId by searchNextIdState
    var searchTotalCount by searchTotalCountState
    var searchGeneration by searchGenerationState
    var searchBusy by searchBusyState
    var searchError by searchErrorState
    var searchRefreshing by searchRefreshingState
    var searchRetry by searchRetryState
    var searchMatchSession by searchMatchSessionState
    var searchMatchGeneration by searchMatchGenerationState

    fun openSearch() {
        searching = true
    }

    fun closeSearch(clearMatchSession: Boolean = true) {
        searching = false
        searchQuery = ""
        searchFilter = ChatSearchFilter()
        searchResults = emptyList()
        searchNextId = 0L
        searchTotalCount = 0
        searchGeneration++
        searchBusy = false
        searchError = null
        searchRefreshing = false
        if (clearMatchSession) {
            searchMatchGeneration++
            searchMatchSession = ChatSearchMatchSession()
            cancelActiveJump()
        }
    }

    fun retrySearch() {
        searchRetry++
    }

    fun resetFilters() {
        searchFilter = ChatSearchFilter()
    }

    fun loadMoreResults() {
        val needle = searchQuery.trim()
        val requestedFilter = searchFilter
        val cursor = searchNextId
        val generation = ++searchGeneration
        scope.launch {
            searchBusy = true
            searchError = null
            (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chatId, forumTopicId, needle, cursor, requestedFilter)
            else conversation.searchMessages(chatId, needle, cursor, requestedFilter))
                .onSuccess { page ->
                    if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                        searchResults = appendDistinctBy(searchResults, page.messages) { it.id }
                        // Повторённый курсор означает ту же страницу: останавливаем автодогрузку, а не крутим её по кругу.
                        searchNextId = nextChatSearchCursor(cursor, page.nextFromMessageId)
                    }
                }
                .onFailure { failure ->
                    if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                        searchError = failure.userMessage(context, R.string.chat_search_failed)
                    }
                }
            if (generation == searchGeneration && isCurrentChatSearch(needle, requestedFilter, searchQuery, searchFilter)) {
                searchBusy = false
            }
        }
    }

    fun openSearchResult(message: ChatMessage, scrollIndex: Int, scrollOffset: Int) {
        val snapshot = searchResults
        val index = snapshot.indexOfFirst { it.id == message.id }.coerceAtLeast(0)
        val total = searchTotalCount
        val nextId = searchNextId
        val querySnapshot = searchQuery
        val filterSnapshot = searchFilter
        closeSearch(clearMatchSession = false)
        searchMatchGeneration++
        searchMatchSession = ChatSearchMatchSession(
            query = querySnapshot,
            filter = filterSnapshot,
            results = snapshot,
            index = index,
            totalCount = total,
            nextFromMessageId = nextId,
            scrollIndex = scrollIndex,
            scrollOffset = scrollOffset
        )
        onJumpToMessage(message.id, R.string.err_chat_history_load_failed)
    }

    fun reopenSearchFromMatches() {
        val session = searchMatchSession
        cancelActiveJump()
        searchMatchGeneration++
        searchQuery = session.query
        searchFilter = session.filter
        // Сессия остаётся до закрытия поиска: по ней восстанавливается выдача и позиция списка.
        searching = true
    }

    fun stepNewerSearchMatch() {
        val next = searchMatchSession.stepNewer()
        if (next == searchMatchSession) return
        searchMatchSession = next
        next.currentMessageId?.let { onJumpToMessage(it, R.string.err_chat_history_load_failed) }
    }

    fun stepOlderSearchMatch() {
        val session = searchMatchSession
        if (!session.canStepOlder) return
        if (session.index < session.results.lastIndex) {
            val next = session.stepOlderLoaded()
            searchMatchSession = next
            next.currentMessageId?.let { onJumpToMessage(it, R.string.err_chat_history_load_failed) }
            return
        }
        val needle = session.query.trim()
        val cursor = session.nextFromMessageId
        if (cursor == 0L) return
        val generation = ++searchMatchGeneration
        searchMatchSession = session.copy(busy = true)
        scope.launch {
            (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chatId, forumTopicId, needle, cursor, session.filter)
            else conversation.searchMessages(chatId, needle, cursor, session.filter))
                .onSuccess { page ->
                    if (generation != searchMatchGeneration || !searchMatchSession.isActive) return@onSuccess
                    val updated = searchMatchSession.appendPage(page.messages, page.nextFromMessageId)
                    searchMatchSession = updated
                    if (updated.index != session.index) {
                        updated.currentMessageId?.let { onJumpToMessage(it, R.string.err_chat_history_load_failed) }
                    }
                }
                .onFailure { failure ->
                    if (generation != searchMatchGeneration || !searchMatchSession.isActive) return@onFailure
                    searchMatchSession = searchMatchSession.copy(busy = false)
                    onError(failure.userMessage(context, R.string.chat_search_failed))
                }
        }
    }
}

@Composable
internal fun rememberChatSearchController(
    chatId: Long,
    forumTopicId: Long,
    conversation: ConversationRepository,
    context: Context,
    scope: CoroutineScope,
    cancelActiveJump: () -> Unit,
    onJumpToMessage: (messageId: Long, failureRes: Int) -> Unit,
    onError: (String) -> Unit
): ChatSearchController {
    val searchingState = rememberSaveable(chatId, forumTopicId) { mutableStateOf(false) }
    val searchQueryState = rememberSaveable(chatId, forumTopicId) { mutableStateOf("") }
    val searchFilterState = rememberSaveable(chatId, forumTopicId, stateSaver = ChatSearchFilterSaver) { mutableStateOf(ChatSearchFilter()) }
    val searchResultsState = remember(chatId, forumTopicId) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    val searchNextIdState = remember(chatId, forumTopicId) { mutableLongStateOf(0L) }
    val searchTotalCountState = remember(chatId, forumTopicId) { mutableIntStateOf(0) }
    val searchGenerationState = remember(chatId, forumTopicId) { mutableIntStateOf(0) }
    val searchBusyState = remember(chatId, forumTopicId) { mutableStateOf(false) }
    val searchErrorState = remember(chatId, forumTopicId) { mutableStateOf<String?>(null) }
    val searchRefreshingState = remember(chatId, forumTopicId) { mutableStateOf(false) }
    val searchRetryState = remember(chatId, forumTopicId) { mutableIntStateOf(0) }
    val searchMatchSessionState = remember(chatId, forumTopicId) { mutableStateOf(ChatSearchMatchSession()) }
    val searchMatchGenerationState = remember(chatId, forumTopicId) { mutableIntStateOf(0) }

    val currentCancelJump = androidx.compose.runtime.rememberUpdatedState(cancelActiveJump)
    val currentJump = androidx.compose.runtime.rememberUpdatedState(onJumpToMessage)
    val currentError = androidx.compose.runtime.rememberUpdatedState(onError)
    val controller = remember(chatId, forumTopicId, conversation, context, scope) {
        ChatSearchController(
            chatId = chatId,
            forumTopicId = forumTopicId,
            conversation = conversation,
            context = context,
            scope = scope,
            cancelActiveJump = { currentCancelJump.value() },
            onJumpToMessage = { id, failure -> currentJump.value(id, failure) },
            onError = { currentError.value(it) },
            searchingState = searchingState,
            searchQueryState = searchQueryState,
            searchFilterState = searchFilterState,
            searchResultsState = searchResultsState,
            searchNextIdState = searchNextIdState,
            searchTotalCountState = searchTotalCountState,
            searchGenerationState = searchGenerationState,
            searchBusyState = searchBusyState,
            searchErrorState = searchErrorState,
            searchRefreshingState = searchRefreshingState,
            searchRetryState = searchRetryState,
            searchMatchSessionState = searchMatchSessionState,
            searchMatchGenerationState = searchMatchGenerationState
        )
    }

    // Results refresh after a short pause in typing; a new query or filter replaces the pending request.
    LaunchedEffect(controller.searching, controller.searchQuery, controller.searchFilter, controller.searchRetry) {
        val generation = ++controller.searchGeneration
        controller.searchNextId = 0L
        controller.searchError = null
        val needle = controller.searchQuery.trim()
        val requestedFilter = controller.searchFilter
        val active = needle.isNotEmpty() || requestedFilter.isActive
        controller.searchBusy = controller.searching && active
        if (!controller.searching || !active) {
            controller.searchResults = emptyList()
            controller.searchTotalCount = 0
            controller.searchRefreshing = false
            return@LaunchedEffect
        }
        val restore = controller.searchMatchSession
        if (restore.canRestoreResults(controller.searchQuery, requestedFilter)) {
            // Возврат из найденного сообщения: прежние результаты без повторного запроса, позицию восстановит список.
            controller.searchResults = restore.results
            controller.searchNextId = restore.nextFromMessageId
            controller.searchTotalCount = restore.totalCount
            controller.searchBusy = false
            controller.searchRefreshing = false
            return@LaunchedEffect
        }
        // Прежние результаты остаются на месте, но помечены устаревшими до ответа: без вспышки пустого экрана.
        controller.searchRefreshing = controller.searchResults.isNotEmpty()
        delay(300)
        (if (forumTopicId > 0L) conversation.searchForumTopicMessages(chatId, forumTopicId, needle, 0L, requestedFilter)
        else conversation.searchMessages(chatId, needle, 0L, requestedFilter))
            .onSuccess { page ->
                if (generation == controller.searchGeneration && isCurrentChatSearch(needle, requestedFilter, controller.searchQuery, controller.searchFilter)) {
                    controller.searchResults = page.messages
                    controller.searchNextId = page.nextFromMessageId
                    // TDLib's total predates the local date filter, so the UI falls back to the loaded count then.
                    controller.searchTotalCount = if (requestedFilter.hasDateRange) 0 else page.totalCount
                    controller.searchRefreshing = false
                }
            }
            .onFailure { failure ->
                if (generation == controller.searchGeneration && isCurrentChatSearch(needle, requestedFilter, controller.searchQuery, controller.searchFilter)) {
                    controller.searchResults = emptyList()
                    controller.searchTotalCount = 0
                    controller.searchRefreshing = false
                    controller.searchError = failure.userMessage(context, R.string.chat_search_failed)
                }
            }
        if (generation == controller.searchGeneration && isCurrentChatSearch(needle, requestedFilter, controller.searchQuery, controller.searchFilter)) {
            controller.searchBusy = false
        }
    }

    return controller
}
