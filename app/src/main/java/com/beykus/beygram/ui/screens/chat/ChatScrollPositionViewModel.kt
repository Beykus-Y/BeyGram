package com.beykus.beygram.ui.screens.chat

import androidx.lifecycle.ViewModel

/** Keeps the message list position while a chat leaves and re-enters composition, for example when opening chat info. */
internal class ChatScrollPositionViewModel : ViewModel() {
    var firstVisibleItemIndex = 0
    var firstVisibleItemScrollOffset = 0
}
