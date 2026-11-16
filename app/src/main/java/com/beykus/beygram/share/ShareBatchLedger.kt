package com.beykus.beygram.share

internal data class ShareDestination(val chatId: Long, val topicId: Long = 0L, val title: String = "")

/** Counts only confirmed batches per destination; titles never form part of delivery identity. */
internal class ShareBatchLedger(destinations: List<ShareDestination>, val batchCount: Int) {
    val destinations = destinations.distinctBy { it.chatId to it.topicId }
    private val progress = mutableMapOf<Pair<Long, Long>, Int>()
    private fun key(destination: ShareDestination) = destination.chatId to destination.topicId
    fun nextBatch(destination: ShareDestination): Int = progress[key(destination)] ?: 0
    fun confirm(destination: ShareDestination, index: Int) {
        require(index == nextBatch(destination) && index < batchCount)
        progress[key(destination)] = index + 1
    }
    fun pending() = destinations.filter { nextBatch(it) < batchCount }
    fun completedCount() = destinations.size - pending().size
    fun confirmedCount() = destinations.sumOf { nextBatch(it) }
}

internal class ShareOutcomeUncertainException(cause: Throwable? = null) : IllegalStateException("Unconfirmed share delivery", cause)
