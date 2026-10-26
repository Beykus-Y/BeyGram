package com.beykus.beygram.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSecurityTest {
    @Test
    fun `empty new password is rejected before a network request`() {
        assertEquals(
            NewPasswordValidation.Empty,
            validateNewPassword("", "")
        )
    }

    @Test
    fun `short new password is rejected`() {
        assertEquals(
            NewPasswordValidation.TooShort,
            validateNewPassword("abc", "abc")
        )
    }

    @Test
    fun `mismatched confirmation is rejected`() {
        assertEquals(
            NewPasswordValidation.Mismatch,
            validateNewPassword("correct horse", "correct hors")
        )
    }

    @Test
    fun `matching password with the minimum length is accepted locally`() {
        assertEquals(
            NewPasswordValidation.Valid,
            validateNewPassword("pass", "pass")
        )
    }

    @Test
    fun `password spaces are significant and preserved`() {
        assertEquals(
            NewPasswordValidation.Valid,
            validateNewPassword("a b c", "a b c")
        )
    }

    @Test
    fun `recovery email is trimmed and checked permissively`() {
        assertEquals("person@example.com", normalizeRecoveryEmail("  person@example.com "))
        assertTrue(isValidRecoveryEmail(" person@example.com "))
        assertTrue(isValidRecoveryEmail("first.last+tag@mail.example.co.uk"))
    }

    @Test
    fun `obviously invalid recovery email is rejected`() {
        assertFalse(isValidRecoveryEmail(""))
        assertFalse(isValidRecoveryEmail("person"))
        assertFalse(isValidRecoveryEmail("person@"))
        assertFalse(isValidRecoveryEmail("@example.com"))
        assertFalse(isValidRecoveryEmail("person@example"))
        assertFalse(isValidRecoveryEmail("person@.example.com"))
        assertFalse(isValidRecoveryEmail("person@example.com."))
        assertFalse(isValidRecoveryEmail("per son@example.com"))
        assertFalse(isValidRecoveryEmail("a@b@example.com"))
    }

    @Test
    fun `deletion confirmation ignores case and surrounding spaces only`() {
        assertTrue(matchesDeletionConfirmation(" delete ", "DELETE"))
        assertTrue(matchesDeletionConfirmation("УДАЛИТЬ", "удалить"))
        assertFalse(matchesDeletionConfirmation("delete account", "DELETE"))
        assertFalse(matchesDeletionConfirmation("delet", "DELETE"))
        assertFalse(matchesDeletionConfirmation("", "DELETE"))
    }

    @Test
    fun `phone input keeps one leading plus and digits only`() {
        assertEquals("+79991234567", normalizePhoneNumberInput(" +7 (999) 123-45-67 "))
        assertEquals("79991234567", normalizePhoneNumberInput("7-999-123-45-67"))
        assertEquals("7999", normalizePhoneNumberInput("7+999"))
        assertEquals("+7", normalizePhoneNumberInput("++7"))
    }

    @Test
    fun `phone input is capped at twenty characters including the plus`() {
        val capped = normalizePhoneNumberInput("+" + "1".repeat(40))
        assertEquals(20, capped.length)
        assertEquals('+', capped.first())
    }

    @Test
    fun `plausible phone numbers have seven to fifteen digits`() {
        assertTrue(isPlausiblePhoneNumber("1234567"))
        assertTrue(isPlausiblePhoneNumber("+79991234567"))
        assertTrue(isPlausiblePhoneNumber("+123456789012345"))
        assertFalse(isPlausiblePhoneNumber(""))
        assertFalse(isPlausiblePhoneNumber("+"))
        assertFalse(isPlausiblePhoneNumber("+123456"))
        assertFalse(isPlausiblePhoneNumber("+1234567890123456"))
    }
}
