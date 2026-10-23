package com.beykus.beygram.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TdAccountUiPolicyTest {
    @Test fun secondaryAccountDoesNotInheritPrimaryOnlyScreens() {
        val legacyOnly = listOf(
            AppRoute.Chat, AppRoute.ChatInfo, AppRoute.Archive,
            AppRoute.Calls, AppRoute.Contacts, AppRoute.MyProfile,
            AppRoute.GlobalSearch, AppRoute.Notifications,
            AppRoute.NewChat, AppRoute.Sessions, AppRoute.Proxy
        )
        legacyOnly.forEach { assertFalse("Unsafe route $it", secondaryAccountMayOpen(it)) }
    }

    @Test fun secondaryAccountMayOnlyRenderItsOwnChatsAndAccountManager() {
        assertTrue(secondaryAccountMayOpen(AppRoute.Chats))
        assertTrue(secondaryAccountMayOpen(AppRoute.AccountPicker))
        assertTrue(secondaryAccountMayOpen(AppRoute.SecondaryAccounts))
        assertEquals(3, AppRoute.entries.count(::secondaryAccountMayOpen))
    }

    @Test fun currentNotificationProducerIsExplicitlyLegacyOnly() {
        assertEquals("legacy", LEGACY_NOTIFICATION_SOURCE_ACCOUNT)
    }
}
