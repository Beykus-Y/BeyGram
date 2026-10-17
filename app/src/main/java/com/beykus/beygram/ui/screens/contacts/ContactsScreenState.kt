package com.beykus.beygram.ui.screens.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.beykus.beygram.domain.model.ContactPreview

/** Сохраняет загруженные контакты при переключении вкладок: при возвращении список показывается сразу и обновляется в фоне. */
internal class ContactsScreenState : ViewModel() {
    var contacts by mutableStateOf<List<ContactPreview>>(emptyList())
}
