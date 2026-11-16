package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.*
import org.junit.Test

class ForwardDeliveryLedgerTest {
    private val chat = ForwardTarget(1, "Chat")
    private val topic = ForwardTarget(1, "Chat", 7, "Topic")

    @Test fun partialBatchRetriesOnlyMissingIds() {
        val ledger = ForwardDeliveryLedger()
        ledger.recordForwarded(chat, listOf(2L, 4L))
        assertEquals(listOf(3L), ledger.pendingIds(chat, listOf(2L, 3L, 4L)))
        assertEquals(listOf(2L, 3L, 4L), ledger.pendingIds(topic, listOf(2L, 3L, 4L)))
    }

    @Test fun failedCommentDoesNotRepeatMessages() {
        val ledger = ForwardDeliveryLedger()
        ledger.recordForwarded(chat, listOf(2L))
        assertTrue(ledger.pendingIds(chat.copy(title = "Renamed"), listOf(2L)).isEmpty())
        assertFalse(ledger.isComplete(chat))
        ledger.markComplete(chat)
        assertTrue(ledger.isComplete(chat.copy(title = "Renamed")))
        assertFalse(ledger.isComplete(topic))
    }
    @Test fun snapshotRestoresPartialAndCompletedDestinations() {
        val ledger = ForwardDeliveryLedger()
        ledger.recordForwarded(chat, listOf(2L))
        ledger.recordForwarded(topic, listOf(2L, 3L))
        ledger.markComplete(topic)
        val restored = ForwardDeliveryLedger.fromSnapshot(ledger.snapshot())
        assertEquals(listOf(3L), restored.pendingIds(chat, listOf(2L, 3L)))
        assertTrue(restored.isComplete(topic))
        assertFalse(restored.isComplete(chat))
    }
}
