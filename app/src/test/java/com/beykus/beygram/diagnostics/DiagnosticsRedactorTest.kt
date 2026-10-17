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
        assertTrue(redacted.contains("[токен скрыт]"))
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
        // Экран входа отправляет номер как "+" и только цифры; такая строка может попасть в журнал событий.
        val line = "2026-10-16 14:45:01.000 вход: номер +79991234567 отправлен"
        assertEquals("2026-10-16 14:45:01.000 вход: номер [номер скрыт] отправлен", DiagnosticsRedactor.redact(line))
    }

    @Test fun removesBotTokenEndingWithDash() {
        val redacted = DiagnosticsRedactor.redact("bot 123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw-")
        assertFalse(redacted.contains("AAHdqTcvCH1"))
    }

    @Test fun removesLongDigitRunsWithoutPlus() {
        assertEquals("id [номер скрыт]", DiagnosticsRedactor.redact("id 79161234567"))
    }

    @Test fun keepsTimestampsAndConnectionStates() {
        val line = "2026-10-16 14:18:31.123 Соединение с Telegram: ожидание сети"
        assertEquals(line, DiagnosticsRedactor.redact(line))
    }
}
