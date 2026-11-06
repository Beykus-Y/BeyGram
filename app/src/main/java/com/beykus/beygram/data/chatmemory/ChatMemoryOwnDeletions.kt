package com.beykus.beygram.data.chatmemory

/**
 * The deletions this app asked TDLib for. TDLib reports them the way it reports the other side's deletions, so Chat Memory
 * asks here first: a message the user deleted, or a history the user cleared, is not kept. The TDLib update thread and the
 * request threads both use it, so every call holds the lock.
 */
internal class ChatMemoryOwnDeletions {
    private val lock = Any()
    private val requested = HashMap<Long, MutableSet<Long>>()
    private val clearedUpTo = HashMap<Long, Long>()

    /** Called before a request that deletes [messageIds] is sent, since TDLib reports the deletion as it happens. */
    fun expect(chatId: Long, messageIds: Collection<Long>) {
        synchronized(lock) {
            requested.getOrPut(chatId) { HashSet() }.addAll(messageIds)
        }
    }

    /** Called when such a request failed: no deletion of these messages is coming, so the marks go. */
    fun forget(chatId: Long, messageIds: Collection<Long>) {
        synchronized(lock) {
            requested[chatId]?.removeAll(messageIds.toSet())
        }
    }

    /**
     * Called before a request that clears the history of [chatId] is sent: every message up to [lastMessageId] is cleared.
     * Returns the cutoff that was set before, for [restoreClear] when the request fails.
     */
    fun expectClear(chatId: Long, lastMessageId: Long): Long = synchronized(lock) {
        val previous = clearedUpTo[chatId] ?: 0L
        clearedUpTo[chatId] = maxOf(previous, lastMessageId)
        previous
    }

    /** Called when the clear failed: the cutoff of [chatId] goes back to [previous]. */
    fun restoreClear(chatId: Long, previous: Long) {
        synchronized(lock) {
            if (previous <= 0L) clearedUpTo.remove(chatId) else clearedUpTo.put(chatId, previous)
        }
    }

    /**
     * The ids of [messageIds] that this app deleted: a requested id counts once, and so does a message up to a cleared cutoff.
     * A requested id is taken out, so a later report of the same id is the other side's deletion.
     */
    fun takeOwn(chatId: Long, messageIds: Collection<Long>): Set<Long> = synchronized(lock) {
        val cutoff = clearedUpTo[chatId] ?: 0L
        val asked = requested[chatId]
        val own = HashSet<Long>()
        for (messageId in messageIds) {
            val wasAsked = asked?.remove(messageId) == true
            if (wasAsked || messageId <= cutoff) own += messageId
        }
        own
    }
}
