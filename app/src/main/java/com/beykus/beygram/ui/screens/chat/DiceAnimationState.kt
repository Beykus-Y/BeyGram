package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageIdReplacement
import kotlinx.coroutines.sync.Semaphore

/**
 * Identity of one dice throw across the temporary-to-server message id replacement. The account is part of the key
 * because several accounts can contain the same chat and message ids, and a chat is part of it because message ids
 * are only unique inside one chat.
 */
internal data class DiceAnimationKey(val accountId: String, val chatId: Long, val messageId: Long)

/**
 * Remembers which dice throws already played their animation. It is process-wide, so closing and reopening a chat
 * during the same session never replays a finished throw. [begin] also lets a row that was recreated by the
 * temporary-to-server id replacement continue the same throw instead of treating it as a new message.
 *
 * Entries are bounded; a throw that never reaches its result must not leak memory forever. A continuation is only
 * accepted for [continuationWindowMillis] after the first claim.
 */
internal class DiceAnimationPlaybackStore(
    private val maxEntries: Int = 256,
    private val continuationWindowMillis: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private enum class State { Playing, Done }

    private class Entry(var state: State, val startedAt: Long)

    private val entries = LinkedHashMap<DiceAnimationKey, Entry>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(continuationWindowMillis > 0) { "continuationWindowMillis must be positive" }
    }

    /** Read-only probe used by composition before it starts collecting an animation permit. */
    @Synchronized
    fun canBegin(key: DiceAnimationKey, eligible: Boolean): Boolean {
        val entry = entries[key] ?: return eligible
        if (entry.state == State.Done) return false
        return clock() - entry.startedAt <= continuationWindowMillis
    }

    /** Claims the throw once, or continues an unfinished throw whose row was recreated. */
    @Synchronized
    fun begin(key: DiceAnimationKey, eligible: Boolean): Boolean {
        val entry = entries[key]
        if (entry == null) {
            if (!eligible) return false
            entries[key] = Entry(State.Playing, clock())
            trim()
            return true
        }
        if (entry.state == State.Done) return false
        if (clock() - entry.startedAt > continuationWindowMillis) {
            entry.state = State.Done
            return false
        }
        return true
    }

    /** Marks a throw as consumed so reopening the chat shows the final frame instead of throwing again. */
    @Synchronized
    fun finish(key: DiceAnimationKey) {
        val entry = entries[key]
        if (entry == null) {
            entries[key] = Entry(State.Done, clock())
            trim()
        } else {
            entry.state = State.Done
        }
    }

    @Synchronized
    fun isDone(key: DiceAnimationKey): Boolean = entries[key]?.state == State.Done

    @Synchronized
    internal fun size(): Int = entries.size

    private fun trim() {
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: return
            entries.remove(oldest)
        }
    }
}

/** A bounded number of dice animations may play at once, so a burst never keeps several Lottie clocks busy. */
internal class DiceAnimationLimiter(maxConcurrent: Int = 2) {
    private val permits = Semaphore(maxConcurrent)

    init {
        require(maxConcurrent > 0) { "maxConcurrent must be positive" }
    }

    suspend fun acquire() = permits.acquire()

    fun release() = permits.release()
}

internal val diceAnimationPlayback = DiceAnimationPlaybackStore()
internal val diceAnimationLimiter = DiceAnimationLimiter()

/** A fresh outgoing throw is still allowed to animate when its temporary row is replaced by the server id. */
internal const val DICE_LIVE_WINDOW_SECONDS = 60L

internal fun isRecentDiceMessage(
    dateSeconds: Long,
    nowSeconds: Long,
    windowSeconds: Long = DICE_LIVE_WINDOW_SECONDS
): Boolean = dateSeconds > 0 && nowSeconds >= dateSeconds && nowSeconds - dateSeconds <= windowSeconds

/**
 * Walks the temporary-to-server replacement log backwards. Chat rows use a temporary id until the server confirms
 * the send; the dice animation must follow the same throw across that replacement.
 */
internal fun resolveOriginalMessageId(id: Long, replacements: List<MessageIdReplacement>): Long {
    var current = id
    val seen = HashSet<Long>()
    while (seen.add(current)) {
        val previous = replacements.lastOrNull { it.newId == current }?.oldId ?: return current
        if (previous == current) return current
        current = previous
    }
    return current
}
