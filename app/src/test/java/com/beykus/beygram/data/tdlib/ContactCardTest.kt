package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactCardTest {
    @Test
    fun `vcard uses crlf and standard name fields`() {
        val card = ContactCard.vCard("Alice", "Smith", "+15551234567")

        assertEquals(
            "BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "N:Smith;Alice;;;\r\n" +
                "FN:Alice Smith\r\n" +
                "TEL;TYPE=CELL:+15551234567\r\n" +
                "END:VCARD\r\n",
            card
        )
    }

    @Test
    fun `vcard omits an empty phone and writes an empty family name`() {
        val card = ContactCard.vCard("Alice", "", "")

        assertTrue(card.contains("N:;Alice;;;\r\n"))
        assertTrue(card.contains("FN:Alice\r\n"))
        assertFalse(card.contains("TEL"))
    }

    @Test
    fun `vcard escapes text separators and line breaks`() {
        val card = ContactCard.vCard("A;lice,", "Smi\\th\nJr", "+1 555-0000")

        assertTrue(card.contains("N:Smi\\\\th\\nJr;A\\;lice\\,;;;\r\n"))
        assertTrue(card.contains("TEL;TYPE=CELL:+1 555-0000\r\n"))
    }

    @Test
    fun `phone normalization keeps a single international prefix`() {
        assertEquals("+79990000000", ContactCard.normalizePhone(" +7 (999) 000-00-00 "))
        assertEquals("89990000000", ContactCard.normalizePhone("8 999 000-00-00"))
        assertEquals("", ContactCard.normalizePhone("no digits here"))
    }

    @Test
    fun `phone validation allows hidden numbers only when requested`() {
        assertTrue(ContactCard.isValidPhone("+1234567", allowEmpty = false))
        assertFalse(ContactCard.isValidPhone("123456", allowEmpty = false))
        assertTrue(ContactCard.isValidPhone("", allowEmpty = true))
        assertFalse(ContactCard.isValidPhone("", allowEmpty = false))
    }
}
