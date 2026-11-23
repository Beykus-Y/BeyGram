package com.beykus.beygram

import com.beykus.beygram.navigation.AppRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentPhoneTabsPolicyTest {

    @Test
    fun phoneTabRootsAndChatSubtreeKeepTabsMounted() {
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chats, twoPane = false)))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Calls, twoPane = false)))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Contacts, twoPane = false)))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Settings, twoPane = false)))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chat, twoPane = false)))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.MessageThread, twoPane = false)))
        assertTrue(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.ChatInfo, twoPane = false)))
    }

    @Test
    fun authRoutesAndTwoPaneNeverMountPersistentPhoneTabs() {
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Start, twoPane = false)))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.LoginPhone, twoPane = false)))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.LoginCode, twoPane = false)))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.LoginPassword, twoPane = false)))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chats, twoPane = true)))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.Chat, twoPane = true)))
        assertFalse(shouldKeepPhoneTabsMounted(RouteTransitionFrame(AppRoute.ChatInfo, twoPane = true)))
    }
}
