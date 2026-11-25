package com.beykus.beygram.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRedactorTest {
    @Test fun removesBotTokensAndApiHash() {
        val text = "bot 123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw used, api_hash=0123abcd"
        val redacted = DiagnosticsRedactor.redact(text)
        assertFalse(redacted.contains("AAHdqTcvCH1"))
        assertFalse(redacted.contains("0123abcd"))
        assertTrue(redacted.contains("[скрыт токен]"))
        assertTrue(redacted.contains("api_hash=[скрыто]"))
    }

    @Test fun removesPhoneNumbersAndCodes() {
        val redacted = DiagnosticsRedactor.redact("phone: +79161234567 code=48213 password: hunter2")
        assertFalse(redacted.contains("79161234567"))
        assertFalse(redacted.contains("48213"))
        assertFalse(redacted.contains("hunter2"))
        assertEquals("phone: [скрыто] code=[скрыто] password: [скрыто]", redacted)
    }

    @Test fun removesFormattedPhoneNumbers() {
        val redacted = DiagnosticsRedactor.redact("phone: +7 916 123-45-67 next")
        assertFalse(redacted.contains("916"))
        assertFalse(redacted.contains("45-67"))
        assertEquals("phone: [скрыто] next", redacted)
    }

    @Test fun removesPhoneNumberAsSubmittedFromLoginScreen() {
        // Номер вводится в поле ввода с «+» и без него; строка должна быть отредактирована в обоих случаях.
        val line = "2026-10-16 14:45:01.000 Ошибка: номер +79991234567 отклонён"
        assertEquals(
            "2026-10-16 14:45:01.000 Ошибка: номер [номер скрыт] отклонён",
            DiagnosticsRedactor.redact(line),
        )
    }

    @Test fun removesBotTokenEndingWithDash() {
        val redacted = DiagnosticsRedactor.redact("bot 123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw-")
        assertFalse(redacted.contains("AAHdqTcvCH1"))
    }

    @Test fun removesLongDigitRunsWithoutPlus() {
        assertEquals("id [номер скрыт]", DiagnosticsRedactor.redact("id 79161234567"))
    }

    @Test fun keepsTimestampsAndConnectionStates() {
        val line = "2026-10-16 14:18:31.123 Состояние Telegram: обновляем соединение"
        assertEquals(line, DiagnosticsRedactor.redact(line))
    }

    @Test fun keepsTypedNumericDiagnostics() {
        // Issue: размеры и счётчики байтов скрывались как телефонные номера (#1174).
        val line = "video_progressive_attempt prefix_bytes=1048576 file_size_bytes=24641536 bytes_read=8388608"
        assertEquals(line, DiagnosticsRedactor.redact(line))
    }

    @Test fun keepsTypedNumericDiagnosticsWithSpaces() {
        val line = "heap_used_kb_end: 93051 unlock_ms: 1334 seek_count: 1234567"
        assertEquals(line, DiagnosticsRedactor.redact(line))
    }

    @Test fun keepsTypedNumbersInsideACompoundAttribute() {
        val line = "reads=waits=78,need_more=86 stalls=no_transfer=0,timeout=0 download_offset=0"
        assertEquals(line, DiagnosticsRedactor.redact(line))
    }

    @Test fun anUnknownKeyStillRedactsLongNumbers() {
        // Закрытый список: незнакомый ключ не становится исключением автоматически.
        assertEquals("message_id=[номер скрыт]", DiagnosticsRedactor.redact("message_id=79161234567"))
        assertEquals("foo=[номер скрыт]", DiagnosticsRedactor.redact("foo=79161234567"))
    }

    @Test fun aPhoneNumberUnderANumericKeyIsStillRedacted() {
        // Даже если имя похоже на счётчик, «+» и формат номера остаются номером.
        assertEquals("bytes_read=[номер скрыт]", DiagnosticsRedactor.redact("bytes_read=+7 916 123-45-67"))
    }

    @Test fun secretsWinOverTheNumericAllowlist() {
        val redacted = DiagnosticsRedactor.redact("token=1234567890123 phone: 79161234567")
        assertFalse(redacted.contains("1234567890123"))
        assertFalse(redacted.contains("79161234567"))
    }
}
