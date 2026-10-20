package com.beykus.beygram.data.vpn

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Distinguishes an explicit Auto choice with no installed profile from the persisted
 * auto-selector preference. A later subscription refresh must NEVER take over TDLib's
 * manually selected Direct/SOCKS/MTProto route.
 *
 * Generations also reject queued async activations after switching modes.
 */
internal class AutomaticActivationGuard {
    private val generation = AtomicInteger(0)
    private val awaitingFirstImport = AtomicBoolean(false)

    /** Returns the activation generation only when this explicit choice can start now. */
    fun selectAuto(enabled: Boolean, profileExists: Boolean): Int? {
        val selected = generation.incrementAndGet()
        awaitingFirstImport.set(enabled && !profileExists)
        return selected.takeIf { enabled && profileExists }
    }

    /** A manual Telegram route invalidates all queued Auto work, including first import. */
    fun selectManualRoute() {
        generation.incrementAndGet()
        awaitingFirstImport.set(false)
    }

    /** Only the first successful import after explicit Auto selection can start a listener. */
    fun afterImport(enabled: Boolean, hasConnections: Boolean): Int? {
        if (!enabled || !hasConnections || !awaitingFirstImport.compareAndSet(true, false)) return null
        return generation.get()
    }

    fun permitsActivation(expectedGeneration: Int, enabled: Boolean, profileExists: Boolean): Boolean =
        enabled && profileExists && generation.get() == expectedGeneration
}
