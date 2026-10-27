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

/** Позиция списка одной серверной папки: первая видимая строка и смещение внутри неё. */
internal data class ChatListScrollPosition(val index: Int, val offset: Int)

/**
 * Состояние списка чатов живёт выше раскладки. Телефон, двухпанельный режим, поворот и складка
 * используют один и тот же поиск, выбранную серверную папку и позицию списка, поэтому ни переход
 * в чат, ни смена ширины окна не возвращают список в начало.
 *
 * Каждая серверная папка помнит свою позицию: возврат на прежнюю папку показывает те же строки,
 * а не открывает её с начала. Новая папка, которую ещё не листали, начинается с верха.
 */
@Stable
class ChatsScreenState private constructor(
    query: String,
    selectedFolderId: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
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
        put(selectedFolderId, ChatListScrollPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset))
    }

    /** Выбирает серверную папку; 0 — основной список «Все чаты». */
    fun selectFolder(folderId: Int) {
        if (selectedFolderId == folderId) return
        folderScrollPositions[selectedFolderId] = currentScrollPosition()
        selectedFolderId = folderId
        val saved = folderScrollPositions[folderId]
        listState = LazyListState(saved?.index ?: 0, saved?.offset ?: 0)
    }

    /** Части для [ChatsScreenStateSaver]: поиск, папка, текущая позиция и позиции остальных папок. */
    internal fun saveableParts(): List<Any> {
        // Текущая папка ещё не покинута, поэтому её позицию добавляем к уже запомненным.
        val positions = folderScrollPositions + (selectedFolderId to currentScrollPosition())
        // Плоский массив троек не зависит от реализации Map и одинаково читается после
        // восстановления процесса; порядок папок фиксирован для предсказуемого теста.
        val encodedPositions = positions.entries
            .sortedBy { it.key }
            .flatMap { (folderId, position) -> listOf(folderId, position.index, position.offset) }
            .toIntArray()
        return listOf(
            query,
            selectedFolderId,
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            encodedPositions
        )
    }

    private fun currentScrollPosition() = ChatListScrollPosition(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    )

    internal companion object {
        /** Восстанавливает состояние, в том числе запомненные позиции остальных папок. */
        fun restore(
            query: String,
            selectedFolderId: Int,
            firstVisibleItemIndex: Int,
            firstVisibleItemScrollOffset: Int,
            folderPositions: Map<Int, ChatListScrollPosition>
        ): ChatsScreenState = ChatsScreenState(
            query = query,
            selectedFolderId = selectedFolderId,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            folderPositions = folderPositions
        )
    }
}

/** Раскладывает плоский массив троек «папка, строка, смещение» обратно в карту позиций. */
internal fun decodeChatListScrollPositions(encoded: IntArray?): Map<Int, ChatListScrollPosition> {
    if (encoded == null || encoded.size % 3 != 0) return emptyMap()
    return encoded.toList().chunked(3).associate { (folderId, index, offset) ->
        // Битое сохранённое состояние не должно ронять восстановление списка.
        folderId to ChatListScrollPosition(index.coerceAtLeast(0), offset.coerceAtLeast(0))
    }
}

internal val ChatsScreenStateSaver: Saver<ChatsScreenState, Any> = listSaver(
    save = { it.saveableParts() },
    restore = { parts ->
        ChatsScreenState.restore(
            query = parts[0] as String,
            selectedFolderId = parts[1] as Int,
            firstVisibleItemIndex = parts[2] as Int,
            firstVisibleItemScrollOffset = parts[3] as Int,
            // Состояние, сохранённое до появления позиций по папкам, содержит только четыре части.
            folderPositions = decodeChatListScrollPositions(parts.getOrNull(4) as? IntArray)
        )
    }
)

@Composable
fun rememberChatsScreenState(): ChatsScreenState =
    rememberSaveable(saver = ChatsScreenStateSaver) { ChatsScreenState() }
