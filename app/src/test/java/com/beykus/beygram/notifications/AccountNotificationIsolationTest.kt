package com.beykus.beygram.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccountNotificationIsolationTest {
    @Test fun legacyTrayTagAndPreferencesAreUnchanged() {
        assertEquals("beygram_message_42", accountNotificationTag("legacy", 42))
        assertEquals("notifications", notificationPreferencesName("legacy"))
    }

    @Test fun identicalTdlibNotificationGroupIdsNeverCollideAcrossAccounts() {
        val legacy = accountNotificationTag("legacy", 42)
        val a = accountNotificationTag("a-first", 42)
        val b = accountNotificationTag("a-second", 42)
        assertNotEquals(legacy, a)
        assertNotEquals(legacy, b)
        assertNotEquals(a, b)
        assertEquals("beygram_message_a-first_42", a)
        assertNotEquals(notificationPreferencesName("a-first"), notificationPreferencesName("a-second"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun notificationAccountRejectsPathTraversal() {
        accountNotificationTag("../legacy", 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun watermarkAccountRejectsPathTraversal() {
        notificationPreferencesName("a/../other")
    }
}
