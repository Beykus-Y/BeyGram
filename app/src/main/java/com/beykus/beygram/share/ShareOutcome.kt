package com.beykus.beygram.share

import kotlinx.coroutines.TimeoutCancellationException

/** TDLib can accept a request after the local await times out. Never treat this as a confirmed rejection. */
internal fun isShareOutcomeUnknown(error: Throwable?): Boolean {
    if (error is ShareMediaNotHandedOffException) return false
    var cause = error
    val visited = mutableSetOf<Throwable>()
    while (cause != null && visited.add(cause)) {
        if (cause is TimeoutCancellationException || cause is ShareOutcomeUncertainException) return true
        cause = cause.cause
    }
    return false
}

/** Before TDLib has seen any path, the session can safely delete its delivery copies. */
internal class ShareMediaNotHandedOffException(cause: Throwable) : IllegalStateException("Media not handed to TDLib", cause)

internal class ShareMediaHandoff {
    private var handedOff = false
    fun markHandedOff() { handedOff = true }
    fun classify(result: Result<Unit>): Result<Unit> {
        val error = result.exceptionOrNull() ?: return result
        return if (handedOff) result else Result.failure(ShareMediaNotHandedOffException(error))
    }
}
