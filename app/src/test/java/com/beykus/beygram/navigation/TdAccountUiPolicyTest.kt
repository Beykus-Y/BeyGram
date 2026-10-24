package com.beykus.beygram.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TdAccountUiPolicyTest {
    @Test fun sameMainRoutesAreAvailableToAnyBoundAccount() {
        val common = listOf(
            AppRoute.Chats, AppRoute.Chat, AppRoute.MessageThread, AppRoute.ChatInfo, AppRoute.Archive,
            AppRoute.Calls, AppRoute.Contacts, AppRoute.MyProfile,
            AppRoute.GlobalSearch, AppRoute.NewChat, AppRoute.Notifications
        )
        for (route in common) {
            assertTrue("Primary $route", accountRouteCanRender(route, "legacy", "legacy"))
            assertTrue("Secondary $route", accountRouteCanRender(route, "a-123", "a-123"))
            assertFalse("Foreign $route", accountRouteCanRender(route, "a-123", "legacy"))
            assertFalse("Offline $route", accountRouteCanRender(route, "a-123", null))
        }
    }

    @Test fun offlineSecondaryCanStillManageAndSwitchAccounts() {
        assertTrue(accountRouteCanRender(AppRoute.AccountPicker, "a-one", null))
        assertTrue(accountRouteCanRender(AppRoute.SecondaryAccounts, "a-one", null))
        assertFalse(accountRepositoryMatchesSelection("a-one", "a-two"))
        assertFalse(accountRepositoryMatchesSelection("a-one", "legacy"))
        assertTrue(accountRepositoryMatchesSelection("a-one", "a-one"))
    }

    @Test fun legacyNotificationsStillDeclareTheirSource() {
        assertEquals("legacy", LEGACY_NOTIFICATION_SOURCE_ACCOUNT)
    }
}
