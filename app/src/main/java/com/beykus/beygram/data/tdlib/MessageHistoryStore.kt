package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMemoryMark
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.LiveMessageArrival
import com.beykus.beygram.domain.model.MessageIdReplacement
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageSpecialContent
import kotlinx.coroutines.flow.StateFlow

/**
 * One identity per TDLib message, including temporary -> server ID replacement.
 *
 * [fastMerge] false sorts every merge in full. The fast path gives the same lists; the flag lets the tests run both on the
 * same calls.
 */
internal class MessageHistoryStore(private val maxCachedChats: Int = 16, maxStates: Int = MAX_STATES, private val fastMerge: Boolean = true) {
    private val lock = Any()
    private val retiredIds = mutableMapOf<Long, MutableSet<Long>>()
    private var revision = 0L
    private var liveArrivalSequence = 0L
    // Only messages changed while their chat loads are recorded, so an older snapshot cannot overwrite them.
    private val changed = mutableMapOf<Long, MutableMap<Long, Long>>()
    private val openedChats = mutableSetOf<Long>()
    // Chats that keep their messages, least recently used first.
    private val cachedChats = LinkedHashSet<Long>()
    // Read markers outlive a chat's state, which is dropped while the chat is idle; see [states].
    private val readMarks = HashMap<Long, ReadMarks>()
    // Bumped whenever the loaded window is replaced, so responses to loads started before it are dropped.
    private val windowEpochs = mutableMapOf<Long, Long>()
    /**
     * Chat states. An idle chat is dropped when it leaves the message cache or its screen closes, and the least recently
     * used idle ones go once the map is full. Its read markers stay in [readMarks], so reading the chat again rebuilds
     * exactly the state that was dropped.
     */
    private val states = EvictableStateMap<Long, ChatHistory>(
        lock = lock,
        maxEntries = maxStates,
        seed = { chatId ->
            readMarks[chatId]?.let { ChatHistory(lastReadInboxMessageId = it.inbox, lastReadOutboxMessageId = it.outbox) } ?: ChatHistory()
        },
        isIdle = { chatId, history -> chatId !in openedChats && isBare(history) }
    )
    fun version(): Long = synchronized(lock) { revision }

    /** Number of chat states held now; bounded by the store's limit. */
    internal fun stateCount(): Int = states.size()

    /** Whether the state of [chatId] is held now, for tests and diagnostics. */
    internal fun stateHeld(chatId: Long): Boolean = states.contains(chatId)

    /** Файлы медиа, которые показывают сообщения открытых и загруженных чатов: их локальные копии нужны ленте и просмотрщику. */
    fun mediaFileIds(): Set<Int> = synchronized(lock) {
        (cachedChats + openedChats).flatMap { chatId -> states.peekLocked(chatId)?.let { it.messages + it.outsideWindowMessages }.orEmpty() }
            .flatMap { message ->
                buildList {
                    message.media?.fileId?.let { add(it) }
                    message.media?.thumbnailFileId?.let { add(it) }
                    // Dice animations are part of the message content; keeping them avoids re-downloading every time
                    // the chat is reopened and lets storage cleanup leave a visible throw alone.
                    (message.special as? MessageSpecialContent.Dice)?.dice?.let { dice ->
                        dice.initialState?.assets?.forEach { add(it.fileId); it.thumbnailFileId?.let { thumb -> add(thumb) } }
                        dice.finalState?.assets?.forEach { add(it.fileId); it.thumbnailFileId?.let { thumb -> add(thumb) } }
                    }
                }
            }
            .filter { it != 0 }.toSet()
    }

    /** The state of [chatId] for a writer; the caller holds [lock]. */
    private fun flow(chatId: Long) = states.stateLocked(chatId)
    fun observe(chatId: Long): StateFlow<ChatHistory> = states.observe(chatId)

    fun opened(chatId: Long) = synchronized(lock) {
        openedChats += chatId
        keepCached(chatId)
    }

    fun closed(chatId: Long) = synchronized(lock) {
        openedChats -= chatId
        keepCached(chatId)
        // A chat that holds nothing is not cached, so its state would otherwise wait for the limit.
        states.removeIfIdleLocked(chatId)
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
        val history = state.value
        val changes = changed[chatId].orEmpty()
        // A page copy older than an update that arrived while the page was in flight is replaced: a message the window
        // holds already has the update, and one kept only outside the window takes its kept copy, so no gap is left.
        val incoming = messages.mapNotNull { message ->
            when {
                requestVersion == null || (changes[message.id] ?: 0L) <= requestVersion -> message
                history.messages.any { it.id == message.id } -> null
                else -> history.outsideWindowMessages.firstOrNull { it.id == message.id }
            }
        }
        state.value = history.copy(
            messages = merge(chatId, history.messages, incoming),
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
        // Marked in both lists: a page in flight keeps this copy instead of its older one, see finishLoad.
        markChanged(chatId, messageId)
        state.value = history.copy(
            messages = history.messages.map { if (it.id == messageId) transform(it) else it },
            outsideWindowMessages = history.outsideWindowMessages.map { if (it.id == messageId) transform(it) else it }
        )
    }

    fun readInbox(chatId: Long, messageId: Long) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(lastReadInboxMessageId = maxOf(state.value.lastReadInboxMessageId, messageId))
        rememberReadMarks(chatId, state.value)
    }

    fun readOutbox(chatId: Long, messageId: Long) = synchronized(lock) {
        val state = flow(chatId)
        state.value = state.value.copy(lastReadOutboxMessageId = maxOf(state.value.lastReadOutboxMessageId, messageId))
        rememberReadMarks(chatId, state.value)
    }

