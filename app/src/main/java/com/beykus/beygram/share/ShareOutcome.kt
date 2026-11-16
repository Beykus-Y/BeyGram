package com.beykus.beygram.share

import kotlinx.coroutines.TimeoutCancellationException

/** TDLib can accept a request after the local await times out. Never treat this as a confirmed rejection. */
internal fun isShareOutcomeUnknown(error: Throwable?): Boolean {
    var cause = error
    val visited = mutableSetOf<Throwable>()
    while (cause != null && visited.add(cause)) {
        if (cause is TimeoutCancellationException) return true
        cause = cause.cause
    }
    return false
}
