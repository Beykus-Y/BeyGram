package com.beykus.beygram.data.music

import com.beykus.beygram.playback.PlaybackOrigin
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The extension a copied cover keeps, if it is a plain one. Anything else is dropped when a cover is copied. */
private val COVER_EXTENSION = Regex("[A-Za-z0-9]{1,8}")

/**
 * Library of the active account, observable through [library].
 *
 * Every change runs under one mutex: the changed library is written to [persistence] first and published only after the
 * write succeeded, so a failed write leaves both memory and disk unchanged. While the stored file of the active account
 * is unreadable, every change is refused, so the unreadable data is never overwritten.
 *
 * Selecting an account publishes an empty placeholder and sets [isLoaded] to false until the stored library has been read.
 * Changes issued in the meantime wait for the same mutex, so they apply to the loaded library instead of failing.
 * A load or a write that has started runs to its end even if its caller is cancelled, so memory and disk never disagree.
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
    private val _library = MutableStateFlow(MusicLibrary(accountId = "", playlists = emptyList()))
    private val _isLoaded = MutableStateFlow(false)
    private val _storageError = MutableStateFlow(false)

    /**
     * The active account's library. While [isLoaded] is false it is an empty placeholder with no playlists, which must not be
     * shown as the user's data. Its accountId is empty until [setActiveAccount] has run.
     */
    val library: StateFlow<MusicLibrary> = _library.asStateFlow()

    /** True once the stored library of the active account has been read. False before any selection and while a load runs. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /**
     * True while the stored library of the active account could be found but not read, so every change is refused and the
     * stored data is left alone. Set by a load that returned [MusicLibraryLoad.Unavailable]; cleared by the next readable load.
     */
    val storageError: StateFlow<Boolean> = _storageError.asStateFlow()

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
        _isLoaded.value = false
        _storageError.value = false
        _library.value = MusicLibrary(accountId = accountId, playlists = emptyList())
        try {
            // Not cancellable: once the read has started, its result is published as a whole even if the caller is cancelled.
            withContext(NonCancellable + ioDispatcher) { publishLoaded(accountId, persistence.load(accountId)) }
        } finally {
            _isLoaded.value = true
        }
    }

    /** Publishes what was read for [accountId]. It runs inside the non-cancellable read, so it always completes. */
    private fun publishLoaded(accountId: String, loaded: MusicLibraryLoad) {
        when (loaded) {
            is MusicLibraryLoad.Loaded -> {
                _library.value = loaded.library.withFavorites(clock())
                writable = true
                _storageError.value = false
            }
            MusicLibraryLoad.Empty -> {
                _library.value = MusicLibrary.empty(accountId, clock())
                writable = true
                _storageError.value = false
            }
            is MusicLibraryLoad.Unavailable -> {
                writable = false
                _storageError.value = true
            }
        }
    }

    /**
     * Deletes everything stored for [accountId]: its library, its backups and the cover files its playlists use. When that
     * account is active, its library is emptied too.
     */
    suspend fun clearAccount(accountId: String): MusicOutcome<Unit> = mutex.withLock { clearLocked(accountId) }

    private suspend fun clearLocked(accountId: String): MusicOutcome<Unit> {
        // The cover paths are read first: once the library file is gone they cannot be found again.
        val covers = storedCoverPaths(accountId)
        // Not cancellable from here on, so the stored file, the cover files and the published library change together.
        return withContext<MusicOutcome<Unit>>(NonCancellable) {
            try {
                withContext(ioDispatcher) { persistence.clear(accountId) }
            } catch (failure: IOException) {
                return@withContext MusicOutcome.Failed(MusicFailure.StorageUnavailable)
            }
            deleteCoverFiles(covers)
            if (_library.value.accountId == accountId) {
                _library.value = MusicLibrary.empty(accountId, clock())
                writable = true
                _storageError.value = false
            }
            MusicOutcome.Done(Unit)
        }
    }

    /** Cover paths of the account's stored playlists. The active library is used directly; another account is read from storage. */
    private suspend fun storedCoverPaths(accountId: String): List<String> {
        val active = _library.value
        val library = if (active.accountId == accountId) {
            active
        } else {
            (withContext(ioDispatcher) { persistence.load(accountId) } as? MusicLibraryLoad.Loaded)?.library
        }
        return library?.playlists?.mapNotNull { it.coverPath }.orEmpty()
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

    /**
     * [coverPath] must be a file inside [coverRoot], or null to remove the cover. A cover file that no playlist uses any more
     * is deleted once the change is saved.
     */
    suspend fun setCover(playlistId: String, coverPath: String?): MusicOutcome<Unit> {
        if (coverPath != null && !isInsideCoverRoot(coverPath)) return MusicOutcome.Failed(MusicFailure.InvalidCover)
        val now = clock()
        return commit { current -> setCoverEdit(current, playlistId, coverPath, now) }
    }

    /** The favorites playlist cannot be deleted. The cover file of a deleted playlist is removed once the change is saved. */
    suspend fun deletePlaylist(playlistId: String): MusicOutcome<Unit> =
        commit { current -> deletePlaylistEdit(current, playlistId) }

    /**
     * The copy is titled "<title> <copySuffix>"; the suffix comes from the caller because it is a localized string. A cover is
     * copied to a file of its own, so deleting either playlist later never removes the other one's cover.
     */
    suspend fun duplicatePlaylist(playlistId: String, copySuffix: String): MusicOutcome<MusicPlaylist> {
        val copyId = newId()
        val now = clock()
        return mutex.withLock {
            // Not cancellable: the copied cover file is either kept with its playlist or removed again.
            withContext(NonCancellable) {
                val sourceCover = _library.value.playlist(playlistId)?.coverPath
                val copiedCover = sourceCover?.let { copyCoverLocked(it) }
                val outcome = commitLocked { current ->
                    duplicatePlaylistEdit(current, playlistId, copySuffix, copyId, now, newId, copiedCover)
                }
                if (outcome is MusicOutcome.Failed && copiedCover != null) deleteCoverFiles(listOf(copiedCover))
                outcome
            }
        }
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

    /**
     * Puts [track] in the place of entry [itemId]; the entry keeps its position and its id. The caller picks the track, so
     * nothing is replaced on its own. See [replaceTrackEdit] for the refusals.
     */
    suspend fun replaceTrack(playlistId: String, itemId: String, track: PlaylistTrack): MusicOutcome<Unit> {
        val now = clock()
        return commit { current -> replaceTrackEdit(current, playlistId, itemId, track, now) }
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
                if (next === current) {
                    MusicOutcome.Done(outcome.value.value)
                } else {
                    // Not cancellable once the write has started: the stored file and the published library change together.
                    withContext<MusicOutcome<T>>(NonCancellable) { saveAndPublish(current, next, outcome.value.value) }
                }
            }
        }
    }

    /** Writes [next]. Only if that succeeded is it published, and the cover files it no longer uses are deleted. */
    private suspend fun <T> saveAndPublish(current: MusicLibrary, next: MusicLibrary, value: T): MusicOutcome<T> {
        if (!save(next)) return MusicOutcome.Failed(MusicFailure.StorageUnavailable)
        _library.value = next
        deleteCoverFiles(coversNoLongerUsed(current, next))
        return MusicOutcome.Done(value)
    }

    /** Cover paths that [before] used and [after] no longer uses. Only called once [after] is on disk. */
    private fun coversNoLongerUsed(before: MusicLibrary, after: MusicLibrary): List<String> {
        val kept = after.playlists.mapNotNull { it.coverPath }.toSet()
        return before.playlists.mapNotNull { it.coverPath }.filterNot { it in kept }
    }

    private suspend fun save(library: MusicLibrary): Boolean = try {
        withContext(ioDispatcher) { persistence.save(library) }
        true
    } catch (failure: IOException) {
        false
    }

    private suspend fun isInsideCoverRoot(path: String): Boolean =
        withContext(ioDispatcher) { isInsideCoverRootNow(path) }

    /** The blocking part of the check. It may resolve links on disk, so callers run it on [ioDispatcher]. */
    private fun isInsideCoverRootNow(path: String): Boolean {
        val root = coverRoot ?: return false
        return try {
            val candidate = File(path)
            candidate.isAbsolute && candidate.canonicalPath.startsWith(root.canonicalPath + File.separator)
        } catch (failure: IOException) {
            false
        }
    }

    /**
     * Copies a cover inside [coverRoot] to a new file there, keeping its extension. Returns null when the source is not a
     * cover file or the copy fails.
     */
    private suspend fun copyCoverLocked(source: String): String? {
        val root = coverRoot ?: return null
        if (!isInsideCoverRoot(source)) return null
        val extension = File(source).extension.takeIf { COVER_EXTENSION.matches(it) }?.let { ".$it" }.orEmpty()
        val target = File(root, UUID.randomUUID().toString() + extension)
        return withContext(ioDispatcher) {
            try {
                root.mkdirs()
                File(source).copyTo(target, overwrite = false)
                target.absolutePath
            } catch (failure: IOException) {
                target.delete()
                null
            }
        }
    }

    /** Deletes the given cover files that lie inside [coverRoot]. A file that cannot be deleted is left behind and never referenced. */
    private suspend fun deleteCoverFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        withContext(ioDispatcher) {
            paths.distinct().forEach { path ->
                if (isInsideCoverRootNow(path)) File(path).delete()
            }
        }
    }
}
