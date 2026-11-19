package com.beykus.beygram.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beykus.beygram.domain.model.OutgoingMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a voice take is sent. Captured when the take is finished, so a later composer state (another topic or reply)
 * cannot redirect a queued or recovered recording.
 */
internal data class VoiceRecordingDestination(val topicId: Long, val replyId: Long?)

/** A finalized voice take waiting for the user, kept together with the destination it was recorded for. */
internal data class VoiceRecordingPreview(val media: List<OutgoingMedia>, val destination: VoiceRecordingDestination)

/** Holds a finalized take while a chat composition is recreated (for example on rotation). */
internal class ChatRecordingRecoveryViewModel : ViewModel() {
    private val _preview = MutableStateFlow<VoiceRecordingPreview?>(null)
    val preview = _preview.asStateFlow()
    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex = _selectedIndex.asStateFlow()

    val processingScope: CoroutineScope get() = viewModelScope

    fun showPreview(media: List<OutgoingMedia>, destination: VoiceRecordingDestination, index: Int = 0) {
        _preview.value = VoiceRecordingPreview(media, destination)
        _selectedIndex.value = index.coerceIn(0, media.lastIndex.coerceAtLeast(0))
    }

    fun select(index: Int) {
        _selectedIndex.value = index.coerceIn(0, (_preview.value?.media?.lastIndex ?: 0).coerceAtLeast(0))
    }

    fun dismissPreview() {
        _preview.value = null
        _selectedIndex.value = 0
    }
}
