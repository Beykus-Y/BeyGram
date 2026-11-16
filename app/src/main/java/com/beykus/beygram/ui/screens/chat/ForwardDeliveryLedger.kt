package com.beykus.beygram.ui.screens.chat

/** Confirmed deliveries within a single picker session. Unknown transport outcomes are not inferred as success. */
internal class ForwardDeliveryLedger {
    private val forwarded = mutableMapOf<Pair<Long, Long>, MutableSet<Long>>()
    private val complete = mutableSetOf<Pair<Long, Long>>()

    private fun key(target: ForwardTarget) = target.chatId to target.topicId

    fun pendingIds(target: ForwardTarget, ids: List<Long>): List<Long> =
        ids.filterNot { it in forwarded[key(target)].orEmpty() }

    fun recordForwarded(target: ForwardTarget, ids: List<Long>) {
        forwarded.getOrPut(key(target)) { mutableSetOf() }.addAll(ids)
    }

    fun isComplete(target: ForwardTarget): Boolean = key(target) in complete

    fun markComplete(target: ForwardTarget) {
        complete.add(key(target))
    }
}
