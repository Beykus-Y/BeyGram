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
 * новее — от самого нового загруженного id. Конец истории: курсор 0 или курсор, который не сдвинулся. Страница без
 * вложений после фильтрации конца не означает: пока окно пустое, дочитываем старее, курсор конечен.
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
    /** Запрос в ту же сторону уже стоит в очереди или выполняется: повтор дал бы лишнюю страницу, поэтому его не ставим. */
    private var olderQueued = false
    private var newerQueued = false
    /** Во время запроса в эту сторону пришёл ещё один вызов: выполнить после текущего. */
    private var olderAgain = false
    private var newerAgain = false

    /**
     * Сбрасывает окно. Без [anchor] страницы идут от самого нового вложения, и новее ничего нет.
     * С [anchor] первая страница начинается с самого вложения, а новее догружается от него.
     */
    fun restart(anchor: Long?) {
        generation++
        loading = false
        messages = emptyList()
        loaded = false
        hasOlder = true
        hasNewer = anchor != null
        error = null
        olderCursor = anchor ?: 0L
        newestId = anchor ?: 0L
        olderQueued = false
        newerQueued = false
        olderAgain = false
        newerAgain = false
    }

    /** Догружает вложения старше уже загруженных. Если старше больше нет, ничего не делает. */
    suspend fun loadOlder() = loadPage(newer = false)

    /** Догружает вложения новее уже загруженных. Если новее больше нет, ничего не делает. */
    suspend fun loadNewer() = loadPage(newer = true)

    /**
     * Догружает ту сторону окна, к которой подошло вложение [messageId], например при свайпе в просмотрщике.
     * Край считаем среди [visible] вложений: просмотрщик не листает секретные, и они не должны сдвигать край.
     */
    suspend fun loadNear(messageId: Long, visible: (ChatMessage) -> Boolean = { true }) {
        val shown = messages.filter(visible)
        val index = shown.indexOfFirst { it.id == messageId }
        if (index < 0) return
        if (index < PREFETCH) loadOlder()
        // Старые страницы дописываются слева, поэтому расстояние до правого края берём до загрузки.
        if (shown.size - index <= PREFETCH) loadNewer()
    }

    private suspend fun loadPage(newer: Boolean) {
        // Вкладка вызывает догрузку из нескольких мест (первая страница, прокрутка, просмотрщик). Пока запрос в эту сторону
        // идёт, повторный не запускает второй параллельный запрос с тем же курсором: он запоминается, и цикл выполнит
        // его после текущего, уже с новым курсором. Так быстрый свайп не теряет догрузку.
        if (if (newer) newerQueued else olderQueued) {
            if (newer) newerAgain = true else olderAgain = true
            return
        }
        if (newer) newerQueued = true else olderQueued = true
        val token = generation
        try {
            do {
                if (newer) newerAgain = false else olderAgain = false
                lock.withLock { runLoop(newer, token) }
            } while (token == generation && (if (newer) newerAgain else olderAgain))
        } finally {
            // Старая корутина после restart не трогает флаги новой генерации.
            if (token == generation) { if (newer) newerQueued = false else olderQueued = false }
        }
    }

    private suspend fun runLoop(newer: Boolean, token: Int) {
        while (token == generation) {
            val exhausted = if (newer) !hasNewer else !hasOlder
            if (exhausted) return
            fetch(newer)
            // Старее дочитываем, пока окно пустое: страница без вложений после фильтрации не конец истории.
            // Новее идём одной страницей. Ошибка или уже есть что показать — останавливаемся.
            if (newer || error != null || messages.isNotEmpty()) return
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
            // An obsolete request must not change the loading state of a restarted window.
            if (current == generation) loading = false
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
                val previousCursor = olderCursor
                merge(page.messages)
                olderCursor = page.nextFromMessageId
                // Курсор, который не сдвинулся, или 0 означают конец: иначе запрос повторялся бы на том же месте.
                hasOlder = page.nextFromMessageId != 0L && page.nextFromMessageId != previousCursor
                loaded = true
            }
            newestId = maxOf(newestId, page.messages.maxOfOrNull { it.id } ?: 0L)
        }.onFailure { error = failure(it) }
    }

    private fun merge(page: List<ChatMessage>) {
        messages = (messages + page).distinctBy { it.id }.sortedBy { it.id }
    }
}
