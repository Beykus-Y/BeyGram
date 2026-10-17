package com.beykus.beygram.diagnostics.report

import com.beykus.beygram.diagnostics.StructuredEventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSnapshotTest {

    private var timeMillis = 1_760_000_000_000L
    private val log = StructuredEventLog(capacity = 500, clock = { timeMillis })

    @Test
    fun `events are sanitized and limited per schema`() {
        log.record(
            kind = EventKind.NETWORK,
            severity = Severity.WARNING,
            code = "network_route",
            message = "секрет " + "x".repeat(600),
            attrs = (1..30).associate { "attr$it" to "v$it" }
        )
        val events = ReportSnapshot.collectEvents(log)
        assertEquals(1, events.size)
        val single = events.single()
        assertTrue(single.message.length <= ReportSnapshot.MAX_EVENT_MESSAGE_CHARS)
        assertTrue(single.message.contains("скрыто") || single.message.length == 500)
        assertEquals(ReportSnapshot.MAX_EVENT_ATTRS, single.attrs.size)
        single.attrs.values.forEach { value -> assertTrue(value.length <= ReportSnapshot.MAX_EVENT_ATTR_VALUE_CHARS) }
        single.attrs.keys.forEach { key -> assertTrue(key.length <= ReportSnapshot.MAX_EVENT_CODE_CHARS) }
    }

    @Test
    fun `sensitive attribute values do not reach events`() {
        log.record(
            kind = EventKind.VPN,
            code = "vpn_subscription",
            message = "download",
            attrs = mapOf("proxy" to "socks5://user:pass@host:1080", "status" to "200")
        )
        val single = ReportSnapshot.collectEvents(log).single()
        val json = ReportJson.encode(BugReportPayload(reportId = "123e4567-e89b-42d3-a456-426614174000", description = "d", events = listOf(single)))
        assertFalse(json.contains("pass@host"))
        assertFalse(json.contains("socks5://"))
        assertTrue(json.contains("200"))
    }

    @Test
    fun `summary stays within limits and contains event lines`() {
        repeat(10) { index ->
            log.record(kind = EventKind.TDLIB, code = "tdlib_connection", message = "событие $index", attrs = mapOf("state" to "ready"))
            timeMillis += 1000
        }
        val events = ReportSnapshot.collectEvents(log)
        val summary = ReportSnapshot.buildSummary(events)
        assertTrue(summary.contains("tdlib_connection"))
        assertTrue(summary.length <= ReportSnapshot.MAX_SUMMARY_CHARS)
    }

    @Test
    fun `fitToBudget trims events first and never the description`() {
        val events = (1..2000).map { index ->
            DiagnosticEvent(
                seq = index.toLong(),
                atSeconds = index.toDouble(),
                kind = EventKind.OTHER,
                message = "наполнение ".repeat(20) + index
            )
        }
        val description = "описание проблемы"
        val payload = BugReportPayload(
            reportId = "123e4567-e89b-42d3-a456-426614174000",
            description = description,
            summary = null,
            events = events
        )
        val smallBudget = 16 * 1024
        val fitted = ReportSnapshot.fitToBudget(payload, maxBytes = smallBudget)!!
        assertTrue(ReportSnapshot.encodedSize(fitted) <= smallBudget)
        assertEquals(description, fitted.description)
        assertTrue(fitted.events.size < events.size)
        // Старые события отбрасываются первыми: остаются последние.
        assertEquals(events.last().seq, fitted.events.last().seq)
    }

    @Test
    fun `fitToBudget returns null when even the minimum does not fit`() {
        val payload = BugReportPayload(
            reportId = "123e4567-e89b-42d3-a456-426614174000",
            description = "в".repeat(2000),
            events = emptyList()
        )
        assertNull(ReportSnapshot.fitToBudget(payload, maxBytes = 64))
    }

    @Test
    fun `encoded size is measured in utf-8 bytes`() {
        val payload = BugReportPayload(
            reportId = "123e4567-e89b-42d3-a456-426614174000",
            description = "ё".repeat(100) // 2 байта на символ
        )
        val expected = ReportJson.encode(payload).toByteArray(Charsets.UTF_8).size
        assertEquals(expected, ReportSnapshot.encodedSize(payload))
        assertTrue("многобайтовые символы считаются байтами", expected > 100)
    }

    @Test
    fun `buffer overflow is reported once capacity is exceeded`() {
        val small = StructuredEventLog(capacity = 2, clock = { timeMillis })
        assertFalse(small.hasOverflowed())
        repeat(3) { small.record(kind = EventKind.OTHER, code = "test") }
        assertTrue(small.hasOverflowed())
        assertEquals(2, small.snapshot().size)
        assertEquals(1L, small.snapshot().first().seq)
    }
}
