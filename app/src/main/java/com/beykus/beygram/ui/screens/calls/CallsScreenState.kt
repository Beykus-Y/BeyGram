package com.beykus.beygram.ui.screens.calls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.beykus.beygram.domain.model.CallRecord

/** Сохраняет уже загруженные страницы истории звонков при переключении вкладок: при возвращении они показываются сразу. */
internal class CallsScreenState : ViewModel() {
    var calls by mutableStateOf<List<CallRecord>>(emptyList())
    var nextOffset by mutableStateOf("")
}
