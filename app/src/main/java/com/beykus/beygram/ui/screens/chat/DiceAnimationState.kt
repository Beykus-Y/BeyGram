package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.content.SharedPreferences
import com.beykus.beygram.domain.model.MessageIdReplacement
import kotlinx.coroutines.sync.Semaphore

/**
 * Identity of one dice throw across the temporary-to-server message id replacement. The account is part of the key
 * because several accounts can contain the same chat and message ids, and a chat is part of it because message ids
 * are only unique inside one chat.
 */
internal data class DiceAnimationKey(val accountId: String, val chatId: Long, val messageId: Long)

/**
 * Keeps finished throws across process restarts, so a restarted process does not replay a throw that is still recent.
 * Implementations are best effort: a lost write can only cause one more replay of a throw that is still recent.
 */
internal interface DicePlaybackPersistence {
    /** Finished throws with the wall-clock time each one finished. Called once, on the first use of the store. */
    fun load(): Map<DiceAnimationKey, Long>

    /** Replaces the persisted set. The store has already pruned and capped [entries]. */
    fun save(entries: Map<DiceAnimationKey, Long>)
}

/**
 * Finished throws in the app's preferences, one string per throw. The account id comes last, so it may contain the
 * separator, while the numeric fields never do.
 */
internal class SharedPreferencesDicePlaybackPersistence(context: Context) : DicePlaybackPersistence {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(DICE_PLAYBACK_PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): Map<DiceAnimationKey, Long> {
        val lines = runCatching { preferences.getStringSet(DICE_PLAYBACK_FINISHED_KEY, null) }.getOrNull().orEmpty()
        val result = LinkedHashMap<DiceAnimationKey, Long>()
        for (line in lines) {
            val (key, finishedAt) = decodeDicePlaybackEntry(line) ?: continue
            result[key] = finishedAt
        }
        return result
    }

    override fun save(entries: Map<DiceAnimationKey, Long>) {
        // SharedPreferences keeps the set it is given, so every save builds a new set instead of editing the old one.
        val lines = HashSet<String>(entries.size)
        for ((key, finishedAt) in entries) lines += encodeDicePlaybackEntry(key, finishedAt)
        preferences.edit().putStringSet(DICE_PLAYBACK_FINISHED_KEY, lines).apply()
    }
}

/** One finished throw per line: chat, message and finish time, then the account, which may itself contain '|'. */
internal fun encodeDicePlaybackEntry(key: DiceAnimationKey, finishedAtMillis: Long): String =
    "${key.chatId}|${key.messageId}|$finishedAtMillis|${key.accountId}"

/** Returns null for a line that does not describe a throw, so one damaged entry never hides the others. */
internal fun decodeDicePlaybackEntry(line: String): Pair<DiceAnimationKey, Long>? {
    val parts = line.split('|', limit = 4)
    if (parts.size != 4) return null
    val chatId = parts[0].toLongOrNull() ?: return null
    val messageId = parts[1].toLongOrNull() ?: return null
    val finishedAt = parts[2].toLongOrNull() ?: return null
    return DiceAnimationKey(accountId = parts[3], chatId = chatId, messageId = messageId) to finishedAt
}

/**
 * Remembers which dice throws already played their animation. It is process-wide, so closing and reopening a chat
 * during the same session never replays a finished throw. [begin] also lets a row that was recreated by the
 * temporary-to-server id replacement continue the same throw instead of treating it as a new message.
 *
 * Entries are bounded; a throw that never reaches its result must not leak memory forever. A continuation is only
 * accepted for [continuationWindowMillis] after the first claim. Finished throws are also written to [persistence], so
 * a restarted process knows they are consumed; the store reads them once, on its first use.
 */
internal class DiceAnimationPlaybackStore(
    private val maxEntries: Int = 256,
    private val continuationWindowMillis: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    persistence: DicePlaybackPersistence? = null
) {
    private enum class State { Playing, Done }

    private class Entry(var state: State, val startedAt: Long, var finishedAt: Long = 0L)

    private val entries = LinkedHashMap<DiceAnimationKey, Entry>()
    private var storage: DicePlaybackPersistence? = persistence
    private var loaded = false

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(continuationWindowMillis > 0) { "continuationWindowMillis must be positive" }
    }

    /** Read-only probe used by composition before it starts collecting an animation permit. */
    @Synchronized
    fun canBegin(key: DiceAnimationKey, eligible: Boolean): Boolean {
        ensureLoaded()
        val entry = entries[key] ?: return eligible
        if (entry.state == State.Done) return false
        return clock() - entry.startedAt <= continuationWindowMillis
    }

    /** Claims the throw once, or continues an unfinished throw whose row was recreated. */
    @Synchronized
    fun begin(key: DiceAnimationKey, eligible: Boolean): Boolean {
        ensureLoaded()
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
            entry.finishedAt = clock()
            persist()
            return false
        }
        return true
    }

    /** Marks a throw as consumed so reopening the chat shows the final frame instead of throwing again. */
    @Synchronized
    fun finish(key: DiceAnimationKey) {
        ensureLoaded()
        val now = clock()
        val entry = entries[key]
        if (entry == null) {
            entries[key] = Entry(State.Done, startedAt = now, finishedAt = now)
            trim()
        } else if (entry.state == State.Done) {
            return
        } else {
            entry.state = State.Done
            entry.finishedAt = now
        }
        persist()
    }

    @Synchronized
    fun isDone(key: DiceAnimationKey): Boolean {
        ensureLoaded()
        return entries[key]?.state == State.Done
    }

    /**
     * Connects the store to its persistence. Only the first call has an effect, so every composable may ask for it.
     * A store that was already used keeps its memory and merges the persisted throws on its next use.
     */
    @Synchronized
    fun attach(persistence: DicePlaybackPersistence) {
        if (storage != null) return
        storage = persistence
        loaded = false
    }

    @Synchronized
    internal fun size(): Int {
        ensureLoaded()
        return entries.size
    }

    /** Merges the throws that finished in earlier processes, once. A throw this process already knows is not changed. */
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val source = storage ?: return
        val now = clock()
        // Oldest first, so the cap in trim() drops the oldest finished throws.
        for ((key, finishedAt) in source.load().entries.sortedBy { it.value }) {
            if (now - finishedAt > DICE_PLAYBACK_RETENTION_MILLIS) continue
            if (entries[key]?.state == State.Done) continue
            entries[key] = Entry(State.Done, startedAt = finishedAt, finishedAt = finishedAt)
        }
        trim()
    }

    /** Writes the finished throws that can still be eligible: recent ones only, newest first, at most [maxEntries]. */
    private fun persist() {
        val target = storage ?: return
        val now = clock()
        val recent = entries.entries.asSequence()
            .filter { it.value.state == State.Done && now - it.value.finishedAt <= DICE_PLAYBACK_RETENTION_MILLIS }
            .sortedByDescending { it.value.finishedAt }
            .take(maxEntries)
            .associate { it.key to it.value.finishedAt }
        target.save(recent)
    }

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

/**
 * Finished throws older than this are not kept. Eligibility needs a message date within [DICE_LIVE_WINDOW_SECONDS],
 * so a throw this old can never replay and does not have to survive a restart.
 */
internal const val DICE_PLAYBACK_RETENTION_MILLIS = 10 * 60_000L

private const val DICE_PLAYBACK_PREFERENCES = "dice_playback"
private const val DICE_PLAYBACK_FINISHED_KEY = "finished"

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
