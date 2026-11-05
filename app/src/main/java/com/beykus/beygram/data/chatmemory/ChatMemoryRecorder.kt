package com.beykus.beygram.data.chatmemory

import android.content.Context
import android.util.Log
import com.beykus.beygram.data.settings.ChatMemorySettingsStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Chat Memory of one account as the repository uses it. Writes that follow TDLib updates and history pages are queued on
 * one background thread, so they apply in the order TDLib produced them. A deletion is written at once, and so are the
 * reads that a history page needs: a page must see a deletion that was already handled, or it would drop a kept message.
 * A storage failure is logged and skipped; it never breaks the chat that was being kept.
 */
internal class ChatMemoryRecorder(
    private val context: Context,
    private val accountId: String,
    private val settings: ChatMemorySettingsStore,
    private val factsOf: (Long) -> ChatMemoryChatFacts,
    private val onVersionsKept: (Map<Pair<Long, Long>, Int>) -> Unit
) {
    private val store: ChatMemoryStore by lazy { ChatMemoryStore.forAccount(context, accountId) }
    private val writer: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "chat-memory-$accountId").apply { isDaemon = true }
    }

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

    /** Keeps the messages the other side deleted. This runs at once, so a history page that follows sees the deletion. */
    fun markDeleted(chatId: Long, messageIds: Collection<Long>, fallback: Map<Long, MemoryObservation>) {
        if (messageIds.isEmpty()) return
        guarded("deletion was not stored") { store.markDeleted(chatId, messageIds, fallback, System.currentTimeMillis()) }
        writer.execute { trimIfDue(force = false) }
    }

    /** The deleted messages of [chatId] within the id range, oldest first. Empty when the store cannot be read. */
    fun deletedBetween(chatId: Long, lowestId: Long, highestId: Long): List<MemorizedMessage> =
        read(emptyList()) { store.deletedMessages(chatId, lowestId, highestId) }

    /** The number of earlier texts kept for each of [messageIds] that has any. */
    fun versionCounts(chatId: Long, messageIds: Collection<Long>): Map<Long, Int> =
        read(emptyMap()) { store.versionCounts(chatId, messageIds) }

    /** The earlier texts kept for one message, oldest first. */
    fun versions(chatId: Long, messageId: Long): List<MemorizedVersion> =
        read(emptyList()) { store.versions(chatId, messageId) }

    /** Removes everything kept for the account. The switches stay. */
    fun clearAll() {
        guarded("clearing failed") { store.clearAll() }
    }

    /** Brings the store back under the limit of the switches now, instead of at the next scheduled trim. */
    fun trimNow() {
        writer.execute { trimIfDue(force = true) }
    }

    private fun enqueue(write: () -> Map<Pair<Long, Long>, Int>) {
        writer.execute {
            try {
                val archived = write()
                if (archived.isNotEmpty()) onVersionsKept(archived)
            } catch (e: Exception) {
                Log.w(TAG, "Chat memory: write failed", e)
            }
            trimIfDue(force = false)
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

    private fun <T> read(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Chat memory: read failed", e)
        fallback
    }

    private companion object {
        const val TAG = "BeyGramChatMemory"
        const val TRIM_EVERY_WRITES = 20
    }
}
