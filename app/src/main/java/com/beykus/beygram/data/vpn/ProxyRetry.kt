package com.beykus.beygram.data.vpn

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

import kotlinx.coroutines.delay

/**
 * Runs [attempt] until it returns true, waiting [retryDelayMillis] between tries. Returns false when all [maxAttempts]
 * tries fail. [attempt] gets the number of its try, counted from 1.
 */
internal suspend fun retryUntilSuccess(
    maxAttempts: Int,
    retryDelayMillis: Long,
    attempt: suspend (Int) -> Boolean
): Boolean {
    repeat(maxAttempts) { index ->
        if (attempt(index + 1)) return true
        if (index < maxAttempts - 1) delay(retryDelayMillis)
    }
    return false
}

/**
 * Fail open to the user's retry/disable decision when a stuck STARTING/STOPPING
 * service never transitions. This does not mark Telegram connected or alter
 * the active route.
 */
internal suspend fun awaitSettledProxyState(
    states: Flow<ProxyRuntimeState>, timeoutMillis: Long
): ProxyRuntimeState? {
    require(timeoutMillis > 0L)
    return withTimeoutOrNull(timeoutMillis) {
        states.first {
            it.status != ProxyRuntimeStatus.STARTING && it.status != ProxyRuntimeStatus.STOPPING
        }
    }
}
