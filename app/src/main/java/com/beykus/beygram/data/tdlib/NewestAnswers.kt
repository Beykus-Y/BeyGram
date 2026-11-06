package com.beykus.beygram.data.tdlib

/**
 * Keeps an answer that TDLib delivers late from replacing a newer list. A request takes a ticket when it starts, and a change
 * of the list takes one when it is made. An answer is accepted only when its ticket is newer than the last accepted answer and
 * than the last change of the same key, so a list that a newer answer or a change has already replaced stays replaced.
 */
internal class NewestAnswers<K> {
    private val lock = Any()
    private var issued = 0L
    private val accepted = HashMap<K, Long>()
    private val changed = HashMap<K, Long>()

    /** A ticket for a request that starts now. */
    fun start(): Long = synchronized(lock) { ++issued }

    /** Marks a change of the list for [key] made now: requests that started before it no longer count. */
    fun change(key: K) {
        synchronized(lock) { changed[key] = ++issued }
    }

    /** Whether the answer with [ticket] may replace the list for [key]. An accepted answer is the newest one from then on. */
    fun accept(key: K, ticket: Long): Boolean = synchronized(lock) {
        val floor = maxOf(accepted[key] ?: 0L, changed[key] ?: 0L)
        val newer = ticket > floor
        if (newer) accepted[key] = ticket
        newer
    }
}
