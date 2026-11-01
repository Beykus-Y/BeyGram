package com.beykus.beygram.data.music

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lowercase hex SHA-256 of the account id. The account id itself never appears in a file name. */
internal fun musicLibraryFileStem(accountId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

/** Backups kept per account. The newest are kept; the oldest are deleted when a new backup is made. */
internal const val MUSIC_LIBRARY_BACKUP_LIMIT = 3

/** The stamp at the end of a backup name, `<file>.<stamp>.bak`. */
private val BACKUP_STAMP = Regex("""\.(\d+)\.bak$""")

/**
 * One JSON file per account inside [directory]: `<sha256(accountId)>.json`.
 *
 * Writes go to a temporary file that is synced and then renamed over the target, so a reader sees either the old or the new
 * library. Nothing is overwritten without a copy first:
 * - a file that cannot be parsed, or that belongs to another account, is renamed to a timestamped `.bak` file;
 * - a file that loaded with skipped entries is copied to a timestamped `.bak` file before its first overwrite.
 * At most [MUSIC_LIBRARY_BACKUP_LIMIT] backups are kept per account; the oldest go first.
 */
class MusicLibraryFileStore(private val directory: File) : MusicLibraryPersistence {
    /** Accounts whose stored file loaded with skipped entries. Their file is backed up before the next save overwrites it. */
    private val backupBeforeSave = ConcurrentHashMap.newKeySet<String>()

    /** Last backup stamp handed out by this process. Stamps only grow, so backup names never repeat or change order. */
    private val lastStamp = AtomicLong(0L)

    fun fileFor(accountId: String): File = File(directory, musicLibraryFileStem(accountId) + ".json")

    override suspend fun load(accountId: String): MusicLibraryLoad = withContext(Dispatchers.IO) {
        loadBlocking(accountId)
    }

    override suspend fun save(library: MusicLibrary) {
        withContext(Dispatchers.IO) { saveBlocking(library) }
    }

    override suspend fun clear(accountId: String) {
        withContext(Dispatchers.IO) { clearBlocking(accountId) }
    }

    private fun loadBlocking(accountId: String): MusicLibraryLoad {
        val file = fileFor(accountId)
        if (!file.exists()) return MusicLibraryLoad.Empty
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (failure: IOException) {
            return MusicLibraryLoad.Unavailable("read failed: ${failure.javaClass.simpleName}")
        }
        return when (val decoded = MusicLibraryJson.decode(text)) {
            is MusicLibraryDecodeResult.Decoded ->
                if (decoded.library.accountId != accountId) {
                    quarantine(file, "account mismatch")
                } else {
                    if (decoded.skippedCount > 0) backupBeforeSave.add(accountId)
                    MusicLibraryLoad.Loaded(decoded.library)
                }
            is MusicLibraryDecodeResult.UnsupportedVersion ->
                MusicLibraryLoad.Unavailable("schema ${decoded.version} is newer than $MUSIC_LIBRARY_SCHEMA_VERSION")
            is MusicLibraryDecodeResult.Corrupt -> quarantine(file, decoded.reason)
        }
    }

    /** Moves an unreadable file aside under a timestamped `.bak` name. If that is impossible, nothing may be written over it. */
    private fun quarantine(file: File, reason: String): MusicLibraryLoad {
        val backup = nextBackupFile(file)
        return if (file.renameTo(backup)) {
            pruneBackups(file)
            MusicLibraryLoad.Empty
        } else {
            MusicLibraryLoad.Unavailable("backup failed after: $reason")
        }
    }

    private fun saveBlocking(library: MusicLibrary) {
        try {
            directory.mkdirs()
            val target = fileFor(library.accountId)
            if (library.accountId in backupBeforeSave) {
                // The first overwrite after a load with skipped entries keeps the original. A failed copy stops the save.
                if (target.exists()) copyToBackup(target)
                backupBeforeSave.remove(library.accountId)
            }
            // The temporary name is fixed. That is safe only because the repository sends every save through its mutex, so
            // two writers never use it at once. A second writer would need unique temporary names.
            val temporary = File(directory, target.name + ".tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(MusicLibraryJson.encode(library).toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                moveReplacing(temporary.toPath(), target.toPath())
            } finally {
                // A failed write must not leave a half-written temporary file behind.
                if (temporary.exists()) temporary.delete()
            }
        } catch (failure: IOException) {
            throw failure
        } catch (failure: Exception) {
            throw IOException("library save failed", failure)
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Copies the stored file to a new backup and prunes the old ones. Throws when the copy fails. */
    private fun copyToBackup(file: File) {
        Files.copy(file.toPath(), nextBackupFile(file).toPath())
        pruneBackups(file)
    }

    /** A backup name that does not exist yet: `<file name>.<stamp>.bak`. */
    private fun nextBackupFile(file: File): File {
        val parent = file.parentFile ?: directory
        var backup = File(parent, "${file.name}.${nextStamp()}.bak")
        while (backup.exists()) {
            backup = File(parent, "${file.name}.${nextStamp()}.bak")
        }
        return backup
    }

    private fun nextStamp(): Long = lastStamp.updateAndGet { previous -> maxOf(System.currentTimeMillis(), previous + 1) }

    /** Deletes the oldest backups of [file] beyond [MUSIC_LIBRARY_BACKUP_LIMIT]. */
    private fun pruneBackups(file: File) {
        val parent = file.parentFile ?: directory
        parent.listFiles().orEmpty()
            .filter { it.name.startsWith(file.name + ".") && it.name.endsWith(".bak") }
            .sortedByDescending { backupStamp(it.name) }
            .drop(MUSIC_LIBRARY_BACKUP_LIMIT)
            .forEach { it.delete() }
    }

    private fun backupStamp(name: String): Long =
        BACKUP_STAMP.find(name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /**
     * Deletes every file that belongs to the account: the library, its temporary file and all backups. Throws if a file
     * stays, so the caller never reports a deletion that did not happen.
     */
    private fun clearBlocking(accountId: String) {
        backupBeforeSave.remove(accountId)
        val stem = musicLibraryFileStem(accountId)
        val survivors = directory.listFiles().orEmpty()
            .filter { it.name.startsWith(stem) }
            // Keep only the files that could not be deleted and still exist.
            .filterNot { it.delete() || !it.exists() }
        if (survivors.isNotEmpty()) throw IOException("${survivors.size} stored library file(s) could not be deleted")
    }
}
