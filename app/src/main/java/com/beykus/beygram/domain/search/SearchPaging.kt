package com.beykus.beygram.domain.search

import com.beykus.beygram.domain.model.ChatSearchFilter

/**
 * Дописывает следующую страницу выдачи к уже показанной. Повтор ключа из предыдущей страницы
 * пропускается, порядок первой страницы сохраняется. Нужен, потому что соседние страницы TDLib
 * могут перекрываться.
 */
fun <T, K> appendDistinctBy(existing: List<T>, incoming: List<T>, key: (T) -> K): List<T> {
    val seen = existing.mapTo(HashSet()) { key(it) }
    val result = existing.toMutableList()
    for (item in incoming) {
        if (seen.add(key(item))) result.add(item)
    }
    return result
}

/**
 * Выдача устаревает, когда пользователь поменял запрос. Результат старого запроса нельзя
 * показывать поверх нового, даже если он пришёл позже.
 */
fun isCurrentSearch(requestedQuery: String, currentQuery: String): Boolean =
    requestedQuery.trim() == currentQuery.trim()

/** Same guard for in-chat search, where changing a filter is as invalidating as changing the text. */
fun isCurrentChatSearch(
    requestedQuery: String,
    requestedFilter: ChatSearchFilter,
    currentQuery: String,
    currentFilter: ChatSearchFilter
): Boolean = requestedFilter == currentFilter && isCurrentSearch(requestedQuery, currentQuery)

/** Сколько последних результатов должно оказаться на экране, чтобы следующая страница догрузилась сама. */
const val CHAT_SEARCH_AUTO_LOAD_THRESHOLD = 5

/**
 * Автодогрузка выдачи: только когда список дошёл до конца, есть курсор, не идёт запрос,
 * нет ошибки и показанные результаты не устарели. Ошибка или устаревание не запускают цикл:
 * тогда остаётся ручная кнопка «Показать ещё».
 */
fun shouldAutoLoadChatSearchPage(
    lastVisibleIndex: Int,
    resultCount: Int,
    hasMore: Boolean,
    busy: Boolean,
    failed: Boolean,
    stale: Boolean
): Boolean =
    resultCount > 0 && hasMore && !busy && !failed && !stale &&
        lastVisibleIndex >= resultCount - CHAT_SEARCH_AUTO_LOAD_THRESHOLD

/** Что показывать в списке результатов внутри чата. Отделяет первую загрузку, ошибку и пустой ответ. */
enum class ChatSearchListState { Idle, Loading, Results, FirstPageError, NoMatchesOnPage, NoFilterMatches, NoMatches }

/**
 * Состояние списка: без критериев — подсказка; без результатов во время запроса — загрузка;
 * есть результаты — список (ошибка догрузки их не скрывает); пустая выдача делится на ошибку
 * первой страницы, «ничего не найдено» и варианты с курсором или фильтром.
 */
fun chatSearchListState(
    criteriaActive: Boolean,
    busy: Boolean,
    resultCount: Int,
    failed: Boolean,
    hasMore: Boolean,
    filterActive: Boolean
): ChatSearchListState = when {
    !criteriaActive -> ChatSearchListState.Idle
    busy && resultCount == 0 -> ChatSearchListState.Loading
    resultCount > 0 -> ChatSearchListState.Results
    failed -> ChatSearchListState.FirstPageError
    hasMore -> ChatSearchListState.NoMatchesOnPage
    filterActive -> ChatSearchListState.NoFilterMatches
    else -> ChatSearchListState.NoMatches
}

/** Курсор, который не сдвинулся, означает, что TDLib отдал ту же страницу: дальше грузить нельзя. */
fun nextChatSearchCursor(requestedCursor: Long, returnedCursor: Long): Long =
    if (returnedCursor == requestedCursor) 0L else returnedCursor
