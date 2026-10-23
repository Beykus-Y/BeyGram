package com.beykus.beygram.data.tdlib

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the database lease for the full native TDLib client lifetime.
 *
 * The session must be released ONLY after TDLib emits AuthorizationStateClosed,
 * not on logout, navigation, collector cancellation or on dispatching Close.
 *
 * This deliberately doesn't create secondary clients automatically: the current
 * repository still owns exactly one legacy TDLib client. Subsequent #185 stages
 * will give each account its own repository and scoped state.
 */
internal class TdAccountClientLifecycle(
    private val privateFilesDir: File,
    private val leases: TdAccountDatabaseLeases = TdAccountDatabaseLeases()
) {
    private val layout = TdAccountStorageLayout(privateFilesDir)

    /**
     * Acquire before constructing the native client. The [clientFactory] receives
     * the chosen isolated paths and a terminal callback to invoke from
     * AuthorizationStateClosed, even if processing the update throws.
     *
     * A failed factory cannot leak the lease. Subsequent calls with the same
     * account stay blocked while its TDLib client is alive. Distinct accounts
     * may hold leases simultaneously without sharing a database.
     */
    fun <T : Any> open(
        accountId: String,
        clientFactory: (directories: TdAccountDirectories, onNativeClientClosed: () -> Unit) -> T
    ): T {
        val directories = layout.forAccount(accountId)
        val lease = leases.acquire(directories.database)
        val released = AtomicBoolean(false)
        val afterClosed: () -> Unit = {
            if (released.compareAndSet(false, true)) lease.releaseAfterClientClosed()
        }
        return try {
            clientFactory(directories, afterClosed)
        } catch (failure: Throwable) {
            // The factory failed before returning an owned client: no TDLib owner
            // can retain this lease, so allow a later attempt after the failure.
            afterClosed()
            throw failure
        }
    }
}
