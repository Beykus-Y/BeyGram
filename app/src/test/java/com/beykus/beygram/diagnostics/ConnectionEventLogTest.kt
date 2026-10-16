package com.beykus.beygram.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionEventLogTest {
    @Test fun keepsOnlyNewestEventsInOrder() {
        val log = ConnectionEventLog(capacity = 3, clock = { 0L })
        (1..5).forEach { log.record("event $it") }
        val events = log.snapshot()
        assertEquals(3, events.size)
        assertTrue(events[0].endsWith(" event 3"))
        assertTrue(events[2].endsWith(" event 5"))
    }

    @Test fun prefixesEachEventWithLocalTimestamp() {
        val log = ConnectionEventLog(clock = { 1_700_000_000_000L })
        log.record("TDLib соединение: ConnectionStateReady")
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} TDLib соединение: ConnectionStateReady$""")
            .matches(log.snapshot().single()))
    }
}
