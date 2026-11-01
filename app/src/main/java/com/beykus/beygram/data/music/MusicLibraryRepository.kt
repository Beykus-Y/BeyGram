package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Library of the active account, observable through [library].
 *
 * Every change runs under one mutex: the changed library is written to [persistence] first and published only after the
 * write succeeded, so a failed write leaves both memory and disk unchanged. While the stored file of the active account
 * is unreadable, every change is refused, so the unreadable data is never overwritten.
 */
class MusicLibraryRepository(
    private val persistence: MusicLibraryPersistence,
    /** Directory that cover files must live in. Null refuses every cover. */
    private val coverRoot: File? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val _library = MutableStateFlow(MusicLibrary.empty(accountId = "", now = clock()))

    /** The active account's library. Its accountId is empty until [setActiveAccount] has run. */
    val library: StateFlow<MusicLibrary> = _library.asStateFlow()

    /** Account whose library is shown, or null before one is selected. */
    val activeAccountId: String?
        get() = _library.value.accountId.takeIf { it.isNotEmpty() }

    /** False while the stored file of the active account cannot be used. Guarded by [mutex]. */
    private var writable = false

    /** Shows the library of [accountId], loading it from storage. Re-selecting a readable active account does nothing. */
    suspend fun setActiveAccount(accountId: String) {
        if (accountId.isBlank()) return
        mutex.withLock { switchLocked(accountId) }
    }

    private suspend fun switchLocked(accountId: String) {
        if (_library.value.accountId == accountId && writable) return
        writable = false
        _library.value = MusicLibrary.empty(accountId, clock())
        when (val loaded = withContext(ioDispatcher) { persistence.load(accountId) }) {
            is MusicLibraryLoad.Loaded -> {
                _library.value = loaded.library.withFavorites(clock())
                writable = true
            }
            MusicLibraryLoad.Empty -> writable = true
            is MusicLibraryLoad.Unavailable -> writable = false
        }
    }

    /** Deletes everything stored for [accountId]. When that account is active, its library is emptied too. */
    suspend fun clearAccount(accountId: String): MusicOutcome<Unit> = mutex.withLock { clearLocked(accountId) }

    private suspend fun clearLocked(accountId: String): MusicOutcome<Unit> {
        try {
            withContext(ioDispatcher) { persistence.clear(accountId) }
        } catch (failure: IOException) {
            return MusicOutcome.Failed(MusicFailure.StorageUnavailable)
        }
        if (_library.value.accountId == accountId) {
            _library.value = MusicLibrary.empty(accountId, clock())
            writable = true
        }
        return MusicOutcome.Done(Unit)
    }

    // ---- Playlists ---------------------------------------------------------------------------------

    suspend fun createPlaylist(title: String, description: String = ""): MusicOutcome<MusicPlaylist> {
        val playlistId = newId()
        val now = clock()
        return commit { current -> createPlaylistEdit(current, title, description, playlistId, now) }
    }

    suspend fun renamePlaylist(playlistId: String, title: String): MusicOutcome<Unit> {
        val now = clock()
        return commit { current -> renamePlaylistEdit(current, playlistId, title, now) }
    }

    suspend fun updateDescription(playlistId: String, description: String): MusicOutcome<Unit> {
        val now = clock()
        return commit { current -> updateDescriptionEdit(current, playlistId, description, now) }
    }

    /** [coverPath] must be a file inside [coverRoot], or null to remove the cover. */
    suspend fun setCover(playlistId: String, coverPath: String?): MusicOutcome<Unit> {
        if (coverPath != null && !isInsideCoverRoot(coverPath)) return MusicOutcome.Failed(MusicFailure.InvalidCover)
        val now = clock()
        return commit { current -> setCoverEdit(current, playlistId, coverPath, now) }
    }

    /** The favorites playlist cannot be deleted. */
    suspend fun deletePlaylist(playlistId: String): MusicOutcome<Unit> =
        commit { current -> deletePlaylistEdit(current, playlistId) }

    /** The copy is titled "<title> <copySuffix>"; the suffix comes from the caller because it is a localized string. */
    suspend fun duplicatePlaylist(playlistId: String, copySuffix: String): MusicOutcome<MusicPlaylist> {
        val copyId = newId()
        val now = clock()
        return commit { current -> duplicatePlaylistEdit(current, playlistId, copySuffix, copyId, now, newId) }
    }

    // ---- Tracks ------------------------------------------------------------------------------------

    suspend fun addTrack(playlistId: String, track: PlaylistTrack): MusicOutcome<AddTracksResult> =
        addTracks(playlistId, listOf(track))

    suspend fun addTracks(playlistId: String, tracks: List<PlaylistTrack>): MusicOutcome<AddTracksResult> {
        val now = clock()
        return commit { current -> addTracksEdit(current, playlistId, tracks, now, newId) }
    }

    suspend fun removeTrack(playlistId: String, itemId: String): MusicOutcome<Unit> {
        val now = clock()
        return commit { current -> removeTrackEdit(current, playlistId, itemId, now) }
    }

    suspend fun moveTrack(playlistId: String, from: Int, to: Int): MusicOutcome<Unit> {
        val now = clock()
        return commit { current -> moveTrackEdit(current, playlistId, from, to, now) }
    }

    /** Applies [mode] to the stored order once. Nothing is re-sorted later by itself. */
    suspend fun sortPlaylist(playlistId: String, mode: MusicSortMode): MusicOutcome<Unit> {
        val now = clock()
        return commit { current -> sortPlaylistEdit(current, playlistId, mode, now) }
    }

    /** Returns whether the track is a favorite after the call. */
    suspend fun toggleFavorite(track: PlaylistTrack): MusicOutcome<Boolean> {
        val now = clock()
        return commit { current -> toggleFavoriteEdit(current, track, now, newId) }
    }

    fun isFavorite(origin: PlaybackOrigin): Boolean =
        _library.value.favorites?.tracks?.any { it.origin == origin } == true

    // ---- Listen history ----------------------------------------------------------------------------

    /** Records one start of [track] for the active account. */
    suspend fun recordPlay(track: MusicTrackRef, playedAt: Long = clock()): MusicOutcome<Unit> =
        commit { current -> recordPlayEdit(current, track, playedAt) }

    // ---- Internals ---------------------------------------------------------------------------------

    private suspend fun <T> commit(edit: (MusicLibrary) -> MusicEditOutcome<T>): MusicOutcome<T> =
        mutex.withLock { commitLocked(edit) }

    private suspend fun <T> commitLocked(edit: (MusicLibrary) -> MusicEditOutcome<T>): MusicOutcome<T> {
        val current = _library.value
        if (current.accountId.isEmpty()) return MusicOutcome.Failed(MusicFailure.NoActiveAccount)
        if (!writable) return MusicOutcome.Failed(MusicFailure.StorageUnavailable)
        return when (val outcome = edit(current)) {
            is MusicOutcome.Failed -> outcome
            is MusicOutcome.Done -> {
                val next = outcome.value.library
                when {
                    next === current -> MusicOutcome.Done(outcome.value.value)
                    save(next) -> {
                        _library.value = next
                        MusicOutcome.Done(outcome.value.value)
                    }
                    else -> MusicOutcome.Failed(MusicFailure.StorageUnavailable)
                }
            }
        }
    }

    private suspend fun save(library: MusicLibrary): Boolean = try {
        withContext(ioDispatcher) { persistence.save(library) }
        true
    } catch (failure: IOException) {
        false
    }

    private suspend fun isInsideCoverRoot(path: String): Boolean {
        val root = coverRoot ?: return false
        return withContext(ioDispatcher) {
            try {
                val candidate = File(path)
                candidate.isAbsolute && candidate.canonicalPath.startsWith(root.canonicalPath + File.separator)
            } catch (failure: IOException) {
                false
            }
        }
    }
}
