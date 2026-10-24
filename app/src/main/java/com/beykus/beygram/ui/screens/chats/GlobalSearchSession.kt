package com.beykus.beygram.ui.screens.chats

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.GlobalChatType
import com.beykus.beygram.domain.model.GlobalSearchFilter
import com.beykus.beygram.domain.model.GlobalSearchPeriod
import com.beykus.beygram.domain.model.SharedMediaKind
import kotlinx.coroutines.Job

/** People/chat sources have no offset in TDLib, so a next page asks for a larger limit. */
internal const val GLOBAL_SEARCH_PAGE_SIZE = 20
internal const val GLOBAL_SEARCH_PEOPLE_MAX = 50

/**
 * One independent search source. Loading, failure and retry never leak between chats, contacts,
 * public chats and messages. [completedQuery] plus [completedGeneration] let the screen restore a
 * source from the hoisted session after Chat → Back without issuing the same search again.
 */
internal class GlobalSearchSource<T>(
    val supportsMore: Boolean = true,
    private val initialLimit: Int = GLOBAL_SEARCH_PAGE_SIZE
) {
    val items = mutableStateOf<List<T>>(emptyList())
    val searching = mutableStateOf(false)
    val failed = mutableStateOf(false)
    val retryVersion = mutableIntStateOf(0)
    val limit = mutableIntStateOf(initialLimit)
    val hasMore = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)
    val moreFailed = mutableStateOf(false)
    var completedQuery: String? = null
    var completedGeneration = -1L
    var moreJob: Job? = null

    fun clearForNewQuery() {
        moreJob?.cancel()
        moreJob = null
        items.value = emptyList()
        failed.value = false
        limit.value = initialLimit
        hasMore.value = false
        loadingMore.value = false
        moreFailed.value = false
        completedQuery = null
        completedGeneration = -1L
    }

    fun reset() {
        clearForNewQuery()
        searching.value = false
        retryVersion.value = 0
    }

    fun canLoadMore(): Boolean = supportsMore && hasMore.value && !loadingMore.value && limit.value < GLOBAL_SEARCH_PEOPLE_MAX

    fun nextLimit(): Int = (limit.value + GLOBAL_SEARCH_PAGE_SIZE).coerceAtMost(GLOBAL_SEARCH_PEOPLE_MAX)
}

/**
 * Hoisted across GlobalSearch → Chat → Back, so paging and filters are not recreated whenever the
 * navigation branch leaves composition. Search pages are deliberately reloaded after process
 * recreation; filters and the query are restored.
 */
class GlobalSearchSession(initialQuery: String) {
    val query = mutableStateOf(initialQuery)
    val filter = mutableStateOf(GlobalSearchFilter())

    /** Changes on every explicit reset (new query, account switch), invalidating late answers. */
    val generation = mutableLongStateOf(0L)

    internal val chats = GlobalSearchSource<ChatPreview>()
    internal val contacts = GlobalSearchSource<ContactPreview>()
    // SearchPublicChats has no offset or limit parameter, so this section has no "show more".
    internal val publicChats = GlobalSearchSource<ChatPreview>(supportsMore = false)

    // Message search keeps its own offset paging and message metadata cache.
    val hits = mutableStateOf<List<ChatMessage>>(emptyList())
    val chatContexts = mutableStateOf<Map<Long, ChatPreview>>(emptyMap())
    val nextOffset = mutableStateOf("")
    val loadingMore = mutableStateOf(false)
    val moreFailed = mutableStateOf(false)
    val pagingJob = mutableStateOf<Job?>(null)
    val searching = mutableStateOf(false)
    val failed = mutableStateOf(false)
    val retryVersion = mutableIntStateOf(0)
    var completedRequest: Pair<String, GlobalSearchFilter>? = null
    var completedGeneration = -1L

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
        pagingJob.value = null
        generation.value++
        chats.reset()
        contacts.reset()
        publicChats.reset()
        query.value = newQuery
        filter.value = GlobalSearchFilter()
        hits.value = emptyList()
        chatContexts.value = emptyMap()
        nextOffset.value = ""
        loadingMore.value = false
        moreFailed.value = false
        searching.value = false
        failed.value = false
        retryVersion.value = 0
        completedRequest = null
        completedGeneration = -1L
    }
}
