package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.motion.scrollToItemRespectingMotion
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ChatOlderHistoryEffect(
    conversation: ConversationRepository,
    chatId: Long,
    forumTopicId: Long,
    listState: LazyListState,
    scrollPosition: ChatScrollPositionViewModel,
    history: ChatHistory,
    rowCount: Int
) {
    // LaunchedEffect lives across recompositions; rememberUpdatedState keeps the collector on fresh history/row counts.
    val autoLoadHistory by rememberUpdatedState(history)
    val autoLoadRowCount by rememberUpdatedState(rowCount)
    // Reverse LazyColumn: larger visible indexes are OLDER messages. Prefetch when the user approaches
    // the loaded edge, but wait for the unread/focused opening to settle, and stop on errors until retry.
    // beginLoad() in the repository also prevents duplicate in-flight requests.
    LaunchedEffect(chatId, forumTopicId, conversation, listState, scrollPosition) {
        snapshotFlow {
            shouldAutoLoadOlderMessages(
                lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1,
                loadedItemCount = autoLoadRowCount,
                hasOlderMessages = autoLoadHistory.hasOlderMessages,
                isLoading = autoLoadHistory.isLoading,
                hasError = autoLoadHistory.error != null,
                openingSettled = scrollPosition.readBoundaryChecked
            )
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad) {
                if (forumTopicId > 0L) conversation.loadOlderForumTopicMessages(chatId, forumTopicId)
                else conversation.loadOlderMessages(chatId)
            }
        }
    }
}

@Composable
internal fun ChatTimelineOpeningEffects(
    listState: LazyListState,
    scrollPosition: ChatScrollPositionViewModel,
    items: List<ChatMessageListItem>,
    history: ChatHistory,
    newest: ChatMessage?,
    unreadCount: Int,
    focusMessageId: Long,
    hasPendingJump: () -> Boolean,
    pendingRecording: String?,
    bottomThresholdPx: Int,
    listScrollInstant: State<Boolean>
) {
    // Появление кружка в ленте прокручивает к низу, как и исходящее сообщение.
    LaunchedEffect(listState, scrollPosition, pendingRecording) {
        if (pendingRecording != null) listState.scrollToItemRespectingMotion(0, motionInstant = listScrollInstant)
    }
    // Restores the position saved when the chat was left. Another opening owns the position while a jump, a focused message
    // or the unread separator is pending: a restore that ran after the separator was placed would move it.
    LaunchedEffect(listState, scrollPosition, items, history.isLoading) {
        if (scrollPosition.anchorRestored || items.isEmpty()) return@LaunchedEffect
        val unreadOpening = !scrollPosition.readBoundaryChecked && unreadCount > 0
        val anchor = scrollPosition.anchor
        if (focusMessageId != 0L || scrollPosition.focusClaimed || hasPendingJump() || unreadOpening ||
            anchor == null || !anchor.isFresh(System.currentTimeMillis())) {
            scrollPosition.anchorRestored = true
            return@LaunchedEffect
        }
        // While history is still loading, an older anchor may not be in the list yet: wait rather than take a neighbour.
        val found = items.any { item ->
            item.key == anchor.key || (anchor.messageId != 0L && item.messages.any { it.id == anchor.messageId })
        }
        if (!found && history.isLoading) return@LaunchedEffect
        val index = resolveAnchorIndex(items, anchor)
        if (index == null) listState.scrollToItem(0) else listState.scrollToItem(index, anchor.offset)
        scrollPosition.anchorRestored = true
    }
    LaunchedEffect(listState, scrollPosition, newest?.id) {
        // A message sent while the chat opens drops the boundary: the list goes to the bottom and the boundary is not placed.
        val arrived = scrollPosition.seenNewestId != 0L && newest != null && newest.id != scrollPosition.seenNewestId
        scrollPosition.seenNewestId = newest?.id ?: 0L
        // The opening position belongs to the restore effect above until it has finished: following here would undo it.
        if (!arrived && !scrollPosition.anchorRestored) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val localSend = scrollPosition.localSendPending(now)
        if (unreadCount > 0 && !scrollPosition.readBoundaryChecked) {
            if (arrived && newest?.isOutgoing == true && localSend) scrollPosition.readBoundaryChecked = true
            else return@LaunchedEffect
        }
        // The list is newest-first, so the row that was at the bottom before this arrival is now one index lower, with the same key.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        val atBottomPosition = firstIndex == 0 && firstOffset <= bottomThresholdPx
        val previousKey = scrollPosition.previousFirstKey
        val wasAtBottom = atBottomPosition ||
            (previousKey != null && firstOffset <= bottomThresholdPx && items.getOrNull(firstIndex)?.key == previousKey)
        val follow = newest != null && shouldFollowNewest(
            wasAtBottom, listState.isScrollInProgress, newest.isOutgoing, localSend, history.hasNewerMessages
        )
        if (follow) {
            listState.scrollToItemRespectingMotion(0, motionInstant = listScrollInstant)
            if (localSend) scrollPosition.consumeLocalSend()
        }
        if (atBottomPosition || follow) scrollPosition.previousFirstKey = items.firstOrNull()?.key
    }
}
