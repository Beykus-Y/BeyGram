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
    /** Set when the visit opens on a message from outside the chat (profile media). That message owns the opening position. */
    var focusClaimed = false
    /** Newest message seen by the list scroll, so a message that arrives later is told apart from the first load. */
    var seenNewestId = 0L
    /** Set when the chat hands over to chat info, so leaving the screen for it does not end the visit. */
    var visitContinues = false
    /** Message-based position, kept across visits within [CHAT_SCROLL_ANCHOR_TTL_MS]; index and offset above are the fallback. */
    var anchor: ChatScrollAnchor? = null
    /** Set once the anchor has been applied, or decided not to apply, for the current composition of the list. */
    var anchorRestored = false
    /** Time of the last send started from this device; zero when none is waiting for its message. */
    var localSendAtMs = 0L
    /** Key of the row that was at the bottom of the list before the last arrival, to tell whether the list was at the bottom. */
    var previousFirstKey: String? = null

    fun markLocalSend(nowMs: Long) {
        localSendAtMs = nowMs
    }

    fun localSendPending(nowMs: Long): Boolean =
        localSendAtMs != 0L && nowMs - localSendAtMs in 0..LOCAL_SEND_FOLLOW_WINDOW_MS

    fun consumeLocalSend() {
        localSendAtMs = 0L
    }

    fun endVisit() {
        readBoundaryId = null
        readBoundaryChecked = false
        readBoundaryWindowRequested = false
        focusClaimed = false
        seenNewestId = 0L
        anchorRestored = false
        localSendAtMs = 0L
        previousFirstKey = null
    }
}
