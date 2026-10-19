package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Keeps the message list position across chat info and other short detours. The ViewModel lives as long as the activity,
 * so the unread state of one visit is cleared by [endVisit] when the user leaves the chat.
 */
internal class ChatScrollPositionViewModel : ViewModel() {
    var firstVisibleItemIndex = 0
    var firstVisibleItemScrollOffset = 0
    /** Read boundary of the current visit; the separator is drawn after it. */
    var readBoundaryId by mutableStateOf<Long?>(null)
    /** Set once the opening position of the visit is decided. Until then read marks are held back, so the boundary cannot move. */
    var readBoundaryChecked by mutableStateOf(false)
    var readBoundaryWindowRequested = false
    /** Newest message seen by the list scroll, so a message that arrives later is told apart from the first load. */
    var seenNewestId = 0L
    /** Set when the chat hands over to chat info, so leaving the screen for it does not end the visit. */
    var visitContinues = false

    fun endVisit() {
        readBoundaryId = null
        readBoundaryChecked = false
        readBoundaryWindowRequested = false
        seenNewestId = 0L
    }
}
