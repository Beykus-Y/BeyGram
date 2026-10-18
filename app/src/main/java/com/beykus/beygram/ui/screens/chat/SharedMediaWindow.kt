package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.*
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSearchPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Сколько вложений должно остаться с одной стороны окна, когда догружаем страницу с этой стороны. */
private const val PREFETCH = 6

/**
 * Вложения одного вида, которые грузятся страницами в обе стороны. Этим окном пользуются и сетка в профиле чата,
 * и просмотрщик фото и видео в чате, поэтому пагинация у них одна.
 *
 * Держим по возрастанию id, то есть по времени, и каждый id один раз. Страница старше идёт курсором TDLib, страница
 * новее — от самого нового загруженного id. Пустая страница, курсор 0 или страница без новых id означают конец.
 * Загрузки идут по одной, а ответ, пришедший после [restart], отбрасывается.
 */
@Stable
internal class SharedMediaWindow(
    /** Страница вложений старше [fromMessageId], а при [newer] — новее. См. ConversationRepository.sharedMessages. */
    private val load: suspend (fromMessageId: Long, newer: Boolean) -> Result<MessageSearchPage>,
    private val failure: (Throwable) -> String
) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    /** Первая страница пришла: пустой список тогда значит «ничего нет», а не «ещё грузим». */
    var loaded by mutableStateOf(false)
        private set
    var hasOlder by mutableStateOf(true)
        private set
    var hasNewer by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val lock = Mutex()
    private var generation = 0
    /** Курсор TDLib для страниц старше; без якоря 0 — от самого нового вложения. */
    private var olderCursor = 0L
    /** Самый новый id в окне: страницы новее идут от него. */
    private var newestId = 0L

    /**
     * Сбрасывает окно. Без [anchor] страницы идут от самого нового вложения, и новее ничего нет.
     * С [anchor] первая страница начинается с самого вложения, а новее догружается от него.
     */
    fun restart(anchor: Long?) {
        generation++
        messages = emptyList()
        loaded = false
        hasOlder = true
        hasNewer = anchor != null
        error = null
        olderCursor = anchor ?: 0L
        newestId = anchor ?: 0L
    }

    /** Догружает вложения старше уже загруженных. Если старше больше нет, ничего не делает. */
    suspend fun loadOlder() = loadPage(newer = false)

    /** Догружает вложения новее уже загруженных. Если новее больше нет, ничего не делает. */
    suspend fun loadNewer() = loadPage(newer = true)

    /** Догружает ту сторону окна, к которой подошло вложение [messageId], например при свайпе в просмотрщике. */
    suspend fun loadNear(messageId: Long) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        if (index < PREFETCH) loadOlder()
        if (index >= messages.size - PREFETCH) loadNewer()
    }

    private suspend fun loadPage(newer: Boolean) {
        lock.withLock {
            val exhausted = if (newer) !hasNewer else !hasOlder
            if (exhausted) return
            fetch(newer)
        }
    }

    private suspend fun fetch(newer: Boolean) {
        val current = generation
        loading = true
        error = null
        try {
            val result = if (newer) load(newestId, true) else load(olderCursor, false)
            if (current == generation) applyPage(result, newer)
        } finally {
            loading = false
        }
    }

    private fun applyPage(result: Result<MessageSearchPage>, newer: Boolean) {
        result.onSuccess { page ->
            if (newer) {
                // Страница новых может зацепить уже загруженное, поэтому берём только то, что новее самого нового.
                val fresh = page.messages.filter { it.id > newestId }
                merge(fresh)
                hasNewer = fresh.isNotEmpty()
            } else {
                merge(page.messages)
                olderCursor = page.nextFromMessageId
                hasOlder = page.nextFromMessageId != 0L && page.messages.isNotEmpty()
                loaded = true
            }
            newestId = maxOf(newestId, page.messages.maxOfOrNull { it.id } ?: 0L)
        }.onFailure { error = failure(it) }
    }

    private fun merge(page: List<ChatMessage>) {
        messages = (messages + page).distinctBy { it.id }.sortedBy { it.id }
    }
}
