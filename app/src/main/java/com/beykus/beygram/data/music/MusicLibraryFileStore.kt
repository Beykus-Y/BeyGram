package com.beykus.beygram.data.music

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lowercase hex SHA-256 of the account id. The account id itself never appears in a file name. */
internal fun musicLibraryFileStem(accountId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

/**
 * One JSON file per account inside [directory]: `<sha256(accountId)>.json`. Writes go to a temporary file that is synced
 * and then renamed over the target, so a reader sees either the old or the new library. A file that cannot be parsed, or
 * that belongs to another account, is renamed to a `.bak` file instead of being overwritten.
 */
class MusicLibraryFileStore(private val directory: File) : MusicLibraryPersistence {

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
                if (decoded.library.accountId == accountId) {
                    MusicLibraryLoad.Loaded(decoded.library)
                } else {
                    quarantine(file, "account mismatch")
                }
            is MusicLibraryDecodeResult.UnsupportedVersion ->
                MusicLibraryLoad.Unavailable(
                    "schema ${decoded.version} is newer than $MUSIC_LIBRARY_SCHEMA_VERSION"
                )
            is MusicLibraryDecodeResult.Corrupt -> quarantine(file, decoded.reason)
        }
    }

    /** Moves an unreadable file aside under a `.bak` name. If that is impossible, nothing may be written over it. */
    private fun quarantine(file: File, reason: String): MusicLibraryLoad {
        val parent = file.parentFile ?: directory
        var backup = File(parent, file.name + ".bak")
        var suffix = 1
        while (backup.exists()) {
            backup = File(parent, file.name + "." + suffix + ".bak")
            suffix++
        }
        return if (file.renameTo(backup)) {
            MusicLibraryLoad.Empty
        } else {
            MusicLibraryLoad.Unavailable("backup failed after: $reason")
        }
    }

    private fun saveBlocking(library: MusicLibrary) {
        try {
            directory.mkdirs()
            val target = fileFor(library.accountId)
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

    private fun clearBlocking(accountId: String) {
        val stem = musicLibraryFileStem(accountId)
        directory.listFiles()
            ?.filter { it.name.startsWith(stem) }
            ?.forEach { it.delete() }
    }
}
