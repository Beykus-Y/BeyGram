package com.beykus.beygram.data.tdlib

import android.content.Context
import java.io.File
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Combines stages A/B1/B2 into one native TDLib client creation boundary.
 *
 * A client is not started here automatically. The caller must supply a client
 * factory with a correctly established shared Mihomo/network policy before
 * introducing account login UI. Never use the selected UI account to route
 * updates: every callback is bound to the account that created that client.
 */
internal class TdAccountNativeClientOwner(
    private val registry: TdAccountRegistry,
    private val lifecycle: TdAccountClientLifecycle,
    private val router: TdAccountUpdateRouter,
    private val configForAccount: (String) -> TdLibConfig
) {
    constructor(context: Context) : this(
        TdAccountRegistry(context.filesDir),
        TdAccountClientLifecycle(context.filesDir),
        TdAccountUpdateRouter(TdAccountRegistry(context.filesDir)),
        { accountId -> TdLibConfig.forAccount(context, accountId) }
    )

    /**
     * The native factory must not return until it owns the Client and its
     * update handler. Close/logout are the factory's responsibility.
     *
     * The database lease and update handler are both retired on TDLib's
     * AuthorizationStateClosed, even if the event consumer throws.
     */
    fun <T : Any> create(
        accountId: String,
        onUpdate: (TdAccountUpdateRouter.TdAccountRoutedUpdate) -> Unit,
        onClosed: () -> Unit = {},
        clientFactory: (TdLibConfig, Client.ResultHandler) -> T
    ): T {
        require(accountId in registry.loadOrCreate().accountIds) {
            "Cannot start an unregistered TDLib account"
        }
        return lifecycle.open(accountId) { paths, closed ->
            val config = configForAccount(accountId)
            // Never allow a bug in the config provider to map a different ID
            // onto this client's leased database or onto an unsafe directory.
            require(File(config.databaseDirectory).canonicalFile == paths.database &&
                File(config.filesDirectory).canonicalFile == paths.files) {
                "TDLib configuration is not isolated to its leased account paths"
            }
            val registration = router.register(accountId) { routed ->
                try {
                    onUpdate(routed)
                } finally {
                    if (routed.update is TdApi.UpdateAuthorizationState &&
                        routed.update.authorizationState is TdApi.AuthorizationStateClosed) {
                        try { closed() } finally { onClosed() }
                    }
                }
            }
            try {
                clientFactory(config, registration.updateHandler)
            } catch (failure: Throwable) {
                registration.onNativeClosed()
                throw failure // lifecycle.open() releases the lease
            }
        }
    }
}
