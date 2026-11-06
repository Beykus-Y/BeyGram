package com.beykus.beygram.data.music

import android.content.SharedPreferences

/** Preferences file that holds the cover records of every account. */
const val MUSIC_COVER_OWNERS_PREFERENCES = "music_cover_owners"

/**
 * The cover files that each account's library used the last time it was written. The record lives apart from the library
 * file, so the covers of an account whose library cannot be read are still known when that account is cleared.
 */
interface MusicCoverOwners {
    /** The covers recorded for [accountId]; empty when there are none. */
    fun covers(accountId: String): Set<String>

    /** Replaces the covers recorded for [accountId]. Returns false when the record could not be stored. */
    fun replace(accountId: String, covers: Set<String>): Boolean

    /** Removes the record of [accountId]. Returns false when the record could not be removed. */
    fun forget(accountId: String): Boolean
}

/** Keeps the records in one preferences file, as one string set per account. Each write is committed before it returns. */
class PreferencesMusicCoverOwners(private val preferences: SharedPreferences) : MusicCoverOwners {
    override fun covers(accountId: String): Set<String> =
        preferences.getStringSet(accountId, null)?.toSet().orEmpty()

    override fun replace(accountId: String, covers: Set<String>): Boolean =
        preferences.edit().putStringSet(accountId, HashSet(covers)).commit()

    override fun forget(accountId: String): Boolean =
        preferences.edit().remove(accountId).commit()
}

/** Records kept in memory, for a repository without storage for them and for tests. */
class MemoryMusicCoverOwners : MusicCoverOwners {
    private val records = HashMap<String, Set<String>>()

    override fun covers(accountId: String): Set<String> = records[accountId].orEmpty()

    override fun replace(accountId: String, covers: Set<String>): Boolean {
        records[accountId] = covers.toSet()
        return true
    }

    override fun forget(accountId: String): Boolean {
        records.remove(accountId)
        return true
    }
}
