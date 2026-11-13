package com.beykus.beygram.data.tdlib

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe per-key request epoch counter used to discard stale history/jump window responses
 * when a newer request or an explicit cancellation happens before TDLib responds.
 */
internal class RequestEpochMap<K : Any> {
    private val epochs = ConcurrentHashMap<K, Long>()

    fun next(key: K): Long = epochs.merge(key, 1L) { current, _ -> current + 1L } ?: 1L

    fun isCurrent(key: K, token: Long): Boolean = epochs[key] == token

    fun invalidate(key: K): Long = next(key)
}
