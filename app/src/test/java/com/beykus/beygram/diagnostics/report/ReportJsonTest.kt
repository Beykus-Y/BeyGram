package com.beykus.beygram.diagnostics.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportJsonTest {

    private val event = DiagnosticEvent(
        seq = 1,
        atSeconds = 1760000000.0,
        kind = EventKind.NETWORK,
        severity = Severity.WARNING,
        code = "network_route",
        message = "сеть изменилась",
        attrs = mapOf("type" to "wifi", "vpn" to "true")
    )

    private val payload = BugReportPayload(
        reportId = "123e4567-e89b-42d3-a456-426614174000",
        description = "Не приходит сообщение",
        summary = "сводка",
        clientMeta = ReportClientMeta(
            appVersion = "1.0-dev",
            appVersionCode = 1,
            platform = "android",
            osVersion = "Android 15 (SDK 35)",
            deviceModel = "Google Pixel 8",
            locale = "ru_RU",
            vpnActive = true,
            proxyEnabled = false,
            tdlibVersion = null,
            eventCountTotal = 1,
            bufferOverflowed = false
        ),
        events = listOf(event),
        createdAtSeconds = 1760000001.0
    )

    @Test
    fun `encoded payload contains only allowlisted top-level keys`() {
        val json = ReportJson.encode(payload)
        assertTrue(json.contains("\"schema_version\":1"))
        assertTrue(json.contains("\"report_id\""))
        assertTrue(json.contains("\"description\""))
        assertTrue(json.contains("\"summary\""))
        assertTrue(json.contains("\"client_meta\""))
        assertTrue(json.contains("\"events\""))
        assertTrue(json.contains("\"created_at\":1760000001"))
        // Секретных полей в схеме нет и быть не должно.
        assertFalse(json.contains("nonce"))
        assertFalse(json.contains("telegram_user_id"))
        assertFalse(json.contains("authorization"))
    }

    @Test
    fun `client_meta contains only allowlisted fields`() {
        val json = ReportJson.encode(payload)
        val metaKeys = listOf(
            "app_version", "app_version_code", "platform", "os_version", "device_model",
            "locale", "vpn_active", "proxy_enabled", "tdlib_version", "event_count_total", "buffer_overflowed"
        )
        metaKeys.forEach { key ->
            if (key == "tdlib_version") return@forEach // null не кодируется
            assertTrue("missing $key", json.contains("\"$key\""))
        }
        assertFalse(json.contains("application_id"))
        assertFalse(json.contains("build_type"))
    }

    @Test
    fun `encoding is deterministic for identical payload`() {
        assertEquals(ReportJson.encode(payload), ReportJson.encode(payload))
    }

    @Test
    fun `strings are escaped`() {
        val tricky = BugReportPayload(
            reportId = payload.reportId,
            description = "кавычки \" и слэш \\ и перевод\nстроки и таб\t"
        )
        val json = ReportJson.encode(tricky)
        assertTrue(json.contains("кавычки \\\" и слэш \\\\ и перевод\\nстроки и таб\\t"))
        val parsed = ReportJson.parse(json) as Map<*, *>
        assertEquals(tricky.description, parsed["description"])
    }

    @Test
    fun `control characters are escaped as unicode`() {
        val payload = BugReportPayload(reportId = payload.reportId, description = "a${1.toChar()}b")
        val json = ReportJson.encode(payload)
        assertTrue(json.contains("\\u0001"))
    }

    @Test
    fun `accepted response parses`() {
        val json = """
            {"report_id":"123e4567-e89b-42d3-a456-426614174000","status":"queued",
             "accepted_at":1760000000.5,"status_token":"tok","idempotent_replay":false,
             "redactions":{"ip_like":3},"detail":"ок"}
        """.trimIndent()
        val accepted = ReportJson.parseAccepted(ReportJson.parse(json))
        assertEquals("123e4567-e89b-42d3-a456-426614174000", accepted?.reportId)
        assertEquals("queued", accepted?.status)
        assertEquals(1760000000.5, accepted?.acceptedAtSeconds)
        assertEquals("tok", accepted?.statusToken)
        assertEquals(false, accepted?.idempotentReplay)
    }

    @Test
    fun `status response parses`() {
        val json = """
            {"report_id":"123e4567-e89b-42d3-a456-426614174000","status":"delivered",
             "accepted_at":1,"updated_at":2,"delivered_at":3,"attempts":2,"detail":"готово"}
        """.trimIndent()
        val status = ReportJson.parseStatus(ReportJson.parse(json))
        assertEquals("delivered", status?.status)
        assertEquals(3.0, status?.deliveredAtSeconds)
        assertEquals(2, status?.attempts)
    }

    @Test
    fun `error envelope parses`() {
        val json = """{"error":{"code":"rate_limited","message":"много"}}"""
        assertEquals("rate_limited", ReportJson.parseErrorCode(ReportJson.parse(json)))
        assertEquals("много", ReportJson.parseErrorMessage(ReportJson.parse(json)))
    }

    @Test
    fun `unexpected shapes yield null instead of throwing`() {
        assertNull(ReportJson.parseAccepted(ReportJson.parse("{}")))
        assertNull(ReportJson.parseStatus(ReportJson.parse("[]")))
        assertNull(ReportJson.parseErrorCode(ReportJson.parse("\"ok\"")))
        assertNull(ReportJson.parse("not json at all {"))
    }

    @Test
    fun `pretty print keeps content`() {
        val pretty = ReportJson.prettyPrint(ReportJson.encode(payload))
        val reparsed = ReportJson.parse(pretty) as Map<*, *>
        assertEquals(payload.reportId, reparsed["report_id"])
        assertTrue(pretty.contains("\n"))
    }
}
