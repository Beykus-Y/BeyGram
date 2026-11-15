package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.util.Log
import com.beykus.beygram.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    internal const val QUARANTINE = "pending-text-legacy"
    internal const val CORRUPT_QUARANTINE = "pending-text-corrupt"
    private val fileLock = Any()

    fun <T> withFileLock(block: () -> T): T = synchronized(fileLock) { block() }

    fun name(accountId: String): String = "pending-text-sends-" +
        MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".json"

    fun quarantineLegacy(filesDir: File, noBackupDir: File) = synchronized(fileLock) {
        val candidates = filesDir.listFiles()?.filter { legacyName.matches(it.name) }.orEmpty()
        if (candidates.isEmpty()) return@synchronized
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

    fun quarantineCorrupt(baseFile: File, quarantineDir: File) = synchronized(fileLock) {
        val candidates = listOf(baseFile, File(baseFile.path + ".bak"), File(baseFile.path + ".new"))
            .filter { it.exists() }
        if (candidates.isEmpty()) return@synchronized
        if (!quarantineDir.isDirectory && !quarantineDir.mkdirs()) {
            throw IOException("Could not create corrupt pending text quarantine")
        }
        var failed = false
        for (source in candidates) {
            if (!source.isFile) {
                failed = true
                continue
            }
            val destination = File(quarantineDir, source.name + "." + UUID.randomUUID())
            if (!source.renameTo(destination)) failed = true
        }
        if (failed) throw IOException("Could not quarantine corrupt pending text snapshot")
    }

    fun marker(noBackupDir: File, accountId: String): File =
        File(noBackupDir, name(accountId) + ".clear-owed")

    fun purge(filesDir: File, noBackupDir: File, accountId: String) = synchronized(fileLock) {
        val base = name(accountId)
        val owed = marker(noBackupDir, accountId)
        if (!owed.exists() && !owed.createNewFile()) throw IOException("Could not mark pending text cleanup")
        for (root in listOf(filesDir, noBackupDir)) {
            for (suffix in listOf("", ".bak", ".new")) {
                deleteFile(File(root, base + suffix))
            }
        }
        for (folder in listOf(QUARANTINE, CORRUPT_QUARANTINE)) {
            val quarantine = File(noBackupDir, folder)
            if (quarantine.exists()) {
                val entries = quarantine.listFiles() ?: throw IOException("Could not inspect pending text quarantine")
                entries.filter { it.name == base || it.name.startsWith(base + ".") }.forEach(::deleteFile)
            }
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
        var purgeOwed = false
        var diskJob: Job? = null
    }

    private val defaultIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var ioScope: CoroutineScope = defaultIoScope
    @Volatile private var storageDispatcher: CoroutineDispatcher = Dispatchers.IO
    @Volatile private var startupQuarantineJob: Job? = null
    private val entries = HashMap<String, Entry>()
    private fun entry(accountId: String): Entry = entries.getOrPut(accountId) { Entry() }

    /** Moves legacy backup-eligible snapshots off the main thread without racing with session cleanup. */
    fun scheduleStartupQuarantine(context: Context): Job {
        val app = context.applicationContext
        val previous: Job?
        val job: Job
        synchronized(this) {
            previous = startupQuarantineJob
            job = ioScope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                runCatching {
                    PendingTextSendFiles.quarantineLegacy(app.filesDir, app.noBackupFilesDir)
                }.onFailure {
                    Log.w("BeyGramAccounts", "Legacy pending text quarantine incomplete")
                }
            }
            startupQuarantineJob = job
        }
        job.start()
        return job
    }

    @Synchronized fun generation(accountId: String): StateFlow<Long> = entry(accountId).generation

    @Synchronized fun queue(context: Context, accountId: String): TextSendQueue {
        val entry = entry(accountId)
        entry.queue?.let { return it }
        val app = context.applicationContext
        val queue = if (entry.signedOut) {
            TextSendQueue(storageDispatcher = storageDispatcher).also { it.close() }
        } else {
            val quarantineSnapshot = startupQuarantineJob
            val diskJobSnapshot = entry.diskJob
            val store = object : TextSendAttemptStore {
                private val corruptDir = File(app.noBackupFilesDir, PendingTextSendFiles.CORRUPT_QUARANTINE)
                private val delegate = FileTextSendAttemptStore(
                    File(app.noBackupFilesDir, PendingTextSendFiles.name(accountId)),
                    corruptDir
                )
                override suspend fun prepare() {
                    quarantineSnapshot?.join()
                    diskJobSnapshot?.join()
                }
                override fun load(): List<TextSendAttempt> = PendingTextSendFiles.withFileLock {
                    val owed = synchronized(PendingTextSendSessions) { entry.purgeOwed }
                    if (owed || PendingTextSendFiles.marker(app.noBackupFilesDir, accountId).exists()) {
                        PendingTextSendFiles.purge(app.filesDir, app.noBackupFilesDir, accountId)
                        synchronized(PendingTextSendSessions) { entry.purgeOwed = false }
                    }
                    delegate.load().also { rows ->
                        require(rows.all { it.scope.accountId == accountId }) { "Pending text account mismatch" }
                    }
                }
                override fun save(attempts: List<TextSendAttempt>) = PendingTextSendFiles.withFileLock {
                    delegate.save(attempts)
                }
                override fun quarantineCorrupt() = PendingTextSendFiles.withFileLock {
                    delegate.quarantineCorrupt()
                }
            }
            TextSendQueue(
                store = store,
                recoveryError = app.getString(R.string.chat_send_recovery_notice),
                storageDispatcher = storageDispatcher
            )
        }
        entry.queue = queue
        return queue
    }

    private fun schedulePurge(context: Context, accountId: String): Deferred<Result<Unit>> {
        val app = context.applicationContext
        val previousJob: Job?
        val quarantineJob: Job?
        lateinit var purgeDeferred: Deferred<Result<Unit>>
        synchronized(this) {
            val entry = entry(accountId)
            entry.signedOut = true
            entry.purgeOwed = true
            entry.queue?.close()
            entry.queue = null
            entry.generation.value++
            previousJob = entry.diskJob
            quarantineJob = startupQuarantineJob
            purgeDeferred = ioScope.async(start = CoroutineStart.LAZY) {
                quarantineJob?.join()
                previousJob?.join()
                runCatching {
                    PendingTextSendFiles.purge(app.filesDir, app.noBackupFilesDir, accountId)
                    synchronized(PendingTextSendSessions) {
                        if (entry.diskJob === purgeDeferred) {
                            entry.purgeOwed = false
                        }
                    }
                }
            }
            entry.diskJob = purgeDeferred
        }
        purgeDeferred.start()
        return purgeDeferred
    }

    /** Called on confirmed logout, remote session revocation, or account deletion without blocking callbacks. */
    fun signedOut(context: Context, accountId: String) {
        val deferred = schedulePurge(context, accountId)
        ioScope.launch {
            deferred.await().onFailure {
                Log.w("BeyGramAccounts", "Pending text session cleanup incomplete")
            }
        }
    }

    /** Suspends until the account's pending text files are purged, rethrowing if cleanup fails. */
    suspend fun signedOutAndPurge(context: Context, accountId: String) {
        schedulePurge(context, accountId).await().getOrThrow()
    }

    @Synchronized fun authorized(context: Context, accountId: String) {
        val entry = entry(accountId)
        if (!entry.signedOut) return
        // Even if logout cleanup is still running or previously failed, the next queue waits and purges before load.
        entry.queue?.close()
        entry.queue = null
        entry.signedOut = false
        entry.generation.value++
    }

    @Synchronized internal fun installQueueForTests(accountId: String, queue: TextSendQueue) {
        val entry = entry(accountId)
        entry.queue?.close()
        entry.queue = queue
        entry.signedOut = false
        entry.generation.value++
    }

    internal fun resetForTests(dispatcher: CoroutineDispatcher? = null) {
        val previousScope: CoroutineScope
        synchronized(this) {
            entries.values.forEach { it.queue?.close() }
            entries.clear()
            startupQuarantineJob = null
            previousScope = ioScope
            if (dispatcher != null) {
                storageDispatcher = dispatcher
                ioScope = CoroutineScope(SupervisorJob() + dispatcher)
            } else {
                storageDispatcher = Dispatchers.IO
                ioScope = defaultIoScope
            }
        }
        if (previousScope !== defaultIoScope) previousScope.cancel()
    }
}
