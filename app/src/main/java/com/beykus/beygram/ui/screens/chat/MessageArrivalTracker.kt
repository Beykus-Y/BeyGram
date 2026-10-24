package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatHistory

/**
 * Decides which messages may play an entrance animation exactly once.
 *
 * The history store records only live insertions in [ChatHistory.liveArrivals]; history pages, edits, delivery status
 * changes and temporary-to-server replacements do not add entries. The first observation of a composition becomes the
 * baseline, so opening a chat, returning to it or recreating the screen never replays animations for messages that were
 * already there. A bounded set keeps arrival claims until the corresponding list row is composed; if a message is never
 * shown, the oldest claims fall out instead of growing without limit.
 */
internal class MessageArrivalTracker(private val maxPending: Int = 64) {
    private var lastSequence: Long? = null
    private var paused = false
    private val pending = LinkedHashSet<Long>()

    /** Reads the live arrival log and returns its sequence. Call before the lazy list composes rows so their claims see the same snapshot. */
    fun observe(history: ChatHistory): Long {
        val currentSequence = history.liveArrivalSequence
        val previous = lastSequence
        lastSequence = currentSequence
        // The first snapshot is whatever the screen opened with, not a burst of new messages.
        if (previous == null || currentSequence <= previous) return currentSequence
        // A background resume may deliver a batch that the user has not seen yet. It becomes the new baseline.
        if (paused) return currentSequence
        val visibleIds = history.messages.mapTo(HashSet()) { it.id }
        history.liveArrivals.asSequence()
            .filter { it.sequence > previous && it.messageId in visibleIds }
            .map { it.messageId }
            .forEach(::enqueue)
        return currentSequence
    }

    /** Called when the screen stops. Messages that arrive while the app is away are not entrances. */
    fun pause() {
        paused = true
        pending.clear()
    }

    /** Called shortly after the screen resumes, once a batch delivered in the background has been observed. */
    fun resume() {
        paused = false
    }

    /** Returns true when any of [messageIds] is a new message that has not animated yet, and consumes those claims. */
    fun claim(messageIds: Collection<Long>): Boolean {
        var claimed = false
        for (id in messageIds) {
            if (pending.remove(id)) claimed = true
        }
        return claimed
    }

    /** Only for tests: the number of arrivals waiting for a row to be composed. */
    internal fun pendingCount(): Int = pending.size

    /** A message that arrives and leaves before its row is composed must not wait forever. */
    private fun enqueue(id: Long) {
        if (!pending.add(id)) return
        while (pending.size > maxPending) {
            val oldest = pending.first()
            pending.remove(oldest)
        }
    }
}
