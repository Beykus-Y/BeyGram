package com.beykus.beygram.data.delayed

import android.content.Context
import android.os.SystemClock
import com.beykus.beygram.data.settings.StealthSettingsStore
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.domain.model.MessageSendOptions
import com.beykus.beygram.domain.model.MessageTextEntity
import com.beykus.beygram.domain.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The account already holds the most delayed messages allowed; the caller keeps the draft. */
internal class DelayedSendQueueFullException : IllegalStateException("Too many delayed messages")

/**
 * Delayed sending for one account. [enqueue] stores a message on disk before the composer is cleared. A stored message is
 * sent through [ConversationRepository.sendMessage] when its time has come, and only while a chat of the account is open
 * ([attach]). Requests run one at a time. A message is marked [DelayedSendStatus.Dispatching] on disk before its request,
 * so after a restart it shows as Uncertain and is never resent by itself.
 */
internal class DelayedSendQueue private constructor(context: Context, private val accountId: String) {
    private val appContext = context.applicationContext
    private val outbox = DelayedSendOutbox(File(appContext.noBackupFilesDir, "delayed-send/$accountId"), accountId)
    private val stealth = StealthSettingsStore.forAccount(appContext, accountId)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatchLock = Mutex()
    private val lock = Any()

    // Guarded by [lock]: ids whose wait is over, and messages held at load under "hide online".
    private val dueIds = HashSet<String>()
    private val heldAtLoad = HashSet<String>()

    private val _entries = MutableStateFlow<List<DelayedSendEntry>>(emptyList())
    val entries: StateFlow<List<DelayedSendEntry>> = _entries.asStateFlow()

    @Volatile private var repository: ConversationRepository? = null

    init {
        val now = System.currentTimeMillis()
        val toSchedule = synchronized(lock) {
            outbox.discardExpired(now, DELAYED_SEND_TTL_MILLIS)
            val stored = outbox.entries()
            val reconciled = reconcileOnLoad(stored, now, stealth.current.hideOnline)
            // Only changed messages are written back. If a write fails, the reconciled state still applies in memory.
            for ((before, after) in stored.zip(reconciled.entries)) {
                if (before != after) runCatching { outbox.save(after) }
            }
            heldAtLoad.addAll(reconciled.heldAtLoad)
            _entries.value = reconciled.entries
            reconciled.entries.filter { it.status == DelayedSendStatus.Queued && it.id !in heldAtLoad }
        }
        toSchedule.forEach { schedule(it) }
    }

