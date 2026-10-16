package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** One identity per TDLib message, including temporary -> server ID replacement. */
internal class MessageHistoryStore {
    private val states = ConcurrentHashMap<Long, MutableStateFlow<ChatHistory>>()
    private val retiredIds = mutableMapOf<Long, MutableSet<Long>>()
    private val lock = Any()
    private var revision = 0L
    private val changed = mutableMapOf<Pair<Long, Long>, Long>()
    fun version(): Long = synchronized(lock) { revision }

    private fun flow(chatId: Long) = states.computeIfAbsent(chatId) { MutableStateFlow(ChatHistory()) }
    fun observe(chatId: Long): StateFlow<ChatHistory> = flow(chatId).asStateFlow()

    fun put(message: ChatMessage, replaces: Long? = null) = synchronized(lock) {
        changed[message.chatId to message.id] = ++revision
        if (replaces != null && replaces != message.id) retiredIds.getOrPut(message.chatId) { mutableSetOf() }.add(replaces)
        val state = flow(message.chatId)
        state.value = state.value.copy(messages = merge(message.chatId, state.value.messages, listOf(message)))
    }

    fun beginLoad(chatId: Long): Boolean = synchronized(lock) {
        val state = flow(chatId)
        if (state.value.isLoading) false else {
            state.value = state.value.copy(isLoading = true, error = null)
            true
        }
    }

    fun finishLoad(chatId: Long, messages: List<ChatMessage>, hasOlder: Boolean, continueLoading: Boolean = false, requestVersion: Long? = null) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(
            messages = merge(chatId, state.value.messages, messages.filter { requestVersion == null || (changed[chatId to it.id] ?: 0L) <= requestVersion }),
            isLoading = continueLoading,
            hasOlderMessages = hasOlder,
            error = null
        )
    }

    fun update(chatId: Long, messageId: Long, transform: (ChatMessage) -> ChatMessage) = synchronized(lock) {
        val state = flow(chatId)
        if (state.value.messages.none { it.id == messageId }) return@synchronized
        changed[chatId to messageId] = ++revision
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

    fun fail(chatId: Long, error: String, endLoading: Boolean = true) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(error = error, isLoading = if (endLoading) false else state.value.isLoading)
    }

    fun delete(chatId: Long, ids: Collection<Long>) = synchronized(lock) {
        retiredIds.getOrPut(chatId) { mutableSetOf() }.addAll(ids)
        val state = flow(chatId)
        state.value = state.value.copy(messages = state.value.messages.filterNot { it.id in ids })
    }

    private fun merge(chatId: Long, current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        val retired = retiredIds[chatId].orEmpty()
        return (current + incoming).filterNot { it.id in retired }.associateBy { it.id }.values
            .sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.sendState == MessageSendState.Sending }.thenBy { it.id })
    }
}
