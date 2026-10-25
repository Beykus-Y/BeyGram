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

/**
 * Состояние списка чатов живёт выше раскладки. Телефон, двухпанельный режим, поворот и складка
 * используют один и тот же поиск, выбранную серверную папку и позицию списка, поэтому ни переход
 * в чат, ни смена ширины окна не возвращают список в начало.
 */
@Stable
class ChatsScreenState(
    query: String = "",
    selectedFolderId: Int = 0,
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0
) {
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

    /** Выбирает серверную папку; 0 — основной список «Все чаты». */
    fun selectFolder(folderId: Int) {
        if (selectedFolderId == folderId) return
        selectedFolderId = folderId
        listState = LazyListState()
    }

    /** Части для [ChatsScreenStateSaver]: поиск, папка и позиция списка. */
    internal fun saveableParts(): List<Any> = listOf(
        query,
        selectedFolderId,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    )
}

internal val ChatsScreenStateSaver: Saver<ChatsScreenState, Any> = listSaver(
    save = { it.saveableParts() },
    restore = { parts ->
        ChatsScreenState(
            query = parts[0] as String,
            selectedFolderId = parts[1] as Int,
            firstVisibleItemIndex = parts[2] as Int,
            firstVisibleItemScrollOffset = parts[3] as Int
        )
    }
)

@Composable
fun rememberChatsScreenState(): ChatsScreenState =
    rememberSaveable(saver = ChatsScreenStateSaver) { ChatsScreenState() }
