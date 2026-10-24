package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.LiveMessageArrival
import com.beykus.beygram.domain.model.MessageIdReplacement
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
    private var liveArrivalSequence = 0L
    // Only messages changed while their chat loads are recorded, so an older snapshot cannot overwrite them.
    private val changed = mutableMapOf<Long, MutableMap<Long, Long>>()
    private val openedChats = mutableSetOf<Long>()
    // Chats that keep their messages, least recently used first.
    private val cachedChats = LinkedHashSet<Long>()
    // Bumped whenever the loaded window is replaced, so responses to loads started before it are dropped.
    private val windowEpochs = mutableMapOf<Long, Long>()
    fun version(): Long = synchronized(lock) { revision }

    /** Файлы медиа, которые показывают сообщения открытых и загруженных чатов: их локальные копии нужны ленте и просмотрщику. */
    fun mediaFileIds(): Set<Int> = synchronized(lock) {
        (cachedChats + openedChats).flatMap { chatId -> states[chatId]?.value?.let { it.messages + it.outsideWindowMessages }.orEmpty() }
            .flatMap { message -> listOfNotNull(message.media?.fileId, message.media?.thumbnailFileId) }
            .filter { it != 0 }.toSet()
    }

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
        // A jump window does not reach the newest messages, so an incoming message would leave a gap; it shows once they load.
        if (state.value.hasNewerMessages && replaces == null && message.sendState == MessageSendState.Sent && state.value.messages.none { it.id == message.id }) return@synchronized
        val history = state.value
        val replacedWasVisible = replaces != null && history.messages.any { it.id == replaces }
        val inserted = history.messages.none { it.id == message.id }
        // A confirmed send replaces the temporary bubble the user already saw, so the server id is not a new arrival.
        val arrival = if (inserted && !replacedWasVisible) LiveMessageArrival(++liveArrivalSequence, message.id) else null
        val arrivals = if (arrival == null) history.liveArrivals else (history.liveArrivals + arrival).takeLast(MAX_LIVE_ARRIVALS)
        val replacements = if (replaces != null && replaces != message.id) {
            (history.recentReplacements + MessageIdReplacement(replaces, message.id)).takeLast(MAX_ID_REPLACEMENTS)
        } else history.recentReplacements
        state.value = history.copy(
            messages = merge(message.chatId, history.messages, listOf(message)),
            liveArrivals = arrivals,
            liveArrivalSequence = arrival?.sequence ?: history.liveArrivalSequence,
            recentReplacements = replacements,
            outsideWindowMessages = history.outsideWindowMessages.map { if (it.id == message.id) message else it }
        )
        keepCached(message.chatId)
    }

    /** Replaces the loaded messages with a window around a jumped-to message; unsent messages stay and loads in flight are dropped. */
    fun replaceWithWindow(chatId: Long, messages: List<ChatMessage>, reachesLatest: Boolean) = synchronized(lock) {
        val state = flow(chatId)
        val unsent = state.value.messages.filter { it.sendState != MessageSendState.Sent }
        windowEpochs[chatId] = epochOf(chatId) + 1
        state.value = state.value.copy(
            messages = merge(chatId, unsent, messages),
            isLoading = false,
            hasOlderMessages = true,
            hasNewerMessages = !reachesLatest,
            error = null
        )
        endLoad(chatId)
        keepCached(chatId)
    }

    /** Starts loading the newest messages; a jump window is dropped first, and loads started before this call are dropped too. */
    fun restartFromLatest(chatId: Long): Long = synchronized(lock) {
        val state = flow(chatId)
        windowEpochs[chatId] = epochOf(chatId) + 1
        val history = state.value
        state.value = if (history.hasNewerMessages) history.copy(
            messages = history.messages.filter { it.sendState != MessageSendState.Sent },
            hasOlderMessages = true,
            hasNewerMessages = false,
            isLoading = true,
            error = null
        ) else history.copy(isLoading = true, error = null)
        changed.remove(chatId)
        keepCached(chatId)
        epochOf(chatId)
    }

    /** Returns the epoch of the started load, or null when a load is already running. */
    fun beginLoad(chatId: Long): Long? = synchronized(lock) {
        val state = flow(chatId)
        if (state.value.isLoading) null else {
            state.value = state.value.copy(isLoading = true, error = null)
            keepCached(chatId)
            epochOf(chatId)
        }
    }

    private fun epochOf(chatId: Long): Long = windowEpochs[chatId] ?: 0L

    /** Returns false when the load started before the window was replaced, so its page is dropped. */
    fun finishLoad(chatId: Long, messages: List<ChatMessage>, hasOlder: Boolean, continueLoading: Boolean = false, requestVersion: Long? = null, epoch: Long? = null): Boolean = synchronized(lock) {
        if (epoch != null && epoch != epochOf(chatId)) return@synchronized false
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
        true
    }

    /**
     * Keeps messages loaded outside the window, such as older thread comments, so later updates and deletions still reach
     * them. A copy already kept wins: a page may be older than an update that arrived after its request was sent.
     */
    fun recordOutsideWindow(chatId: Long, messages: List<ChatMessage>) = synchronized(lock) {
        val state = flow(chatId)
        val history = state.value
        val known = history.outsideWindowMessages.mapTo(HashSet<Long>()) { it.id }
        val retired = retiredIds[chatId].orEmpty()
        val added = messages.filter { it.id !in known && it.id !in retired }.distinctBy { it.id }
        if (added.isEmpty()) return@synchronized
        state.value = history.copy(outsideWindowMessages = history.outsideWindowMessages + added)
        keepCached(chatId)
    }

    fun update(chatId: Long, messageId: Long, transform: (ChatMessage) -> ChatMessage) = synchronized(lock) {
        val state = flow(chatId)
        val history = state.value
        val inWindow = history.messages.any { it.id == messageId }
        val outside = history.outsideWindowMessages.any { it.id == messageId }
        if (!inWindow && !outside) return@synchronized
        // An outside-only message is not marked changed: a window page holding it would drop its copy and leave a gap.
        if (inWindow) markChanged(chatId, messageId)
        state.value = history.copy(
            messages = history.messages.map { if (it.id == messageId) transform(it) else it },
            outsideWindowMessages = history.outsideWindowMessages.map { if (it.id == messageId) transform(it) else it }
        )
    }

    fun readInbox(chatId: Long, messageId: Long) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(lastReadInboxMessageId = maxOf(state.value.lastReadInboxMessageId, messageId))
    }

    fun readOutbox(chatId: Long, messageId: Long) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(lastReadOutboxMessageId = maxOf(state.value.lastReadOutboxMessageId, messageId))
    }

    fun fail(chatId: Long, error: UiText, endLoading: Boolean = true, epoch: Long? = null) = synchronized(lock) {
        if (epoch != null && epoch != epochOf(chatId)) return@synchronized
        val state = flow(chatId)
        state.value = state.value.copy(error = error, isLoading = if (endLoading) false else state.value.isLoading)
        if (endLoading) endLoad(chatId)
    }

    fun delete(chatId: Long, ids: Collection<Long>) = synchronized(lock) {
        retire(chatId, ids)
        val state = flow(chatId)
        state.value = state.value.copy(
            messages = state.value.messages.filterNot { it.id in ids },
            outsideWindowMessages = state.value.outsideWindowMessages.filterNot { it.id in ids }
        )
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
        val history = flow(chatId).value
        if (history.messages.isEmpty() && history.outsideWindowMessages.isEmpty()) return
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
        /** Enough to cover a burst of messages delivered while the chat is composing, without growing without bound. */
        const val MAX_LIVE_ARRIVALS = 64
        /** Composer Launch follows an id across its temporary-to-server confirmation. */
        const val MAX_ID_REPLACEMENTS = 64
    }
}
