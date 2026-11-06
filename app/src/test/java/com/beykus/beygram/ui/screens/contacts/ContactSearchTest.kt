package com.beykus.beygram.ui.screens.contacts

import com.beykus.beygram.domain.model.ContactPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {
    private val contact = ContactPreview(
        userId = 42L,
        title = "Анна Петрова",
        subtitle = "@anya",
        photo = null,
        avatarColorArgb = 0L,
        phone = "+7 (923) 123-45-67"
    )

    @Test fun matchesNameAndUsernameWithoutChangingExistingSearch() {
        assertTrue(contactMatchesQuery(contact, "анна"))
        assertTrue(contactMatchesQuery(contact, "@ANYA"))
        assertTrue(contactMatchesQuery(contact, "  "))
        assertFalse(contactMatchesQuery(contact, "Павел"))
    }

    @Test fun findsSavedPhoneWhenUsernameIsDisplayed() {
        assertTrue(contactMatchesQuery(contact, "923123"))
        assertTrue(contactMatchesQuery(contact, "+7 (923) 123"))
        assertTrue(contactMatchesQuery(contact, "45-67"))
        assertFalse(contactMatchesQuery(contact, "923124"))
    }

    @Test fun shortOrNonPhoneQueriesDoNotMatchHiddenNumber() {
        assertFalse(contactMatchesQuery(contact, "23"))
        assertFalse(contactMatchesQuery(contact, "923abc"))
        assertFalse(contactMatchesQuery(contact.copy(phone = ""), "923123"))
    }
}
