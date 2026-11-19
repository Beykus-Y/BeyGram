package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Позиция списка одной серверной папки: первая видимая строка, смещение внутри неё и ключ этой строки ([anchorKey]).
 * Индекс нужен только как запасной вариант: если строка-якорь пропала или ещё не загружена, список встаёт по индексу.
 */
internal data class ChatListScrollPosition(val index: Int, val offset: Int, val anchorKey: String? = null)

/** Ключ строки чата в списке. Id чата у TDLib 64-битный, поэтому префикс отделяет его от служебных строк. */
private fun chatRowKey(chatId: Long): String = "c:$chatId"

/** Ключ служебной строки списка: «story_bar», «archive_row» и так далее. */
private fun systemRowKey(key: String): String = "s:$key"

/**
 * Кодирует ключ строки LazyColumn в строку, которую можно сохранить. Чаты — «c:<id>», служебные строки — «s:<ключ>».
 * Прочие ключи (например, автоматические у строк без явного ключа) не якорь: возвращается null.
 */
internal fun encodeChatListKey(key: Any?): String? = when (key) {
    is Long -> chatRowKey(key)
    is String -> systemRowKey(key)
    else -> null
}

/**
 * Строит ключи строк в том же порядке и с теми же условиями, что и items в LazyColumn экрана чатов.
 * Если порядок здесь расходится с экраном, якорь будет искаться не там, где лежит строка.
 */
internal fun chatListItemKeys(
    showStoryBar: Boolean,
    showArchiveRow: Boolean,
    pinnedIds: List<Long>,
    showLocalSearchHeader: Boolean,
    regularIds: List<Long>,
    showFolderFooter: Boolean
): List<String> = buildList {
    if (showStoryBar) add(systemRowKey("story_bar"))
    if (showArchiveRow) add(systemRowKey("archive_row"))
    if (pinnedIds.isNotEmpty()) {
        add(systemRowKey("pinned_header"))
        pinnedIds.forEach { add(chatRowKey(it)) }
    }
    if (showLocalSearchHeader) add(systemRowKey("local_search_header"))
    if (regularIds.isEmpty() && pinnedIds.isEmpty()) {
        add(systemRowKey("empty_state"))
    } else {
        if (pinnedIds.isNotEmpty() && regularIds.isNotEmpty()) add(systemRowKey("pinned_separator"))
        regularIds.forEach { add(chatRowKey(it)) }
    }
    if (showFolderFooter) add(systemRowKey("server_folder_load_more"))
}

/**
 * Индекс, с которого нужно показать список: строка-якорь, если она есть в [keys], иначе запасной индекс из [position],
 * ограниченный диапазоном списка. Пустой список строк не имеет позиции: возвращается null.
 */
internal fun resolveChatListAnchorIndex(keys: List<String>, position: ChatListScrollPosition): Int? {
    if (keys.isEmpty()) return null
    val anchorIndex = position.anchorKey?.let { keys.indexOf(it) } ?: -1
    return if (anchorIndex >= 0) anchorIndex else position.index.coerceIn(0, keys.lastIndex)
}

/**
 * Состояние списка чатов живёт выше раскладки. Телефон, двухпанельный режим, поворот и складка
 * используют один и тот же поиск, выбранную серверную папку и позицию списка, поэтому ни переход
 * в чат, ни смена ширины окна не возвращают список в начало.
 *
 * Каждая серверная папка помнит свою позицию: возврат на прежнюю папку показывает те же строки,
 * а не открывает её с начала. Новая папка, которую ещё не листали, начинается с верха.
 * Поиск не записывает свою позицию и свою свёртку шапки ни в одну папку.
 */
