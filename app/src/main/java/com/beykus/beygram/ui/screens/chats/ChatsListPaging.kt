package com.beykus.beygram.ui.screens.chats

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.beykus.beygram.domain.model.TelegramFolderLoadState

/** Сколько последних строк списка папки видно, когда следующая страница уже нужна. */
internal const val FolderPagingPrefetchRows = 6

/**
 * Нужна ли следующая страница серверной папки прямо сейчас.
 * Страница запрашивается, только когда папка уже загружена хотя бы раз, есть ещё строки, ничего не грузится,
 * нет ошибки и пользователь подошёл к концу списка. Одна и та же пара (папка, число строк) не запрашивается дважды:
 * если страница пришла, а строк не прибавилось, решает ручная кнопка «Загрузить ещё».
 */
internal fun shouldAutoLoadFolderPage(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    state: TelegramFolderLoadState,
    rowCount: Int,
    lastRequestedRowCount: Int?
): Boolean = state.loadedOnce &&
    state.hasMore &&
    !state.loading &&
    !state.error &&
    lastVisibleIndex >= totalItemsCount - FolderPagingPrefetchRows &&
    lastRequestedRowCount != rowCount

/** Что показывает пустой список серверной папки, пока в ней нет ни одного чата. */
internal enum class FolderEmptyContent { Loading, Error, Empty }

/**
 * Пустая папка не означает «чатов нет»: пока TDLib не ответил, или пока есть ещё страницы, показываем загрузку.
 * «Пусто» — только когда папка полностью загружена и страниц больше нет.
 */
internal fun folderEmptyContent(load: TelegramFolderLoadState): FolderEmptyContent = when {
    load.loading -> FolderEmptyContent.Loading
    load.error -> FolderEmptyContent.Error
    !load.loadedOnce || load.hasMore -> FolderEmptyContent.Loading
    else -> FolderEmptyContent.Empty
}

/** Последний автоматический запрос страницы: папка и число строк, с которым он был сделан. */
@Stable
internal class FolderPagingMemory {
    var requested by mutableStateOf<Pair<Int, Int>?>(null)
}

/**
 * Подгружает следующую страницу серверной папки, когда пользователь подходит к концу списка (#1026).
 * Возвращает true, когда автоподгрузка остановилась без прироста строк: тогда экран показывает ручную кнопку
 * «Загрузить ещё чаты». Ошибку и её повтор экран показывает сам по [loadState].
 */
@Composable
internal fun ServerFolderAutoPaging(
    listState: LazyListState,
    folderId: Int?,
    rowCount: Int,
    loadState: TelegramFolderLoadState,
    onLoadMore: (Int) -> Unit
): Boolean {
    val memory = remember { FolderPagingMemory() }
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    // Перезапуск при любом изменении входов: решение принимается по актуальному состоянию папки.
    LaunchedEffect(listState, folderId, rowCount, loadState) {
        val id = folderId ?: return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            // Запрос относится только к этой папке: ответ после переключения папки не запускает подгрузку новой.
            val requestedRows = memory.requested?.takeIf { it.first == id }?.second
            if (shouldAutoLoadFolderPage(lastVisible, total, loadState, rowCount, requestedRows)) {
                memory.requested = id to rowCount
                latestOnLoadMore(id)
            }
        }
    }
    val requested = memory.requested
    return folderId != null &&
        requested != null &&
        requested.first == folderId &&
        requested.second == rowCount &&
        loadState.hasMore &&
        !loadState.loading &&
        !loadState.error
}
