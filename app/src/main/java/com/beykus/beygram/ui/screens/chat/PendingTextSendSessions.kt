package com.beykus.beygram.ui.screens.chat

import android.content.Context
import com.beykus.beygram.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Pending plaintext is tied to this installation, not to the backup or to a reusable login slot.
 * Old files were in the backup-eligible directory, so their provenance cannot be established.
 * Preserve them privately, but never import them into the active queue automatically.
 */
internal object PendingTextSendFiles {
    private val legacyName = Regex("pending-text-sends-[0-9a-f]{64}\\.json(?:\\.(?:bak|new))?")
    private const val QUARANTINE = "pending-text-legacy"

    fun name(accountId: String): String = "pending-text-sends-" +
        MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".json"

    fun quarantineLegacy(filesDir: File, noBackupDir: File) {
        val candidates = filesDir.listFiles()?.filter { legacyName.matches(it.name) }.orEmpty()
        if (candidates.isEmpty()) return
        val quarantine = File(noBackupDir, QUARANTINE)
        if (!quarantine.isDirectory && !quarantine.mkdirs()) {
            throw IOException("Could not create private pending text quarantine")
        }
        var failed = false
        for (source in candidates) {
            if (!source.isFile) {
                failed = true
                continue
            }
            val destination = File(quarantine, source.name + "." + UUID.randomUUID())
            if (!source.renameTo(destination)) failed = true
        }
        if (failed) throw IOException("Could not quarantine all legacy pending text files")
    }

    fun marker(noBackupDir: File, accountId: String): File =
        File(noBackupDir, name(accountId) + ".clear-owed")

    fun purge(filesDir: File, noBackupDir: File, accountId: String) {
        val base = name(accountId)
        val owed = marker(noBackupDir, accountId)
        if (!owed.exists() && !owed.createNewFile()) throw IOException("Could not mark pending text cleanup")
        for (root in listOf(filesDir, noBackupDir)) {
            for (suffix in listOf("", ".bak", ".new")) {
                deleteFile(File(root, base + suffix))
            }
        }
        val quarantine = File(noBackupDir, QUARANTINE)
        if (quarantine.exists()) {
            val entries = quarantine.listFiles() ?: throw IOException("Could not inspect pending text quarantine")
            entries.filter { it.name == base || it.name.startsWith(base + ".") }.forEach(::deleteFile)
        }
        deleteFile(owed)
    }

    private fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Could not remove pending text snapshot")
    }
}

/** One queue per account session across chat/thread screens and Activity recreation. */
internal object PendingTextSendSessions {
    private class Entry {
        val generation = MutableStateFlow(0L)
        var queue: TextSendQueue? = null
        var signedOut = false
    }

    private val entries = HashMap<String, Entry>()
    private fun entry(accountId: String): Entry = entries.getOrPut(accountId) { Entry() }

    @Synchronized fun generation(accountId: String): StateFlow<Long> = entry(accountId).generation

    @Synchronized fun queue(context: Context, accountId: String): TextSendQueue {
        val entry = entry(accountId)
        entry.queue?.let { return it }
        val app = context.applicationContext
        val queue = if (entry.signedOut) {
            TextSendQueue().also { it.close() }
        } else {
            val store = object : TextSendAttemptStore {
                private val delegate = FileTextSendAttemptStore(File(app.noBackupFilesDir, PendingTextSendFiles.name(accountId)))
                override fun load(): List<TextSendAttempt> {
                    if (PendingTextSendFiles.marker(app.noBackupFilesDir, accountId).exists()) {
                        PendingTextSendFiles.purge(app.filesDir, app.noBackupFilesDir, accountId)
                    }
                    return delegate.load().also { rows ->
                        require(rows.all { it.scope.accountId == accountId }) { "Pending text account mismatch" }
                    }
                }
                override fun save(attempts: List<TextSendAttempt>) = delegate.save(attempts)
            }
            TextSendQueue(store = store, recoveryError = app.getString(R.string.chat_send_recovery_notice))
        }
        entry.queue = queue
        return queue
    }

    /** Called only on confirmed logout/deletion, never on ordinary client close or account switching. */
    @Synchronized fun signedOut(context: Context, accountId: String) {
        val entry = entry(accountId)
        entry.signedOut = true
        entry.queue?.close()
        entry.queue = null
        entry.generation.value++
        val app = context.applicationContext
        PendingTextSendFiles.purge(app.filesDir, app.noBackupFilesDir, accountId)
    }

    @Synchronized fun authorized(context: Context, accountId: String) {
        val entry = entry(accountId)
        if (!entry.signedOut) return
        // Even if logout cleanup previously failed, no fresh session may inherit that snapshot.
        val app = context.applicationContext
        PendingTextSendFiles.purge(app.filesDir, app.noBackupFilesDir, accountId)
        entry.queue?.close()
        entry.queue = null
        entry.signedOut = false
        entry.generation.value++
    }
}
