package com.beykus.beygram.ui.screens.chat

import androidx.compose.runtime.saveable.listSaver

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

    internal fun snapshot(): List<Long> = buildList {
        forwarded.forEach { (key, ids) -> ids.forEach { id -> addAll(listOf(0L, key.first, key.second, id)) } }
        complete.forEach { key -> addAll(listOf(1L, key.first, key.second, 0L)) }
    }

    companion object {
        internal fun fromSnapshot(entries: List<Long>): ForwardDeliveryLedger = ForwardDeliveryLedger().apply {
            entries.chunked(4).filter { it.size == 4 }.forEach { entry ->
                val target = ForwardTarget(entry[1], "", entry[2])
                if (entry[0] == 0L) recordForwarded(target, listOf(entry[3]))
                else if (entry[0] == 1L) markComplete(target)
            }
        }
        val saver = listSaver<ForwardDeliveryLedger, Long>(save = { it.snapshot() }, restore = { fromSnapshot(it) })
    }
}
