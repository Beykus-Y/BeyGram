package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

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
    private class Listener(
        val generation: Long,
        val onUpdate: (TdAccountRoutedUpdate) -> Unit
    ) {
        // Keep expensive repository callbacks off TDLib's ResponseReceiver thread.
        // The bounded, single-worker queue preserves per-client delivery order.
        val dispatchLock = Any()
        var accepting = true // guarded by dispatchLock
        val dispatcher = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(4096),
            { runnable -> Thread(runnable, "beygram-tdlib-$generation").apply { isDaemon = true } },
            { runnable, executor ->
                // Never drop a state update: apply backpressure in extreme bursts.
                if (executor.isShutdown) throw RejectedExecutionException("TDLib dispatcher closed")
                executor.queue.put(runnable)
            }
        )
    }
    private val logger = Logger.getLogger(TdAccountUpdateRouter::class.java.name)
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
        fun onNativeClosed(notifyOwner: Boolean = true) {
            val listener = synchronized(monitor) {
                live[accountId]?.takeIf { it.generation == generation }
            } ?: return
            // Queue retirement AFTER every already accepted update. Do not block
            // the shared native callback thread waiting for repository work.
            val drained = if (!notifyOwner) CountDownLatch(1) else null
            synchronized(listener.dispatchLock) {
                if (!listener.accepting) return
                listener.accepting = false
                listener.dispatcher.execute {
                    try {
                        synchronized(monitor) {
                            if (live[accountId] === listener) live.remove(accountId)
                        }
                        if (notifyOwner) onAfterClosed()
                    } catch (error: Throwable) {
                        logger.log(Level.SEVERE, "TDLib native client retirement failed", error)
                    } finally {
                        listener.dispatcher.shutdown()
                        drained?.countDown()
                    }
                }
            }
            // Factory failure is special: its DB lease is released by the
            // caller immediately, so drain any callbacks before returning.
            if (drained != null) check(drained.await(30, TimeUnit.SECONDS)) {
                "Timed out draining failed TDLib client callbacks"
            }
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
        val listener = synchronized(monitor) {
            live[accountId]?.takeIf { it.generation == generation }
        } ?: return
        synchronized(listener.dispatchLock) {
            if (listener.accepting) {
                listener.dispatcher.execute {
                    try {
                        listener.onUpdate(TdAccountRoutedUpdate(accountId, generation, update))
                    } catch (error: Throwable) {
                        logger.log(Level.SEVERE, "TDLib account callback failed: $accountId", error)
                    }
                }
            }
        }
    }

    fun registeredSessions(): Set<String> = synchronized(monitor) { live.keys.toSet() }
}
