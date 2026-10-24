package com.beykus.beygram.data.tdlib

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption

/**
 * Durable *metadata* for future multi-account TDLib. No TDLib clients or database files
 * are opened, moved, deleted, or recreated by this registry.
 *
 * The pre-existing installation remains account "legacy" with its original
 * filesDir/tdlib/database and filesDir/tdlib/files paths (zero-copy migration).
 */
internal data class TdAccountRegistryState(
    val accountIds: List<String>,
    val activeAccountId: String
)

/** The user reached BeyGram's bounded number of registered Telegram accounts. */
internal class TdAccountLimitException :
    IllegalStateException("Too many registered TDLib accounts")

internal class TdAccountRegistry(private val privateFilesDir: File) {
    companion object {
        private const val FORMAT = "beygram-accounts-v1"
        private const val REGISTRY_NAME = ".registry-v1"
        private const val MAX_ACCOUNT_IDS = 10
        /** Avoid races between distinct registry objects in the same Android process. */
        private val mutationLock = Any()
    }

    private val layout = TdAccountStorageLayout(privateFilesDir)
    private val root = privateFilesDir.resolve("tdlib-accounts")
    private val registry = root.resolve(REGISTRY_NAME)
    private val pending = root.resolve("$REGISTRY_NAME.pending")

    /**
     * Idempotent migration of *metadata only*. No copy/rename of TDLib's existing
     * SQLite database: the first account is pinned to the historical paths.
     *
     * A torn pending write is ignored: only the atomically renamed registry is
     * authoritative. A malformed committed file is an error, NOT permission to
     * overwrite registered secondary accounts with a fresh default.
     */
    fun loadOrCreate(): TdAccountRegistryState = synchronized(mutationLock) {
        verifyPrivateRoot()
        if (registry.exists()) return@synchronized readCommitted()
        val initial = TdAccountRegistryState(listOf(TdAccountStorageLayout.LEGACY_ACCOUNT_ID),
            TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        persist(initial)
        initial
    }

    /** Reserve a new opaque account ID; does not start TDLib or create its database. */
    fun prepareSecondaryAccount(): Pair<TdAccountRegistryState, String> = synchronized(mutationLock) {
        val current = loadOrCreate()
        if (current.accountIds.size >= MAX_ACCOUNT_IDS) throw TdAccountLimitException()
        val id = generateSequence { layout.newSecondaryId() }
            .first { it !in current.accountIds }
        // Validate the chosen storage before publishing the new ID to disk.
        layout.forAccount(id)
        val next = current.copy(accountIds = current.accountIds + id)
        persist(next)
        next to id
    }

    /**
     * Only metadata is changed. The active runtime remains the legacy client
     * until a separate PR wires account-scoped repository/client switching.
     */
    fun selectAccount(accountId: String): TdAccountRegistryState = synchronized(mutationLock) {
        val current = loadOrCreate()
        require(accountId in current.accountIds) { "Unregistered TDLib account" }
        if (current.activeAccountId == accountId) return@synchronized current
        val next = current.copy(activeAccountId = accountId)
        persist(next)
        next
    }

    /** Remove only a secondary slot after its native TDLib session has CLOSED.
     * Never delete filesystem data until the registry no longer points at it. */
    fun unregisterSecondaryAccount(accountId: String): TdAccountRegistryState = synchronized(mutationLock) {
        require(accountId != TdAccountStorageLayout.LEGACY_ACCOUNT_ID)
        val current = loadOrCreate()
        require(accountId in current.accountIds) { "Account is not registered" }
        val next = current.copy(
            accountIds = current.accountIds - accountId,
            activeAccountId = if (current.activeAccountId == accountId)
                TdAccountStorageLayout.LEGACY_ACCOUNT_ID else current.activeAccountId
        )
        persist(next)
        next
    }

    private fun verifyPrivateRoot() {
        val canonicalPrivate = privateFilesDir.canonicalFile
        val expected = canonicalPrivate.resolve("tdlib-accounts")
        require(root.canonicalFile == expected) { "Registry directory redirects outside app storage" }
        require(registry.canonicalFile == expected.resolve(REGISTRY_NAME) &&
            pending.canonicalFile == expected.resolve("$REGISTRY_NAME.pending")) {
            "Registry file redirects outside app storage"
        }
        // Check the link itself, including a dangling symlink whose canonicalFile
        // otherwise looks like an ordinary path. All paths belong to app-private storage.
        require(!Files.isSymbolicLink(root.toPath()) &&
            !Files.isSymbolicLink(registry.toPath()) &&
            !Files.isSymbolicLink(pending.toPath())) { "Symlinked TDLib account registry path" }
        if (!root.exists() && !root.mkdirs()) throw IOException("Cannot prepare TDLib account metadata")
        if (!root.isDirectory) throw IOException("TDLib account metadata root is not a directory")
    }

    private fun readCommitted(): TdAccountRegistryState {
        val lines = registry.readText(Charsets.UTF_8).trimEnd('\n').split('\n')
        require(lines.size == 3 && lines[0] == FORMAT &&
            lines[1].startsWith("active=") && lines[2].startsWith("accounts=")) {
            "Invalid TDLib account registry format"
        }
        val state = TdAccountRegistryState(
            accountIds = lines[2].removePrefix("accounts=").split(','),
            activeAccountId = lines[1].removePrefix("active=")
        )
        validate(state)
        return state
    }

    private fun validate(state: TdAccountRegistryState) {
        require(state.accountIds.isNotEmpty() && state.accountIds.size <= MAX_ACCOUNT_IDS &&
            state.accountIds.first() == TdAccountStorageLayout.LEGACY_ACCOUNT_ID &&
            state.accountIds.distinct().size == state.accountIds.size &&
            state.activeAccountId in state.accountIds) { "Invalid TDLib account registry state" }
        state.accountIds.forEach { layout.forAccount(it) }
    }

    private fun persist(state: TdAccountRegistryState) {
        verifyPrivateRoot()
        validate(state)
        val contents = "$FORMAT\nactive=" + state.activeAccountId +
            "\naccounts=" + state.accountIds.joinToString(",") + "\n"
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(contents.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Android minSdk 29: Files.move(ATOMIC_MOVE) is available. Fail closed
            // if unsupported rather than writing partial metadata over the live file.
            Files.move(pending.toPath(), registry.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // FileDescriptor.sync() above only protects the pending file contents.
            // The directory entry installed by rename must also be durable before
            // considering this account list/selection committed.
            FileChannel.open(root.toPath(), StandardOpenOption.READ).use { directory ->
                directory.force(true)
            }
        } finally {
            // A crash may also leave this temp file behind. It is NEVER authoritative.
            pending.delete()
        }
    }
}
