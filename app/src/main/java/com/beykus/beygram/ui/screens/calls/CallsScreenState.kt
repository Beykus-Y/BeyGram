package com.beykus.beygram.ui.screens.calls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.beykus.beygram.domain.model.CallHistoryPage
import com.beykus.beygram.domain.model.CallRecord

/** Сохраняет уже загруженные страницы истории звонков при переключении вкладок: при возвращении они показываются сразу. */
internal class CallsScreenState : ViewModel() {
    var calls by mutableStateOf<List<CallRecord>>(emptyList())
    var nextOffset by mutableStateOf("")

    /** Свежая первая страница заменяет прежнюю. Более старые страницы остаются, только если свежая пересекается с уже загруженными звонками: иначе между ними может быть пропуск. */
    fun replaceFirstPage(page: CallHistoryPage) {
        val fresh = page.calls.map { it.key() }.toSet()
        val overlaps = calls.any { it.key() in fresh }
        val older = calls.filterNot { it.key() in fresh }
        calls = if (overlaps) page.calls + older else page.calls
        if (!overlaps || older.isEmpty()) nextOffset = page.nextOffset
    }

    /** Следующая страница дописывается в конец; звонки, которые уже есть в списке, не повторяются. */
    fun appendPage(page: CallHistoryPage) {
        val known = calls.map { it.key() }.toSet()
        calls = calls + page.calls.filterNot { it.key() in known }
        nextOffset = page.nextOffset
    }

    private fun CallRecord.key() = "$chatId:$messageId"
}
