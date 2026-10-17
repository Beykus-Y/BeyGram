package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** One identity per TDLib message, including temporary -> server ID replacement. */
internal class MessageHistoryStore(private val maxCachedChats: Int = 16) {
    private val states = ConcurrentHashMap<Long, MutableStateFlow<ChatHistory>>()
    private val retiredIds = mutableMapOf<Long, MutableSet<Long>>()
    private val lock = Any()
    private var revision = 0L
    // Only messages changed while their chat loads are recorded, so an older snapshot cannot overwrite them.
    private val changed = mutableMapOf<Long, MutableMap<Long, Long>>()
    private val openedChats = mutableSetOf<Long>()
    // Chats that keep their messages, least recently used first.
    private val cachedChats = LinkedHashSet<Long>()
    fun version(): Long = synchronized(lock) { revision }

    private fun flow(chatId: Long) = states.computeIfAbsent(chatId) { MutableStateFlow(ChatHistory()) }
    fun observe(chatId: Long): StateFlow<ChatHistory> = flow(chatId).asStateFlow()

    fun opened(chatId: Long) = synchronized(lock) {
        openedChats += chatId
        keepCached(chatId)
    }

    fun closed(chatId: Long) = synchronized(lock) {
        openedChats -= chatId
        keepCached(chatId)
    }

    fun put(message: ChatMessage, replaces: Long? = null) = synchronized(lock) {
        markChanged(message.chatId, message.id)
        if (replaces != null && replaces != message.id) retire(message.chatId, listOf(replaces))
        val state = flow(message.chatId)
        state.value = state.value.copy(messages = merge(message.chatId, state.value.messages, listOf(message)))
        keepCached(message.chatId)
    }

    fun beginLoad(chatId: Long): Boolean = synchronized(lock) {
        val state = flow(chatId)
        if (state.value.isLoading) false else {
            state.value = state.value.copy(isLoading = true, error = null)
            keepCached(chatId)
            true
        }
    }

    fun finishLoad(chatId: Long, messages: List<ChatMessage>, hasOlder: Boolean, continueLoading: Boolean = false, requestVersion: Long? = null) = synchronized(lock) {
        val state = flow(chatId)
        val changes = changed[chatId].orEmpty()
        state.value = state.value.copy(
            messages = merge(chatId, state.value.messages, messages.filter { requestVersion == null || (changes[it.id] ?: 0L) <= requestVersion }),
            isLoading = continueLoading,
            hasOlderMessages = hasOlder,
            error = null
        )
        if (!continueLoading) endLoad(chatId)
        keepCached(chatId)
    }

    fun update(chatId: Long, messageId: Long, transform: (ChatMessage) -> ChatMessage) = synchronized(lock) {
        val state = flow(chatId)
        if (state.value.messages.none { it.id == messageId }) return@synchronized
        markChanged(chatId, messageId)
        state.value = state.value.copy(messages = state.value.messages.map { if (it.id == messageId) transform(it) else it })
    }

    fun readInbox(chatId: Long, messageId: Long) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(lastReadInboxMessageId = maxOf(state.value.lastReadInboxMessageId, messageId))
    }

    fun readOutbox(chatId: Long, messageId: Long) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(lastReadOutboxMessageId = maxOf(state.value.lastReadOutboxMessageId, messageId))
    }

    fun fail(chatId: Long, error: UiText, endLoading: Boolean = true) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(error = error, isLoading = if (endLoading) false else state.value.isLoading)
        if (endLoading) endLoad(chatId)
    }

    fun delete(chatId: Long, ids: Collection<Long>) = synchronized(lock) {
        retire(chatId, ids)
        val state = flow(chatId)
        state.value = state.value.copy(messages = state.value.messages.filterNot { it.id in ids })
    }

    private fun markChanged(chatId: Long, messageId: Long) {
        val current = ++revision
        if (flow(chatId).value.isLoading) changed.getOrPut(chatId) { mutableMapOf() }[messageId] = current
    }

    private fun retire(chatId: Long, ids: Collection<Long>) {
        retiredIds.getOrPut(chatId) { mutableSetOf() }.addAll(ids)
        // A running load may still return snapshots from before these ids were retired, so the trim waits for it.
        if (!flow(chatId).value.isLoading) trimRetired(chatId)
    }

    private fun endLoad(chatId: Long) {
        changed.remove(chatId)
        trimRetired(chatId)
    }

    private fun trimRetired(chatId: Long) {
        val retired = retiredIds[chatId] ?: return
        // Late responses only concern recent sends and deletions, so the oldest entries go first.
        while (retired.size > MAX_RETIRED_IDS_PER_CHAT) retired.remove(retired.first())
    }

    // Drops the messages of least recently used chats beyond the limit. Open, loading and unsent chats stay,
    // because a screen, a load or a send still refers to them.
    private fun keepCached(chatId: Long) {
        cachedChats.remove(chatId)
        // A chat without messages holds no memory, so it takes no slot.
        if (flow(chatId).value.messages.isEmpty()) return
        cachedChats.add(chatId)
        val candidates = cachedChats.iterator()
        while (cachedChats.size > maxCachedChats && candidates.hasNext()) {
            val candidate = candidates.next()
            val state = flow(candidate)
            if (candidate == chatId || candidate in openedChats || state.value.isLoading) continue
            if (state.value.messages.any { it.sendState != MessageSendState.Sent }) continue
            candidates.remove()
            state.value = ChatHistory(
                lastReadInboxMessageId = state.value.lastReadInboxMessageId,
                lastReadOutboxMessageId = state.value.lastReadOutboxMessageId
            )
        }
    }

    private fun merge(chatId: Long, current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        val retired = retiredIds[chatId].orEmpty()
        return (current + incoming).filterNot { it.id in retired }.associateBy { it.id }.values
            .sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.sendState == MessageSendState.Sending }.thenBy { it.id })
    }

    private companion object {
        const val MAX_RETIRED_IDS_PER_CHAT = 256
    }
}
