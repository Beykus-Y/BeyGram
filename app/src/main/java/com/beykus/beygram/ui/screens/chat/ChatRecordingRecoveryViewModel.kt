package com.beykus.beygram.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds a finalized take while a chat composition is recreated (for example on rotation). */
internal class ChatRecordingRecoveryViewModel : ViewModel() {
    private val _preview = MutableStateFlow<List<OutgoingMedia>?>(null)
    val preview = _preview.asStateFlow()
    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex = _selectedIndex.asStateFlow()

    val processingScope: CoroutineScope get() = viewModelScope

    fun showPreview(media: List<OutgoingMedia>, index: Int = 0) {
        _preview.value = media
        _selectedIndex.value = index.coerceIn(0, media.lastIndex.coerceAtLeast(0))
    }

    fun select(index: Int) {
        _selectedIndex.value = index.coerceIn(0, (_preview.value?.lastIndex ?: 0).coerceAtLeast(0))
    }

    fun dismissPreview() {
        _preview.value = null
        _selectedIndex.value = 0
    }
}
