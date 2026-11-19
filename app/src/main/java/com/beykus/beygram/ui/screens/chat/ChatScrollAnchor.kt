package com.beykus.beygram.ui.screens.chat

/**
 * Where the chat list was, by message rather than by row index. Row indexes shift when messages arrive or history loads,
 * so the restored position follows the message that was on screen.
 */
internal data class ChatScrollAnchor(
    val key: String,
    val messageId: Long,
    val offset: Int,
    val atBottom: Boolean,
    val savedAtMs: Long
)

internal const val CHAT_SCROLL_ANCHOR_TTL_MS = 30L * 60 * 1000
/** A message sent from this device follows the bottom only within this window after the send started. */
internal const val LOCAL_SEND_FOLLOW_WINDOW_MS = 15_000L

internal fun ChatScrollAnchor.isFresh(nowMs: Long): Boolean = nowMs - savedAtMs in 0..CHAT_SCROLL_ANCHOR_TTL_MS

/**
 * Index in [items] where the anchor should be restored. The list is newest-first, so index 0 is the bottom.
 * Returns null when the anchor is at the bottom (the caller scrolls to 0), when the list is empty, or when the anchor
 * has no message id to fall back on.
 */
internal fun resolveAnchorIndex(items: List<ChatMessageListItem>, anchor: ChatScrollAnchor): Int? {
    if (items.isEmpty() || anchor.atBottom) return null
    val sameKey = items.indexOfFirst { it.key == anchor.key }
    if (sameKey >= 0) return sameKey
    if (anchor.messageId == 0L) return null
    val containing = items.indexOfFirst { item -> item.messages.any { it.id == anchor.messageId } }
    if (containing >= 0) return containing
    // The anchor message is gone: the nearest older row is the first one whose newest real message is older than it.
    val olderNeighbour = items.indexOfFirst { item ->
        val newestRealId = item.messages.filter { it.id > 0L }.maxOfOrNull { it.id }
        newestRealId != null && newestRealId < anchor.messageId
    }
    if (olderNeighbour >= 0) return olderNeighbour
    return items.lastIndex
}

/**
 * Anchor for the row at [firstIndex] (the bottom-most visible row in the reversed list), [offset] pixels from the edge.
 * Null when there is no such row.
 */
internal fun chatScrollAnchorOf(
    items: List<ChatMessageListItem>, firstIndex: Int, offset: Int, bottomThresholdPx: Int, nowMs: Long
): ChatScrollAnchor? {
    val item = items.getOrNull(firstIndex) ?: return null
    return ChatScrollAnchor(
        key = item.key,
        messageId = item.messages.maxOfOrNull { it.id } ?: 0L,
        offset = offset,
        atBottom = firstIndex == 0 && offset <= bottomThresholdPx,
        savedAtMs = nowMs
    )
}

/** Decide if a newly arrived newest message should move the list to the bottom. */
internal fun shouldFollowNewest(wasAtBottom: Boolean, userScrolling: Boolean, newestOutgoing: Boolean, localSendPending: Boolean, hasNewerMessages: Boolean): Boolean =
    !hasNewerMessages && ((newestOutgoing && localSendPending) || (wasAtBottom && !userScrolling))