@Stable
class ChatsScreenState private constructor(
    query: String,
    selectedFolderId: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleAnchorKey: String?,
    folderPositions: Map<Int, ChatListScrollPosition>
) {
    constructor(
        query: String = "",
        selectedFolderId: Int = 0,
        firstVisibleItemIndex: Int = 0,
        firstVisibleItemScrollOffset: Int = 0
    ) : this(
        query = query,
        selectedFolderId = selectedFolderId,
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        firstVisibleAnchorKey = null,
        folderPositions = emptyMap()
    )

    var query by mutableStateOf(query)
    var selectedFolderId by mutableIntStateOf(selectedFolderId)
        private set

    /**
     * Позиция текущей папки. При смене папки создаётся новый список: чужая прокрутка
     * не должна открывать новую папку с середины.
     */
    var listState: LazyListState by mutableStateOf(
        LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset)
    )
        private set

    /** Позиции папок, с которых пользователь уже уходил. */
    private val folderScrollPositions = folderPositions.toMutableMap().apply {
        if (query.isBlank()) {
            put(selectedFolderId, ChatListScrollPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset, firstVisibleAnchorKey))
        }
    }

    /** Прогресс свёртки шапки (0..1) для папок, с которых пользователь уже уходил. В памяти, без сохранения. */
    private val folderHeaderProgress = mutableMapOf<Int, Float>()

    /**
     * Позиция, которую надо вернуть по якорю в новом списке папки. Выдаётся один раз: экран берёт её
     * через [clearPendingAnchor], когда строки папки уже загружены.
     */
    var pendingAnchor: ChatListScrollPosition? = if (query.isBlank() && firstVisibleAnchorKey != null) {
        ChatListScrollPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset, firstVisibleAnchorKey)
    } else {
        null
    }
        private set

    private val scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Просьбы прокрутить текущий список к началу: повторное нажатие на вкладку «Чаты» и на её заголовок. */
    val scrollToTopRequests: SharedFlow<Unit> = scrollToTopEvents.asSharedFlow()

    /** Запрашивает плавную прокрутку текущего списка наверх. Позиции других папок не трогаются. */
    fun requestScrollToTop() {
        scrollToTopEvents.tryEmit(Unit)
    }

    /**
     * Выбирает серверную папку; 0 — основной список «Все чаты». [headerProgress] — свёртка шапки, которую
     * нужно запомнить для уходящей папки.
     */
    fun selectFolder(folderId: Int, headerProgress: Float = 0f) {
        if (selectedFolderId == folderId) return
        // Поиск не должен записывать свою позицию и свою шапку в папку.
        if (query.isBlank()) {
            folderScrollPositions[selectedFolderId] = currentScrollPosition()
            folderHeaderProgress[selectedFolderId] = headerProgress.coerceIn(0f, 1f)
        }
        selectedFolderId = folderId
        val saved = folderScrollPositions[folderId]
        listState = LazyListState(saved?.index ?: 0, saved?.offset ?: 0)
        pendingAnchor = saved?.takeIf { it.anchorKey != null && query.isBlank() }
    }

    /** Прогресс свёртки шапки, запомненный для папки; null, если из папки ещё не уходили. */
    fun savedHeaderProgress(folderId: Int): Float? = folderHeaderProgress[folderId]

    /** Снимает одноразовый якорь после того, как список встал на место (или когда якорь больше не нужен). */
    fun clearPendingAnchor() {
        pendingAnchor = null
    }

    /**
     * Удаляет позиции и шапку папок, которых больше нет на сервере. [existingIds] должен включать 0 («Все чаты»),
     * иначе основной список тоже потеряет позицию.
     */
    fun pruneFolders(existingIds: Set<Int>) {
        folderScrollPositions.keys.retainAll(existingIds)
        folderHeaderProgress.keys.retainAll(existingIds)
    }

    /**
     * Части для [ChatsScreenStateSaver]: поиск, папка, текущая позиция, позиции остальных папок и их якоря.
     * Якоря идут в том же порядке, что и позиции папок (по возрастанию id); пустая строка значит «якоря нет».
     */
    internal fun saveableParts(): List<Any> {
        // Поиск не пишет позицию ни в одну папку: текущая папка добавляется, только когда поиска нет.
        val positions = if (query.isBlank()) {
            folderScrollPositions + (selectedFolderId to currentScrollPosition())
        } else {
            folderScrollPositions
        }
        val sorted = positions.entries.sortedBy { it.key }
        // Плоский массив троек не зависит от реализации Map и одинаково читается после восстановления процесса.
        val encodedPositions = sorted
            .flatMap { (folderId, position) -> listOf(folderId, position.index, position.offset) }
            .toIntArray()
        val anchors = ArrayList(sorted.map { it.value.anchorKey.orEmpty() })
        return listOf(
            query,
            selectedFolderId,
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            encodedPositions,
            anchors
        )
    }

    private fun currentScrollPosition(): ChatListScrollPosition {
        val visible = listState.layoutInfo.visibleItemsInfo
        // Пока строки не измерены, известный якорь остаётся прежним: иначе его потеряет первое сохранение.
        val anchor = if (visible.isEmpty()) {
            pendingAnchor?.anchorKey ?: folderScrollPositions[selectedFolderId]?.anchorKey
        } else {
            encodeChatListKey(visible.first().key)
        }
        return ChatListScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, anchor)
    }

    internal companion object {
        /** Восстанавливает состояние, в том числе запомненные позиции остальных папок. */
        fun restore(
            query: String,
            selectedFolderId: Int,
            firstVisibleItemIndex: Int,
            firstVisibleItemScrollOffset: Int,
            folderPositions: Map<Int, ChatListScrollPosition>,
            firstVisibleAnchorKey: String? = null
        ): ChatsScreenState = ChatsScreenState(
            query = query,
            selectedFolderId = selectedFolderId,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            firstVisibleAnchorKey = firstVisibleAnchorKey,
            folderPositions = folderPositions
        )
    }
}

/**
 * Раскладывает плоский массив троек «папка, строка, смещение» обратно в карту позиций.
 * [anchors] — якоря в том же порядке; их может не быть в состоянии, сохранённом до появления якорей.
 */
internal fun decodeChatListScrollPositions(encoded: IntArray?, anchors: List<*>? = null): Map<Int, ChatListScrollPosition> {
    if (encoded == null || encoded.size % 3 != 0) return emptyMap()
    return encoded.toList().chunked(3).mapIndexed { i, (folderId, index, offset) ->
        // Битое сохранённое состояние не должно ронять восстановление списка.
        val anchor = (anchors?.getOrNull(i) as? String)?.takeIf { it.isNotEmpty() }
        folderId to ChatListScrollPosition(index.coerceAtLeast(0), offset.coerceAtLeast(0), anchor)
    }.toMap()
}

internal val ChatsScreenStateSaver: Saver<ChatsScreenState, Any> = listSaver(
    save = { it.saveableParts() },
    restore = { parts ->
        val selectedFolderId = parts[1] as Int
        val positions = decodeChatListScrollPositions(
            encoded = parts.getOrNull(4) as? IntArray,
            // Состояние, сохранённое до появления якорей, содержит только пять частей и восстанавливается по индексам.
            anchors = parts.getOrNull(5) as? List<*>
        )
        ChatsScreenState.restore(
            query = parts[0] as String,
            selectedFolderId = selectedFolderId,
            firstVisibleItemIndex = parts[2] as Int,
            firstVisibleItemScrollOffset = parts[3] as Int,
            folderPositions = positions,
            firstVisibleAnchorKey = positions[selectedFolderId]?.anchorKey
        )
    }
)

@Composable
fun rememberChatsScreenState(): ChatsScreenState =
    rememberSaveable(saver = ChatsScreenStateSaver) { ChatsScreenState() }
