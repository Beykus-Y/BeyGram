package com.beykus.beygram.data.vpn

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
