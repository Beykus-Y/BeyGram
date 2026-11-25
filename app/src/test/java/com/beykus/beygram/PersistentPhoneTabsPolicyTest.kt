package com.beykus.beygram

import com.beykus.beygram.navigation.AppRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentPhoneTabsPolicyTest {

    @Test
    fun phoneTabRootsAlwaysMountPersistentTabs() {
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chats, twoPane = false), phoneTabsVisited = false))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Calls, twoPane = false), phoneTabsVisited = false))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Contacts, twoPane = false), phoneTabsVisited = false))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Settings, twoPane = false), phoneTabsVisited = false))
    }

    @Test
    fun coldDeepLinkIntoChatDoesNotEagerlyMountTabsBeforeVisit() {
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chat, twoPane = false), phoneTabsVisited = false))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.MessageThread, twoPane = false), phoneTabsVisited = false))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.ChatInfo, twoPane = false), phoneTabsVisited = false))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.MyProfile, twoPane = false), phoneTabsVisited = false))
    }

    @Test
    fun afterVisitingRootTabsAllNonAuthSinglePaneRoutesKeepTabsMounted() {
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chat, twoPane = false), phoneTabsVisited = true))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.MessageThread, twoPane = false), phoneTabsVisited = true))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.ChatInfo, twoPane = false), phoneTabsVisited = true))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.MyProfile, twoPane = false), phoneTabsVisited = true))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Archive, twoPane = false), phoneTabsVisited = true))
    }

    @Test
    fun authRoutesAndTwoPaneNeverMountPersistentPhoneTabs() {
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Start, twoPane = false), phoneTabsVisited = true))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.LoginPhone, twoPane = false), phoneTabsVisited = true))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.LoginCode, twoPane = false), phoneTabsVisited = true))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.LoginPassword, twoPane = false), phoneTabsVisited = true))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chats, twoPane = true), phoneTabsVisited = true))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chat, twoPane = true), phoneTabsVisited = true))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.ChatInfo, twoPane = true), phoneTabsVisited = true))
    }
}
