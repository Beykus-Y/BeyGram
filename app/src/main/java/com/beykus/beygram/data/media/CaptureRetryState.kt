package com.beykus.beygram.data.media

/**
 * Persisted reference to a camera take whose staging failed. The composer keeps the file and lets
 * the user retry after rotation; only an explicit discard or a successful retry removes it.
 */
internal data class CaptureRetryState(val failedPath: String? = null) {
    val canRetry: Boolean get() = !failedPath.isNullOrBlank()

    fun failed(path: String): CaptureRetryState {
        val clean = path.trim()
        return if (clean.isEmpty()) CaptureRetryState() else CaptureRetryState(clean)
    }

    fun cleared(): CaptureRetryState = CaptureRetryState()

    companion object {
        val Empty = CaptureRetryState()
    }
}