    /**
     * Stores a message that is sent after [delaySeconds]. A success means the message is on disk, so the caller may clear
     * the composer then. Fails with [DelayedSendQueueFullException] when the account already holds the maximum.
     */
    suspend fun enqueue(
        chatId: Long,
        topicId: Long,
        replyToMessageId: Long,
        text: String,
        entities: List<MessageTextEntity>,
        disableNotification: Boolean,
        delaySeconds: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val entry = synchronized(lock) {
                if (_entries.value.size >= DELAYED_SEND_MAX_ENTRIES) throw DelayedSendQueueFullException()
                val created = DelayedSendEntry(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    chatId = chatId,
                    topicId = topicId,
                    replyToMessageId = replyToMessageId,
                    text = text,
                    entities = entities,
                    disableNotification = disableNotification,
                    createdAtMillis = now,
                    notBeforeMillis = now + delaySeconds * 1000L,
                    status = DelayedSendStatus.Queued
                )
                outbox.save(created)
                _entries.value = _entries.value + created
                created
            }
            schedule(entry)
        }
    }

    /** Starts sending through [conversation] and sends the messages that are already due. Nothing is sent without an attached chat. */
    fun attach(conversation: ConversationRepository) {
        repository = conversation
        scope.launch { dispatchDue() }
    }

    /** Stops sending until the next [attach]. The messages keep waiting. */
    fun detach() {
        repository = null
    }

    /**
     * Removes a message that is not in flight and returns it, so its text can go back into the composer. Returns null when
     * it is being sent or its file cannot be deleted.
     */
    fun cancel(id: String): DelayedSendEntry? {
        synchronized(lock) {
            val current = _entries.value.firstOrNull { it.id == id } ?: return null
            if (current.status == DelayedSendStatus.Dispatching) return null
            if (!outbox.remove(id)) return null
            removeLocked(id)
            return current
        }
    }

    /** Sends a message now: a queued one before its time, or a failed or uncertain one again. Messages in flight are left alone. */
    fun sendNow(id: String) {
        synchronized(lock) {
            val current = _entries.value.firstOrNull { it.id == id } ?: return
            if (current.status == DelayedSendStatus.Dispatching) return
            if (current.status != DelayedSendStatus.Queued) {
                val queued = current.copy(status = DelayedSendStatus.Queued)
                try {
                    outbox.save(queued)
                } catch (error: IOException) {
                    return
                }
                replaceLocked(queued)
            }
            heldAtLoad.remove(id)
            dueIds.add(id)
        }
        scope.launch { dispatchDue() }
    }

    /** Deletes every delayed message, for logout or account removal. Returns false when some file could not be deleted. */
    fun clear(): Boolean {
        val failures = synchronized(lock) {
            val failed = outbox.clear()
            _entries.value = emptyList()
            dueIds.clear()
            heldAtLoad.clear()
            failed
        }
        return failures == 0
    }

    /**
     * Waits for the message's time on the monotonic clock, so a wall-clock change does not move the wait. The wait is never
     * cancelled: its coroutine may be the one that is sending another message, and it does nothing once its message is gone.
     */
    private fun schedule(entry: DelayedSendEntry) {
        val dueAtElapsed = SystemClock.elapsedRealtime() + (entry.notBeforeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        scope.launch {
            while (true) {
                val left = dueAtElapsed - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                delay(left)
            }
            synchronized(lock) {
                if (_entries.value.any { it.id == entry.id && it.status == DelayedSendStatus.Queued }) dueIds.add(entry.id)
            }
            dispatchDue()
        }
    }

    /** Sends the due messages, oldest first, one at a time. Returns when none is left or no chat is attached. */
    private suspend fun dispatchDue() {
        dispatchLock.withLock {
            val skipped = HashSet<String>()
            while (true) {
                val conversation = repository ?: return
                val next = nextDue().firstOrNull { it.id !in skipped } ?: return
                if (!dispatch(next, conversation)) skipped.add(next.id)
            }
        }
    }

    private fun nextDue(): List<DelayedSendEntry> = synchronized(lock) {
        val blocked: Set<String> = if (stealth.current.hideOnline) heldAtLoad else emptySet()
        val now = System.currentTimeMillis()
        _entries.value
            .filter { it.status == DelayedSendStatus.Queued && it.id !in blocked && (it.id in dueIds || it.notBeforeMillis <= now) }
            .sortedBy { it.createdAtMillis }
    }

    /** Returns false when the message could not be marked as dispatching, in which case it is not sent. */
    private suspend fun dispatch(entry: DelayedSendEntry, conversation: ConversationRepository): Boolean {
        // Marked on disk before the request: a restart in the middle of it shows the message as Uncertain.
        val sending = synchronized(lock) {
            val current = _entries.value.firstOrNull { it.id == entry.id } ?: return false
            if (current.status != DelayedSendStatus.Queued) return false
            val marked = current.copy(status = DelayedSendStatus.Dispatching)
            try {
                outbox.save(marked)
            } catch (error: IOException) {
                return false
            }
            replaceLocked(marked)
            marked
        }
        val result = try {
            conversation.sendMessage(
                chatId = sending.chatId,
                text = sending.text,
                replyToMessageId = sending.replyToMessageId.takeIf { it > 0L },
                entities = sending.entities,
                forumTopicId = sending.topicId,
                clearDraft = false,
                options = MessageSendOptions(disableNotification = sending.disableNotification)
            )
        } catch (error: CancellationException) {
            // Cancelled with the queue: the message stays Dispatching on disk and reads as Uncertain after a restart.
            throw error
        } catch (error: Exception) {
            Result.failure<Unit>(error)
        }
        synchronized(lock) {
            // A message cancelled while in flight is no longer listed; there is nothing left to update.
            if (_entries.value.none { it.id == sending.id }) return true
            if (result.isSuccess) {
                outbox.remove(sending.id)
                removeLocked(sending.id)
            } else {
                val failed = sending.copy(status = DelayedSendStatus.Failed)
                runCatching { outbox.save(failed) }
                replaceLocked(failed)
                dueIds.remove(sending.id)
            }
        }
        return true
    }

    /** Replaces a listed message. Call under [lock]. */
    private fun replaceLocked(entry: DelayedSendEntry) {
        _entries.value = _entries.value.map { if (it.id == entry.id) entry else it }
    }

    /** Removes a listed message and its in-memory marks. Call under [lock]. */
    private fun removeLocked(id: String) {
        _entries.value = _entries.value.filterNot { it.id == id }
        dueIds.remove(id)
        heldAtLoad.remove(id)
    }

    companion object {
        private val instances = ConcurrentHashMap<String, DelayedSendQueue>()

        /** One queue per account, shared by the chat screen and the account's repository. */
        fun forAccount(context: Context, accountId: String): DelayedSendQueue {
            require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for delayed send" }
            return instances.computeIfAbsent(accountId) { DelayedSendQueue(context, accountId) }
        }

        /** Forgets the queue of a removed account and stops its waits. The caller clears its messages first. */
        fun releaseAccount(accountId: String) {
            instances.remove(accountId)?.scope?.cancel()
        }
    }
}