    /** Keeps the read markers of a chat, so they survive the chat's state being dropped. */
    private fun rememberReadMarks(chatId: Long, history: ChatHistory) {
        val marks = ReadMarks(history.lastReadInboxMessageId, history.lastReadOutboxMessageId)
        if (marks.inbox == 0L && marks.outbox == 0L) readMarks.remove(chatId) else readMarks[chatId] = marks
    }

    fun fail(chatId: Long, error: UiText, endLoading: Boolean = true, epoch: Long? = null) = synchronized(lock) {
        if (epoch != null && epoch != epochOf(chatId)) return@synchronized
        val state = flow(chatId)
        state.value = state.value.copy(error = error, isLoading = if (endLoading) false else state.value.isLoading)
        if (endLoading) endLoad(chatId)
    }

    /** Chat Memory kept these messages after the other side deleted them: they stay on screen, marked as deleted. */
    fun markDeleted(chatId: Long, ids: Collection<Long>, deletedAtMillis: Long) {
        for (id in ids) {
            update(chatId, id) { message ->
                message.copy(memory = (message.memory ?: ChatMemoryMark()).copy(deletedAtMillis = deletedAtMillis))
            }
        }
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
            // The chat now holds only its read markers, which [readMarks] keeps: its state goes at once unless collected.
            states.removeIfIdleLocked(candidate)
        }
    }

    /** A state that holds nothing but read markers is rebuilt from [readMarks] without any loss. */
    private fun isBare(history: ChatHistory): Boolean = history == ChatHistory(
        lastReadInboxMessageId = history.lastReadInboxMessageId,
        lastReadOutboxMessageId = history.lastReadOutboxMessageId
    )

    /**
     * [current] with [incoming] merged in: one copy per ID, the newer copy wins, and retired IDs are left out. The result is
     * in [compareMessageOrder] order. A small batch into a list that is already in that order takes the fast path.
     */
    private fun merge(chatId: Long, current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        val retired = retiredIds[chatId].orEmpty()
        return if (fastMerge && incoming.size <= MAX_SPLICED_BATCH && isInOrder(current)) {
            spliceBatch(current, incoming, retired)
        } else {
            mergeAll(current, incoming, retired)
        }
    }

    /**
     * Sorts the whole list. Big batches take this path, and so does a list that an update has left out of order, since
     * [update] does not sort. This is the reference that [spliceBatch] has to equal.
     */
    private fun mergeAll(current: List<ChatMessage>, incoming: List<ChatMessage>, retired: Set<Long>): List<ChatMessage> =
        (current + incoming).filterNot { it.id in retired }.associateBy { it.id }.values
            .sortedWith(compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.sendState == MessageSendState.Sending }.thenBy { it.id })

    /**
     * [mergeAll] without the sort, for a small batch: each incoming message replaces its older copy and is inserted at its
     * place by binary search, so [current] is copied and not sorted again. [current] must be in order with one copy per ID.
     */
    private fun spliceBatch(current: List<ChatMessage>, incoming: List<ChatMessage>, retired: Set<Long>): List<ChatMessage> {
        val merged = ArrayList<ChatMessage>(current.size + incoming.size)
        if (retired.isEmpty()) merged.addAll(current) else current.filterNotTo(merged) { it.id in retired }
        for (message in incoming) {
            if (message.id in retired) continue
            val older = indexOfId(merged, message.id)
            if (older >= 0) merged.removeAt(older)
            merged.add(insertionIndex(merged, message), message)
        }
        return merged
    }

    private fun indexOfId(list: List<ChatMessage>, id: Long): Int {
        for (index in list.indices) if (list[index].id == id) return index
        return -1
    }

    /** The position of [message] in [list], which is in order and holds no equal message: right after every message ordered before it. */
    private fun insertionIndex(list: List<ChatMessage>, message: ChatMessage): Int {
        var low = 0
        var high = list.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareMessageOrder(list[middle], message) < 0) low = middle + 1 else high = middle
        }
        return low
    }

    /** Whether [list] is strictly in [compareMessageOrder] order, which [spliceBatch] needs. */
    private fun isInOrder(list: List<ChatMessage>): Boolean {
        for (index in 1 until list.size) if (compareMessageOrder(list[index - 1], list[index]) >= 0) return false
        return true
    }

    private companion object {
        /** Idle chat states kept beyond the chats that hold messages; each one is a small flow. */
        const val MAX_STATES = 512
        const val MAX_RETIRED_IDS_PER_CHAT = 256
        /** Enough to cover a burst of messages delivered while the chat is composing, without growing without bound. */
        const val MAX_LIVE_ARRIVALS = 64
        /** Composer Launch follows an id across its temporary-to-server confirmation. */
        const val MAX_ID_REPLACEMENTS = 64
        /** Single messages and small bursts are spliced in; a history page is sorted in one pass, which is cheaper. */
        const val MAX_SPLICED_BATCH = 16
    }
}

/** The last read positions of a chat, kept while its state is not held. */
private class ReadMarks(val inbox: Long, val outbox: Long)

/**
 * The order of a chat's loaded messages: by date, then the messages still being sent after the others, then by ID. Among
 * messages with distinct IDs it is a total order, so a list in this order has one possible shape. [MessageHistoryStore]'s
 * full sort uses the same order, and a test compares the two.
 */
internal fun compareMessageOrder(a: ChatMessage, b: ChatMessage): Int {
    val byDate = a.dateSeconds.compareTo(b.dateSeconds)
    if (byDate != 0) return byDate
    val aSending = a.sendState == MessageSendState.Sending
    val bSending = b.sendState == MessageSendState.Sending
    if (aSending != bSending) return if (aSending) 1 else -1
    return a.id.compareTo(b.id)
}
