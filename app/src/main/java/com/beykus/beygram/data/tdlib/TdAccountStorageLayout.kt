package com.beykus.beygram.data.tdlib

import java.io.File
import java.util.UUID

/**
 * Future multi-account storage contract. This class does not create directories,
 * migrate legacy TDLib, or change the current single-account implementation.
 * In particular, the existing filesDir/tdlib remains untouched.
 */
internal class TdAccountStorageLayout(private val privateFilesDir: File) {
    internal companion object {
        const val LEGACY_ACCOUNT_ID = "legacy"
        private val VALID_ACCOUNT_ID = Regex("[a-zA-Z0-9_-]{1,64}")
    }

    private val root: File get() = privateFilesDir.resolve("tdlib")
    private val secondaryRoot: File get() = privateFilesDir.resolve("tdlib-accounts")

    /** First installed account keeps its exact old paths until a separate migration PR. */
    fun forAccount(accountId: String): TdAccountDirectories {
        require(VALID_ACCOUNT_ID.matches(accountId)) { "Unsafe account directory identifier" }
        val accountRoot = if (accountId == LEGACY_ACCOUNT_ID) root
            else secondaryRoot.resolve(accountId)
        // Canonicalized descendant check also rejects existing symlinks escaping the app root.
        val canonicalPrivate = privateFilesDir.canonicalFile
        val canonicalAccount = accountRoot.canonicalFile
        require(canonicalAccount.toPath().startsWith(canonicalPrivate.toPath())) {
            "Account storage escapes app-private directory"
        }
        val db = canonicalAccount.resolve("database").canonicalFile
        val files = canonicalAccount.resolve("files").canonicalFile
        require(db.toPath().startsWith(canonicalAccount.toPath()) &&
            files.toPath().startsWith(canonicalAccount.toPath()) &&
            db != files) { "Account storage not isolated" }
        return TdAccountDirectories(db, files)
    }

    /** New IDs are opaque and local; they are not Telegram phone numbers or user IDs. */
    fun newSecondaryId(): String = "a-" + UUID.randomUUID().toString()
}

internal data class TdAccountDirectories(val database: File, val files: File)

/**
 * A per-process lease prevents two TDLib clients from opening the same database.
 * This guard must wrap actual TDLib client initialization in a later PR; declaring
 * account directories alone does not enable multi-account support.
 */
internal class TdAccountDatabaseLeases {
    private val opened = mutableSetOf<File>()
    @Synchronized
    fun acquire(database: File): Lease {
        val path = database.canonicalFile
        check(opened.add(path)) { "TDLib database is already in use" }
        return Lease(path)
    }
    inner class Lease internal constructor(private val path: File) : AutoCloseable {
        private var released = false
        @Synchronized override fun close() {
            if (released) return
            synchronized(this@TdAccountDatabaseLeases) {
                if (!released) {
                    opened.remove(path)
                    released = true
                }
            }
        }
    }
}
