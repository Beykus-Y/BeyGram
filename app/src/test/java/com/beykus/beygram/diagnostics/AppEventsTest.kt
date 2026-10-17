package com.beykus.beygram.diagnostics

import com.beykus.beygram.diagnostics.report.EventKind
import com.beykus.beygram.diagnostics.report.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEventsTest {
    @Test fun writesOneEventToTextAndStructuredLogs() {
        recordAppEvent(EventKind.UI, code = "test_app_event", text = "тестовое событие", severity = Severity.WARNING, attrs = mapOf("duration_s" to "7"))

        assertTrue(ConnectionEventLog.shared.snapshot().last().endsWith(" тестовое событие"))
        val entry = StructuredEventLog.shared.snapshot().last()
        assertEquals(EventKind.UI, entry.kind)
        assertEquals(Severity.WARNING, entry.severity)
        assertEquals("test_app_event", entry.code)
        assertEquals("тестовое событие", entry.message)
        assertEquals(mapOf("duration_s" to "7"), entry.attrs)
    }
}
