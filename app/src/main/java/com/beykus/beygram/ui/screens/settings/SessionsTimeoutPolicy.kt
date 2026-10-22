package com.beykus.beygram.ui.screens.settings

import kotlinx.coroutines.withTimeoutOrNull

internal const val SESSION_LOAD_TIMEOUT_MS = 20_000L
internal const val SESSION_ACTION_TIMEOUT_MS = 25_000L

/** Represents a TDLib response or a bounded wait, without confusing timeout with empty sessions. */
internal sealed interface SessionAwait<out T> {
    data class Completed<T>(val result: Result<T>): SessionAwait<T>
    data object TimedOut: SessionAwait<Nothing>
}

/** Parent cancellation is preserved; timeout is reported rather than treated as success. */
internal suspend fun <T> awaitSessionRequest(
    timeoutMs: Long, request: suspend () -> Result<T>
): SessionAwait<T> {
    require(timeoutMs > 0)
    return withTimeoutOrNull(timeoutMs) { SessionAwait.Completed(request()) } ?: SessionAwait.TimedOut
}
