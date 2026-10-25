package com.beykus.beygram.domain.model

/** TDLib accepts at most this many messages in one ForwardMessages or DeleteMessages call. */
const val MAX_MESSAGE_BATCH_SIZE = 100

/**
 * The outcome of one ForwardMessages request. TDLib returns null entries for messages it could not forward, so a
 * partially successful batch must keep the failed IDs selected instead of reporting a clean success.
 */
data class ForwardedMessages(
    val forwardedIds: List<Long>,
    val failedIds: List<Long>
) {
    val isComplete: Boolean get() = failedIds.isEmpty()
}
