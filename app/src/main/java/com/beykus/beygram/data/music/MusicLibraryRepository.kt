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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** The covers each account's library used when it was last written. They are deleted with the account, even if its library is unreadable. */
    private val coverOwners: MusicCoverOwners = MemoryMusicCoverOwners(),
    /** The accounts whose stored library still owes a clear. Nothing is written to such a library, and it is cleared before the account is selected. */
    private val pendingClears: MusicPendingClears = MemoryMusicPendingClears()
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
        // A library that still owes its clear is not kept, even if it is the one shown: the owed clear runs first.
        if (_library.value.accountId == accountId && writable && !pendingClears.isMarked(accountId)) return
        writable = false
        _isLoaded.value = false
        _storageError.value = false
        _library.value = MusicLibrary(accountId = accountId, playlists = emptyList())
        try {
            // Not cancellable: once the read has started, its result is published as a whole even if the caller is cancelled.
            withContext(NonCancellable + ioDispatcher) {
                if (pendingClears.isMarked(accountId) && clearLocked(accountId) is MusicOutcome.Failed) {
                    // The owed clear failed again: nothing of the stored library is shown or written until a clear succeeds.
                    writable = false
                    _storageError.value = true
                    return@withContext
                }
                publishLoaded(accountId, persistence.load(accountId))
            }
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
                // The record gains the covers of this library. Covers that an earlier clear could not delete stay recorded.
                coverOwners.replace(accountId, coverOwners.covers(accountId) + coverPathsOf(loaded.library))
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

    /**
     * Records that the stored library of [accountId] owes a clear. The mark is stored before this returns, so it survives the
     * process ending before the clear runs. Until a clear succeeds, nothing is written to the account's library, and the next
     * selection of the account clears it first and shows nothing of it before that. Returns false when the mark could not be stored.
     */
    fun markClearOwed(accountId: String): Boolean = pendingClears.mark(accountId)

    /**
     * Runs the clear that [markClearOwed] recorded. Nothing happens once the mark is gone: a selection of the account has cleared
     * it already, and the library loaded since then must not be cleared.
     */
    suspend fun clearOwed(accountId: String): MusicOutcome<Unit> = mutex.withLock {
        if (pendingClears.isMarked(accountId)) clearLocked(accountId) else MusicOutcome.Done(Unit)
    }

    private suspend fun clearLocked(accountId: String): MusicOutcome<Unit> {
        // The covers are read first: once the library file is gone, only the recorded covers are still known.
        val covers = storedCoverPaths(accountId) + withContext(ioDispatcher) { coverOwners.covers(accountId) }
        // Not cancellable from here on, so the stored file, the cover files and the published library change together.
        return withContext<MusicOutcome<Unit>>(NonCancellable) {
            try {
                withContext(ioDispatcher) { persistence.clear(accountId) }
            } catch (failure: IOException) {
                return@withContext MusicOutcome.Failed(MusicFailure.StorageUnavailable)
            }
            val left = deleteCoverFiles(covers)
            if (_library.value.accountId == accountId) {
                _library.value = MusicLibrary.empty(accountId, clock())
                writable = true
                _storageError.value = false
            }
            withContext(ioDispatcher) {
                // Only the covers that stayed are recorded, so the next clear or a later write still finds them.
                if (left.isEmpty()) {
                    coverOwners.forget(accountId)
                    // The clear is complete, so the owed clear is over and the account's library may be read again.
                    pendingClears.unmark(accountId)
                } else {
                    coverOwners.replace(accountId, left)
                }
            }
            if (left.isNotEmpty()) return@withContext MusicOutcome.Failed(MusicFailure.StorageUnavailable)
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
                if (outcome is MusicOutcome.Failed && copiedCover != null) {
                    // A copy that could not be removed is recorded, so that a later clear of the account still finds it.
                    val left = deleteCoverFiles(listOf(copiedCover))
                    if (left.isNotEmpty()) rememberCovers(_library.value.accountId, left)
                }
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

    // ---- Imports -----------------------------------------------------------------------------------

    /**
     * Stores [imported] as a new local playlist with a fresh id. The file's revision and source id are kept, so the copy can be
     * matched with the same file later. The id of [imported] is ignored. Its [MusicPlaylist.coverPath], when set, must be a cover
     * file of the library and is stored in the same write as the playlist; a refused cover refuses the whole import. Callers build
     * it with `BgMusicLibraryMapping`.
     */
    suspend fun importPlaylistCopy(imported: MusicPlaylist): MusicOutcome<MusicPlaylist> {
        if (!isCoverAllowed(imported.coverPath)) return MusicOutcome.Failed(MusicFailure.InvalidCover)
        val playlistId = newId()
        val now = clock()
        return commit { current -> importCopyEdit(current, imported, playlistId, now, newId) }
    }

    /**
     * Replaces the content of the local playlist [localId] with [snapshot], keeping its id and creation time. A snapshot without a
     * cover keeps the current one; a cover must be a cover file of the library, as for [importPlaylistCopy]. Only call this after the
     * user confirmed the replacement; nothing is replaced silently.
     */
    suspend fun replaceImportedPlaylist(localId: String, snapshot: MusicPlaylist): MusicOutcome<Unit> {
        if (!isCoverAllowed(snapshot.coverPath)) return MusicOutcome.Failed(MusicFailure.InvalidCover)
        val now = clock()
        return commit { current -> replaceImportedEdit(current, localId, snapshot, now, newId) }
    }

    // ---- Internals ---------------------------------------------------------------------------------

    private suspend fun <T> commit(edit: (MusicLibrary) -> MusicEditOutcome<T>): MusicOutcome<T> =
        mutex.withLock { commitLocked(edit) }

    private suspend fun <T> commitLocked(edit: (MusicLibrary) -> MusicEditOutcome<T>): MusicOutcome<T> {
        val current = _library.value
        if (current.accountId.isEmpty()) return MusicOutcome.Failed(MusicFailure.NoActiveAccount)
        if (!writable || pendingClears.isMarked(current.accountId)) return MusicOutcome.Failed(MusicFailure.StorageUnavailable)
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

    /**
     * Writes [next]. Only if that succeeded is it published, and the cover files it no longer uses are deleted. The covers of
     * [next] are recorded before the write, so a failed or interrupted write never leaves a cover without a record.
     */
    private suspend fun <T> saveAndPublish(current: MusicLibrary, next: MusicLibrary, value: T): MusicOutcome<T> {
        if (!recordCovers(next)) return MusicOutcome.Failed(MusicFailure.StorageUnavailable)
        if (!save(next)) return MusicOutcome.Failed(MusicFailure.StorageUnavailable)
        _library.value = next
        val unused = coversNoLongerUsed(current, next)
        // A cover that is gone leaves the record. One that stayed is kept, so that a later clear can still delete it.
        forgetCovers(next.accountId, unused.toSet() - deleteCoverFiles(unused))
        return MusicOutcome.Done(value)
    }

    /** Adds the covers of [library] to the record of its account. Covers that an earlier clear left behind stay recorded. */
    private suspend fun recordCovers(library: MusicLibrary): Boolean = rememberCovers(library.accountId, coverPathsOf(library))

    private suspend fun rememberCovers(accountId: String, covers: Set<String>): Boolean =
        withContext(ioDispatcher) { coverOwners.replace(accountId, coverOwners.covers(accountId) + covers) }

    /** Removes the covers that are gone from the record of [accountId]. */
    private suspend fun forgetCovers(accountId: String, gone: Set<String>) {
        if (gone.isEmpty()) return
        withContext(ioDispatcher) { coverOwners.replace(accountId, coverOwners.covers(accountId) - gone) }
    }

    private fun coverPathsOf(library: MusicLibrary): Set<String> =
        library.playlists.mapNotNullTo(HashSet<String>()) { it.coverPath }

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

    /** No cover, or a cover file inside [coverRoot]. */
    private suspend fun isCoverAllowed(path: String?): Boolean = path == null || isInsideCoverRoot(path)

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

    /**
     * Deletes the given cover files that lie inside [coverRoot]. Returns the ones still there afterwards. A file that cannot be
     * deleted is never referenced again, but it stays in the record, so a later clear of its account can try once more.
     */
    private suspend fun deleteCoverFiles(paths: List<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        return withContext(ioDispatcher) {
            // Every file is tried, even after a failure, so one locked file does not keep the others.
            paths.distinct().filter { isInsideCoverRootNow(it) }.filter { path ->
                val file = File(path)
                if (file.exists()) file.delete()
                file.exists()
            }.toSet()
        }
    }
}
