package com.beykus.beygram.data.media

/**
 * Persisted references to camera takes whose staging failed. Multiple captures can be retained:
 * taking a new photo must never discard an older recoverable take. Only an explicit retry success
 * or discard removes a path.
 */
internal data class CaptureRetryState(val failedPaths: List<String> = emptyList()) {
    val canRetry: Boolean get() = failedPaths.isNotEmpty()

    /** The capture the UI offers to retry; the rest stay retained. */
    val nextPath: String? get() = failedPaths.firstOrNull()

    fun failed(path: String): CaptureRetryState {
        val clean = path.trim()
        if (clean.isEmpty() || clean in failedPaths) return this
        return copy(failedPaths = failedPaths + clean)
    }

    fun cleared(path: String): CaptureRetryState = copy(failedPaths = failedPaths.filterNot { it == path })

    fun cleared(): CaptureRetryState = Empty

    companion object {
        val Empty = CaptureRetryState()
    }
}
