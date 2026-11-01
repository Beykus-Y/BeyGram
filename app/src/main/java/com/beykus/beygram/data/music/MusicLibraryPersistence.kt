package com.beykus.beygram.data.music

/** Outcome of reading one account's library. */
sealed interface MusicLibraryLoad {
    /** No usable saved library. A corrupt file has already been moved aside as a backup. */
    data object Empty : MusicLibraryLoad

    data class Loaded(val library: MusicLibrary) : MusicLibraryLoad

    /**
     * The stored data exists but cannot be used now: an I/O error, a newer schema, or a corrupt file that could not be
     * moved aside. The stored data is left untouched and the repository refuses to write until the next successful load.
     */
    data class Unavailable(val reason: String) : MusicLibraryLoad
}

/** Storage behind [MusicLibraryRepository]. Tests use an in-memory fake; the app uses [MusicLibraryStore]. */
interface MusicLibraryPersistence {
    suspend fun load(accountId: String): MusicLibraryLoad

    /** Replaces the account's stored library. Throws [java.io.IOException] when the library was not saved. */
    suspend fun save(library: MusicLibrary)

    /** Deletes everything stored for the account, including its backups. */
    suspend fun clear(accountId: String)
}
