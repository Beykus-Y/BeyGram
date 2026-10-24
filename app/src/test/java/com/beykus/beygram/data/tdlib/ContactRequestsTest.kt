package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRequestsTest {
    @Test
    fun `add contact edits by user id and never shares my phone number`() {
        val request = addContactRequest(42L, "Ann", "Lee", "+15551234567")

        assertEquals(42L, request.userId)
        assertEquals("+15551234567", request.contact.phoneNumber)
        assertEquals("Ann", request.contact.firstName)
        assertEquals("Lee", request.contact.lastName)
        assertFalse(request.sharePhoneNumber)
        assertTrue(request.contact.note.text.isEmpty())
        assertEquals(0, request.contact.note.entities.size)
    }

    @Test
    fun `remove contact targets exactly one user id`() {
        val request = removeContactRequest(42L)

        assertEquals(1, request.userIds.size)
        assertEquals(42L, request.userIds.single())
    }

    @Test
    fun `contact message wraps a full TDLib contact card`() {
        val contact = TdApi.Contact(
            "+15551234567",
            "Ann",
            "Lee",
            ContactCard.vCard("Ann", "Lee", "+15551234567"),
            42L
        )

        val content = contactMessageContent(contact)

        assertSame(contact, content.contact)
        assertEquals(42L, content.contact.userId)
        assertTrue(content.contact.vcard.startsWith("BEGIN:VCARD\r\n"))
    }
}
