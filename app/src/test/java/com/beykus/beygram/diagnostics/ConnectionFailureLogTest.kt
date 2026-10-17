package com.beykus.beygram.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFailureLogTest {
    @Test fun keepsOnlyNewestFailuresNewestFirst() {
        var now = 0L
        val log = ConnectionFailureLog(capacity = 2, clock = { now })
        now = 1L; log.record(ConnectionFailure.Timeout)
        now = 2L; log.record(ConnectionFailure.Proxy)
        now = 3L; log.record(ConnectionFailure.FloodWait)
        assertEquals(
            listOf(
                ConnectionFailureEntry(atMillis = 3L, failure = ConnectionFailure.FloodWait),
                ConnectionFailureEntry(atMillis = 2L, failure = ConnectionFailure.Proxy)
            ),
            log.snapshot()
        )
    }

    @Test fun startsEmpty() {
        assertEquals(emptyList<ConnectionFailureEntry>(), ConnectionFailureLog(clock = { 0L }).snapshot())
    }
}
