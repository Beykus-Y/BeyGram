package com.beykus.beygram.ui.screens.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsAccountResultTest {
    @Test fun currentAccountMayOpenLoadedContactChat() {
        assertTrue(canUseContactResult("account-a", "account-a"))
        assertTrue(canUseContactResult("legacy", "legacy"))
    }

    @Test fun oldAccountMustNotNavigateAfterSwitch() {
        assertFalse(canUseContactResult("account-a", "account-b"))
        assertFalse(canUseContactResult("legacy", "account-a"))
    }
}
