package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Keeps the message list position while a chat leaves and re-enters composition, for example when opening chat info. */
internal class ChatScrollPositionViewModel : ViewModel() {
    var firstVisibleItemIndex = 0
    var firstVisibleItemScrollOffset = 0
    /** Read boundary taken when the chat opened with unread messages; the separator is drawn after it. */
    var readBoundaryId by mutableStateOf<Long?>(null)
    /** The opening position is decided once per visit, so returning from chat info does not move the list again. */
    var readBoundaryChecked = false
    var readBoundaryWindowRequested = false
}
