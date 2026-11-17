package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Whether the chat shows BG Wave cards: not groups, channels, secret chats or Saved Messages. */
internal fun isWaveChat(chat: ChatPreview): Boolean = !chat.isGroup && !chat.isChannel && !chat.isSecret && !chat.isSavedMessages

/**
 * A history snapshot with its timeline rows, grouped off the main thread. [waveFiltered] tells whether the BG Wave signal
 * messages were left out of [rows], so the screen uses the rows only together with the same filter.
 */
internal class PreparedChatTimeline(val history: ChatHistory, val rows: TimelineRows, val waveFiltered: Boolean)

/**
 * Groups every history snapshot on [Dispatchers.Default]. Each collection has its own grouper, used one call at a time.
 * Conflation skips only snapshots that a newer one replaces before they are shown; each emitted value is complete, so the
 * newest history always wins.
 */
internal fun Flow<ChatHistory>.preparedTimeline(waveChat: Boolean): Flow<PreparedChatTimeline> =
    flow<PreparedChatTimeline> {
        val grouper = ChatTimelineGrouper()
        collect { history ->
            val messages = if (waveChat) history.messages.filterNot(::isBgWaveSignalMessage) else history.messages
            emit(PreparedChatTimeline(history, grouper.group(messages), waveChat))
        }
    }.conflate().flowOn(Dispatchers.Default)
