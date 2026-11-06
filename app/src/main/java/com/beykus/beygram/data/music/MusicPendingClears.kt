package com.beykus.beygram.data.music

import android.content.SharedPreferences

/** Preferences file that holds the accounts whose stored library still owes a clear. */
const val MUSIC_PENDING_CLEARS_PREFERENCES = "music_pending_clears"

/**
 * The accounts whose stored library must be cleared before anyone sees it. A clear that failed, or one that never ran because
 * the process ended first, leaves its account here until a clear succeeds.
 */
interface MusicPendingClears {
    fun isMarked(accountId: String): Boolean

    /** Returns false when the mark could not be stored. */
    fun mark(accountId: String): Boolean

    /** Returns false when the mark could not be removed. */
    fun unmark(accountId: String): Boolean
}

/**
 * Keeps the marks in one preferences file. Each write is committed before it returns, so a mark survives the process ending.
 * A change reads the set, adds or removes one account and writes it back, so the changes are serialized.
 */
class PreferencesMusicPendingClears(private val preferences: SharedPreferences) : MusicPendingClears {
    private val lock = Any()

    override fun isMarked(accountId: String): Boolean = synchronized(lock) {
        preferences.getStringSet(KEY, null)?.contains(accountId) == true
    }

    override fun mark(accountId: String): Boolean = synchronized(lock) {
        val accounts = HashSet(preferences.getStringSet(KEY, null).orEmpty())
        accounts.add(accountId)
        preferences.edit().putStringSet(KEY, accounts).commit()
    }

    override fun unmark(accountId: String): Boolean = synchronized(lock) {
        val accounts = HashSet(preferences.getStringSet(KEY, null).orEmpty())
        accounts.remove(accountId)
        preferences.edit().putStringSet(KEY, accounts).commit()
    }

    private companion object {
        const val KEY = "accounts"
    }
}

/** Marks kept in memory, for a repository without storage for them and for tests. */
class MemoryMusicPendingClears : MusicPendingClears {
    private val accounts = HashSet<String>()

    @Synchronized
    override fun isMarked(accountId: String): Boolean = accountId in accounts

    @Synchronized
    override fun mark(accountId: String): Boolean {
        accounts.add(accountId)
        return true
    }

    @Synchronized
    override fun unmark(accountId: String): Boolean {
        accounts.remove(accountId)
        return true
    }
}
