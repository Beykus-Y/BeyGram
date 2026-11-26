package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.ChatPreview
import java.time.LocalDate

internal class ChatTimelineContent(
    val items: List<ChatMessageListItem>,
    val itemsWithoutUnreadSeparator: List<ChatMessageListItem>,
    val daySeparators: Map<String, LocalDate>,
    val feedTopicHeaders: Map<String, Long>,
    val senderHeaderByKey: Map<String, Boolean>,
    val localTextAttemptIds: Map<Long, TextSendAttempt>
)

/** Builds the reversed list and its row metadata for one account, chat and topic. */
@Composable
internal fun rememberChatTimelineContent(
    accountId: String,
    chat: ChatPreview,
    forumTopicId: Long,
    history: ChatHistory,
    preparedTimeline: PreparedChatTimeline?,
    waveFiltered: Boolean,
    pendingVideoNotes: List<PendingVideoNote>,
    queuedTextAttempts: List<TextSendAttempt>,
    readBoundaryId: Long?,
    forumAllMessages: Boolean,
    generalTopicId: Long
): ChatTimelineContent {
    // Rows keep their instances across content-only updates, so untouched bubbles skip recomposition; see ChatTimelineGrouper.
    val timelineGrouper = remember(accountId, chat.id, forumTopicId) { ChatTimelineGrouper() }
    // BeyGramApp groups the history off the main thread; this screen groups it only for the first frame or a mismatch.
    val messages = history.messages
    val timelineRows = remember(messages, waveFiltered, timelineGrouper, preparedTimeline) {
        preparedTimeline?.takeIf { it.history.messages === messages && it.waveFiltered == waveFiltered }?.rows
            ?: timelineGrouper.group(if (waveFiltered) messages.filterNot(::isBgWaveSignalMessage) else messages)
    }
    val albumItems = timelineRows.items
    val visibleTextAttempts = remember(queuedTextAttempts, history) {
        visiblePendingTextAttempts(queuedTextAttempts, history)
    }
    val localTextAttemptIds = remember(visibleTextAttempts) {
        visibleTextAttempts.associateBy { -it.attemptId }
    }
    // Pending recording and text rows occupy the newest slots in the reversed list.
    val rawMessageItems = remember(albumItems, pendingVideoNotes, visibleTextAttempts) {
        pendingVideoNotes.map { ChatMessageListItem("pending-video:${it.sessionName}", emptyList(), pendingVideoNote = it) } +
            visibleTextAttempts.map { ChatMessageListItem("pending-text:${it.attemptId}", listOf(it.toChatMessage())) } +
            albumItems
    }
    val messageListItems = remember(rawMessageItems, readBoundaryId) {
        readBoundaryId?.let { withUnreadSeparator(rawMessageItems, it) } ?: rawMessageItems
    }
    // Keyed by the row structure, not the list: a content-only update leaves days and sender headers unchanged.
    val daySeparators = remember(timelineRows.structureVersion, timelineGrouper, pendingVideoNotes, visibleTextAttempts, readBoundaryId, history.hasOlderMessages) {
        chatDaySeparators(messageListItems, showOldestDay = !history.hasOlderMessages)
    }
    // In a private chat the peer is already named in the header; groups need the author once per block, not per bubble.
    // The all-messages feed names the topic above the first message of each run that belongs to one topic.
    val feedTopicOf: (ChatMessage) -> Long = { message -> forumFeedTopicId(message, generalTopicId) }
    val feedTopicHeaders = remember(timelineRows.structureVersion, timelineGrouper, pendingVideoNotes, visibleTextAttempts, readBoundaryId, forumAllMessages, generalTopicId) {
        if (forumAllMessages) forumFeedTopicHeaders(messageListItems, feedTopicOf) else emptyMap<String, Long>()
    }
    val senderHeaderByKey = remember(timelineRows.structureVersion, timelineGrouper, pendingVideoNotes, visibleTextAttempts, readBoundaryId, chat.isGroup, chat.isChannel, chat.isSavedMessages, forumAllMessages, generalTopicId) {
        senderHeaderVisibility(chat, messageListItems, if (forumAllMessages) feedTopicOf else null)
    }

    return remember(messageListItems, rawMessageItems, daySeparators, feedTopicHeaders, senderHeaderByKey, localTextAttemptIds) {
        ChatTimelineContent(messageListItems, rawMessageItems, daySeparators, feedTopicHeaders, senderHeaderByKey, localTextAttemptIds)
    }
}
