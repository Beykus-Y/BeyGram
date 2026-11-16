package com.beykus.beygram.share

import org.junit.Assert.*
import org.junit.Test

class ShareBatchLedgerTest {
    @Test fun retriesOnlyUnconfirmedBatchesInExactDestination() {
        val a = ShareDestination(1, 10, "First")
        val b = ShareDestination(1, 20, "Second")
        val ledger = ShareBatchLedger(listOf(a, a.copy(title = "Renamed"), b), 2)
        ledger.confirm(a, 0)
        ledger.confirm(b, 0)
        ledger.confirm(b, 1)
        assertEquals(1, ledger.nextBatch(a))
        assertEquals(listOf(a), ledger.pending())
        assertEquals(1, ledger.completedCount())
        ledger.confirm(a, 1)
        assertTrue(ledger.pending().isEmpty())
    }
    @Test fun partialAlbumIsNotSafeToRetry() {
        assertTrue(isShareOutcomeUnknown(ShareOutcomeUncertainException()))
    }
}
