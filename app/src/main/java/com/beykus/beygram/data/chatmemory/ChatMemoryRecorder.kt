package com.beykus.beygram.data.chatmemory

import android.content.Context
import android.util.Log
import com.beykus.beygram.data.settings.ChatMemorySettingsStore
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Chat Memory of one account as the repository uses it. Writes that follow TDLib updates and history pages are queued on
 * one background thread, so they apply in the order TDLib produced them. Nothing on the TDLib update thread waits for that
 * queue: a deletion is queued and not waited for, and a read sees what is stored plus the deletions still queued. A caller
 * that must see the other queued writes calls [awaitWrites] first, from a thread that may wait. A storage failure is logged
 * and skipped; it never breaks the chat that was being kept.
 */
internal class ChatMemoryRecorder(
    private val context: Context,
    private val accountId: String,
    private val settings: ChatMemorySettingsStore,
    private val factsOf: (Long) -> ChatMemoryChatFacts,
    private val onVersionsKept: (Map<Pair<Long, Long>, Int>) -> Unit,
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "chat-memory-$accountId").apply { isDaemon = true }
    }
) {
    private val store: ChatMemoryStore by lazy { ChatMemoryStore.forAccount(context, accountId) }

    // The thread the writer runs on, so that a caller on the writer knows it must not wait for the writer.
    private val writerThread = AtomicReference<Thread?>(null)

    // The deletions queued for the writer, as the store will show them once it has stored them. A read sees them at once, so a
    // page read before the writer runs still shows them. An entry is removed only by the write that added it.
    private val queuedDeletions = ConcurrentHashMap<Pair<Long, Long>, QueuedDeletion>()

    // Set when the repository is disposed or its account is removed. Reads stop then, so no database is opened again.
    @Volatile private var stopped = false

    // Touched only on the writer thread.
    private var writesSinceTrim = 0

    /** Whether the feature is on for the account at all. */
    val enabled: Boolean get() = settings.current.enabled

    /** Whether Chat Memory keeps the messages of [chatId] now. While the feature is off, this touches no disk and no chat. */
    fun keeps(chatId: Long): Boolean {
        val current = settings.current
        return current.enabled && chatMemorySaves(current, chatId, factsOf(chatId))
    }

    /** Queues messages as TDLib shows them now: a new message is stored, and an edit keeps the text it replaced. */
    fun recordSeen(observations: List<MemoryObservation>) {
        if (observations.isEmpty()) return
        val now = System.currentTimeMillis()
        enqueue { store.observe(observations, now, insertMissing = true) }
    }

    /** Queues a change of text. A message Chat Memory does not know yet is not stored, because its earlier text is unknown. */
    fun recordChange(chatId: Long, messageId: Long, snapshot: MemorySnapshot) {
        val observation = MemoryObservation(
            chatId = chatId,
            messageId = messageId,
            isOutgoing = false,
            dateSeconds = 0L,
            senderId = 0L,
            senderName = "",
            snapshot = snapshot
        )
        val now = System.currentTimeMillis()
        enqueue { store.observe(listOf(observation), now, insertMissing = false) }
    }

    /**
     * Queues the deletions the other side made. The deletion runs after the writes already queued, so a message that is
     * still being recorded is stored before it is marked. The caller does not wait for it.
     */
    fun markDeleted(chatId: Long, messageIds: Collection<Long>, fallback: Map<Long, MemoryObservation>) {
        if (messageIds.isEmpty() || stopped) return
        val now = System.currentTimeMillis()
        // Shown at once, the way the store will show it, so a read before the writer runs does not lose the deletion.
        val queued = messageIds.mapNotNull { id ->
            queuedDeletionOf(chatId, id, fallback[id], now)?.let { (chatId to id) to it }
        }
        queued.forEach { (key, deletion) -> queuedDeletions[key] = deletion }
        val accepted = enqueue {
            try {
                store.markDeleted(chatId, messageIds, fallback, now)
            } finally {
                unqueue(queued)
            }
            emptyMap()
        }
        if (!accepted) unqueue(queued)
    }

    /**
     * Queues the removal of the copies of messages the user deleted or cleared. Their text is not kept. A deletion the other
     * side made stays kept, so a copy that is already kept is left alone.
     */
    fun forget(chatId: Long, messageIds: Collection<Long>) {
        if (messageIds.isEmpty()) return
        enqueue {
            if (ChatMemoryStore.hasDatabase(context, accountId)) store.forget(chatId, messageIds)
            emptyMap()
        }
    }

    /**
     * The deleted messages of [chatId] within the id range, oldest first, including the deletions still queued. Empty when the
     * store cannot be read or the recorder is stopped.
     */
    fun deletedBetween(chatId: Long, lowestId: Long, highestId: Long): List<MemorizedMessage> {
        if (stopped) return emptyList()
        // The queued ones are taken first: a deletion the writer stores in between is then found in the store, not missed.
        val queued = queuedDeletions
            .filterKeys { (queuedChat, id) -> queuedChat == chatId && id in lowestId..highestId }
            .values.map { it.message }
        val stored = read(emptyList()) { store.deletedMessages(chatId, lowestId, highestId) }
        val storedIds = stored.mapTo(HashSet()) { it.messageId }
        return (stored + queued.filter { it.messageId !in storedIds }).sortedBy { it.messageId }
    }

    /** The number of earlier texts kept for each of [messageIds] that has any. */
    fun versionCounts(chatId: Long, messageIds: Collection<Long>): Map<Long, Int> =
        read(emptyMap()) { store.versionCounts(chatId, messageIds) }

    /** The earlier texts kept for one message, oldest first. */
    fun versions(chatId: Long, messageId: Long): List<MemorizedVersion> =
        read(emptyList()) { store.versions(chatId, messageId) }

    /**
     * Waits until the writes queued so far are stored, so that a read after it sees them. Never call it on the TDLib update
     * thread: it waits for the writer.
     */
    fun awaitWrites() {
        afterWrites("waiting for the writer failed", Unit) {}
    }

    /** Removes everything kept for the account, after the writes already queued. The switches stay. */
    fun clearAll() {
        enqueue {
            clearNow()
            emptyMap()
        }
    }

    /** Like [clearAll], but returns once the store is empty, so a settings screen can show the result. */
    fun clearAllAndWait() {
        afterWrites("clearing failed", Unit) { clearNow() }
    }

    /** Brings the store back under the limit of the switches now, instead of at the next scheduled trim. */
    fun trimNow() {
        enqueue {
            trimIfDue(force = true)
            emptyMap()
        }
    }

    /** Like [trimNow], but returns once the store is under the limit, so a settings screen can show the result. */
    fun trimAndWait() {
        afterWrites("trim failed", Unit) { trimIfDue(force = true) }
    }

    /** What is kept now. Empty for an account that never kept anything, so that no database is opened to answer. */
    fun usage(): ChatMemoryUsage {
        val empty = ChatMemoryUsage(messages = 0, deletedMessages = 0, versions = 0, bytes = 0L)
        if (stopped || !ChatMemoryStore.hasDatabase(context, accountId)) return empty
        return read(empty) { store.usage() }
    }

    /**
     * Stops the writer from taking new work and reads from opening the store again. The writes already queued still run,
     * so that nothing accepted is lost. Does not wait, so the TDLib update thread may call it.
     */
    fun stopWriting() {
        stopped = true
        // The writer's last task takes this recorder out of the registry, so a disposed repository is not kept alive by it. The
        // task runs after the writes already queued, so the account's files are never removed under a queued write.
        try {
            writer.execute { running[accountId]?.remove(this) }
        } catch (e: RejectedExecutionException) {
            // Already stopped: the task was queued by the first call.
        }
        writer.shutdown()
    }

    /** Waits up to [waitMillis] for the writer to finish. False while it is still busy. */
    private fun awaitStopped(waitMillis: Long): Boolean {
        if (writerThread.get() === Thread.currentThread()) return false
        return try {
            writer.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    // Clearing an account that never kept anything would open a database only to empty it, so that case is skipped.
    private fun clearNow() {
        guarded("clearing failed") { if (ChatMemoryStore.hasDatabase(context, accountId)) store.clearAll() }
    }

    /**
     * Queues [write] on the writer, after the writes already queued. A failure is logged, never thrown. Once the writer is shut
     * down, nothing more is queued: the write is simply not made, and false is returned.
     */
    private fun enqueue(write: () -> Map<Pair<Long, Long>, Int>): Boolean {
        return try {
            writer.execute {
                writerThread.set(Thread.currentThread())
                try {
                    val archived = write()
                    if (archived.isNotEmpty()) onVersionsKept(archived)
                } catch (e: Exception) {
                    Log.w(TAG, "Chat memory: write failed", e)
                }
                trimIfDue(force = false)
            }
            true
        } catch (e: RejectedExecutionException) {
            // The writer is shut down, so the repository or its account is gone: nothing more is written for it.
            false
        }
    }

    /**
     * The copy the store will show once it has stored the deletion of [messageId]: a stored message gets its deletion mark, and a
     * message not stored yet gets the copy on screen. A message of ours is never kept, and a message already deleted needs no copy.
     */
    private fun queuedDeletionOf(chatId: Long, messageId: Long, onScreen: MemoryObservation?, nowMillis: Long): QueuedDeletion? {
        val stored = read(null) { store.memorized(chatId, messageId) }
        if (stored != null) {
            return if (stored.isOutgoing || stored.deletedAtMillis != null) null
            else QueuedDeletion(stored.copy(deletedAtMillis = nowMillis))
        }
        val known = onScreen?.takeUnless { it.isOutgoing } ?: return null
        return QueuedDeletion(
            MemorizedMessage(
                chatId = chatId,
                messageId = messageId,
                isOutgoing = false,
                dateSeconds = known.dateSeconds,
                senderId = known.senderId,
                senderName = known.senderName,
                text = known.snapshot.text,
                hasAttachment = known.snapshot.hasAttachment,
                deletedAtMillis = nowMillis,
                versionCount = 0
            )
        )
    }

    private fun unqueue(queued: List<Pair<Pair<Long, Long>, QueuedDeletion>>) {
        queued.forEach { (key, deletion) -> queuedDeletions.remove(key, deletion) }
    }

    // A deletion on its way to the store. It is compared by identity, so a write removes only the entry it added.
    private class QueuedDeletion(val message: MemorizedMessage)

    /**
     * Runs [block] on the writer, after the writes already queued, and returns its result, or [fallback] when it fails. On the
     * writer itself the block runs at once, because waiting there would wait for the writer's own task.
     */
    private fun <T> afterWrites(what: String, fallback: T, block: () -> T): T {
        if (stopped) return fallback
        if (writerThread.get() === Thread.currentThread()) return runOn(what, fallback, block)
        return try {
            writer.submit(Callable {
                writerThread.set(Thread.currentThread())
                runOn(what, fallback, block)
            }).get()
        } catch (e: RejectedExecutionException) {
            fallback
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            fallback
        } catch (e: ExecutionException) {
            Log.w(TAG, "Chat memory: $what", e)
            fallback
        }
    }

    private fun trimIfDue(force: Boolean) {
        writesSinceTrim++
        if (!force && writesSinceTrim < TRIM_EVERY_WRITES) return
        writesSinceTrim = 0
        guarded("trim failed") { store.trim(settings.current.limitBytes) }
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Chat memory: $what", e)
        }
    }

    private fun <T> runOn(what: String, fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Chat memory: $what", e)
            fallback
        }

    private fun <T> read(fallback: T, block: () -> T): T {
        if (stopped) return fallback
        return try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Chat memory: read failed", e)
            fallback
        }
    }

    init {
        running.computeIfAbsent(accountId) { ConcurrentHashMap.newKeySet<ChatMemoryRecorder>() }.add(this)
    }

    companion object {
        private const val TAG = "BeyGramChatMemory"
        private const val TRIM_EVERY_WRITES = 20

        // The recorders that are alive, by account. Removing an account stops them and waits for their writers first.
        private val running = ConcurrentHashMap<String, MutableSet<ChatMemoryRecorder>>()

        /** How many recorders of [accountId] are still registered. Tests read it to see that a stopped recorder is released. */
        internal fun registeredCount(accountId: String): Int = running[accountId]?.size ?: 0

        /**
         * Stops the recorders of [accountId] and waits up to [waitMillis] for their writers, so that the writes already queued
         * are stored before the account's files go. Returns true only when no writer is still busy.
         */
        fun stopAccount(accountId: String, waitMillis: Long): Boolean {
            val recorders = running[accountId]?.toList().orEmpty()
            var allStopped = true
            for (recorder in recorders) {
                recorder.stopWriting()
                if (recorder.awaitStopped(waitMillis)) {
                    running[accountId]?.remove(recorder)
                } else {
                    allStopped = false
                }
            }
            return allStopped
        }
    }
}
