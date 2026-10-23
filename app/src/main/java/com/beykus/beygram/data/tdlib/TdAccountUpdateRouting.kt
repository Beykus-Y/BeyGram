package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** A Telegram chat ID is only meaningful inside one TDLib account. */
internal data class TdAccountChatKey(val accountId: String, val chatId: Long)

/** Telegram message IDs (and file IDs) are not globally unique across accounts. */
internal data class TdAccountMessageKey(val accountId: String, val chatId: Long, val messageId: Long)
internal data class TdAccountFileKey(val accountId: String, val fileId: Int)

/**
 * B2 routing contract for future independent TDLib client owners.
 *
 * Routes updates using the account ID that was fixed when the native client
 * was CREATED, never using the selected UI account. Each registration has a
 * monotonic generation, so callbacks from a closed/replaced client cannot
 * mutate the replacement account's state.
 *
 * This is NOT the multi-client UI or notification handler: the native client's
 * handler must be registered here and must terminate with [Registration.onNativeClosed].
 */
internal class TdAccountUpdateRouter(private val registry: TdAccountRegistry) {
    private data class Listener(
        val generation: Long,
        val onUpdate: (TdAccountRoutedUpdate) -> Unit
    )
    private val monitor = Any()
    private val live = mutableMapOf<String, Listener>()
    private var nextGeneration = 0L

    /** Immutable source account, unlike mutable UI activeAccountId. */
    internal data class TdAccountRoutedUpdate(
        val accountId: String,
        val generation: Long,
        val update: TdApi.Object
    )

    inner class Registration internal constructor(
        val accountId: String,
        val generation: Long,
        private val onAfterClosed: () -> Unit
    ) {
        /** Install as this TDLib client's callback. Never route through a global active account. */
        val updateHandler = Client.ResultHandler { update ->
            try {
                deliver(accountId, generation, update)
            } finally {
                if (update is TdApi.UpdateAuthorizationState &&
                    update.authorizationState is TdApi.AuthorizationStateClosed) {
                    onNativeClosed()
                }
            }
        }

        /** Called only after TDLib AuthorizationStateClosed. Idempotent and generation-scoped. */
        fun onNativeClosed() {
            val owned = synchronized(monitor) {
                if (live[accountId]?.generation == generation) {
                    live.remove(accountId)
                    true
                } else false
            }
            // Native owner has already released the database lease during
            // delivery; now the generation-scoped router is fully unregistered.
            if (owned) onAfterClosed()
        }
    }

    /** Fails closed for unknown IDs; never creates DBs or starts a TDLib client itself. */
    fun register(accountId: String, onUpdate: (TdAccountRoutedUpdate) -> Unit): Registration =
        register(accountId, {}, onUpdate)

    fun register(accountId: String, onAfterClosed: () -> Unit, onUpdate: (TdAccountRoutedUpdate) -> Unit): Registration =
        synchronized(monitor) {
            require(accountId in registry.loadOrCreate().accountIds) {
                "Cannot route events for an unregistered TDLib account"
            }
            check(accountId !in live) { "TDLib update listener is already registered for this account" }
            val generation = ++nextGeneration
            live[accountId] = Listener(generation, onUpdate)
            Registration(accountId, generation, onAfterClosed)
        }

    private fun deliver(accountId: String, generation: Long, update: TdApi.Object) {
        // Serialize close vs delivery: after onNativeClosed returns no old event
        // can reach a newly attached owner. The callback must be cheap/nonblocking.
        synchronized(monitor) {
            val listener = live[accountId]
            if (listener?.generation == generation) {
                listener.onUpdate(TdAccountRoutedUpdate(accountId, generation, update))
            }
        }
    }

    fun registeredSessions(): Set<String> = synchronized(monitor) { live.keys.toSet() }
}
