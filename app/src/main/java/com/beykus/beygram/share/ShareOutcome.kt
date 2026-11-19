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
    private var handed = false
    fun markHandedOff() { handed = true }
    /** True once TDLib has been given the request, so a later failure may still have been delivered. */
    fun handedOff(): Boolean = handed
    fun classify(result: Result<Unit>): Result<Unit> {
        val error = result.exceptionOrNull() ?: return result
        return if (handed) result else Result.failure(ShareMediaNotHandedOffException(error))
    }
}
