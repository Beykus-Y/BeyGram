package com.beykus.beygram.perf

import com.beykus.beygram.RouteTransitionFrame
import com.beykus.beygram.shouldKeepProfMainTabsMounted
import com.beykus.beygram.navigation.AppRoute
import com.beykus.beygram.navigation.MainTab
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfDiagnosticsTest {

    @Test
    fun defaultsAreAllDisabled() {
        val defaults = ProfAbFlags()
        assertFalse(defaults.navInstant)
        assertFalse(defaults.bubbleNoIntrinsic)
        assertFalse(defaults.glassChromeOnly)
        assertFalse(defaults.underChromeBypass)
        assertFalse(defaults.glassBypass)
        assertFalse(defaults.keepTabsMounted)
    }

    @Test
    fun updateOnlyAppliesOnProfBuild() {
        ProfDiagnostics.update(
            ProfAbFlags(
                navInstant = true,
                bubbleNoIntrinsic = true,
                glassChromeOnly = true,
                underChromeBypass = false,
                keepTabsMounted = true
            )
        )
        val expected = ProfDiagnostics.isProfBuild
        assertEquals(expected, ProfDiagnostics.navInstant)
        assertEquals(expected, ProfDiagnostics.bubbleNoIntrinsic)
        assertEquals(expected, ProfDiagnostics.glassChromeOnly)
        assertFalse(ProfDiagnostics.underChromeBypass)
        assertFalse(ProfDiagnostics.glassBypass)
        assertEquals(expected, ProfDiagnostics.keepTabsMounted)

        ProfDiagnostics.update(ProfAbFlags())
        assertFalse(ProfDiagnostics.navInstant)
        assertFalse(ProfDiagnostics.bubbleNoIntrinsic)
        assertFalse(ProfDiagnostics.glassChromeOnly)
        assertFalse(ProfDiagnostics.underChromeBypass)
        assertFalse(ProfDiagnostics.keepTabsMounted)
    }

    @Test
    fun h4IsDefaultOnlyInProfBuildAndCanStillBeDisabled() {
        assertTrue(defaultProfAbFlags(isProfBuild = true).keepTabsMounted)
        assertFalse(defaultProfAbFlags(isProfBuild = false).keepTabsMounted)
        assertFalse(defaultProfAbFlags(isProfBuild = true).copy(keepTabsMounted = false).keepTabsMounted)
        assertFalse(defaultProfAbFlags(isProfBuild = true).glassChromeOnly)
        assertFalse(defaultProfAbFlags(isProfBuild = true).bubbleNoIntrinsic)
    }

    @Test
    fun h4RetainsOnlyPhoneChatsStackAndRootTabs() {
        fun frame(route: AppRoute, twoPane: Boolean = false) = RouteTransitionFrame(route, twoPane)

        assertTrue(shouldKeepProfMainTabsMounted(frame(AppRoute.Chats), true, MainTab.Chats))
        assertTrue(shouldKeepProfMainTabsMounted(frame(AppRoute.Chat), true, MainTab.Chats))
        assertTrue(shouldKeepProfMainTabsMounted(frame(AppRoute.MessageThread), true, MainTab.Chats))
        assertTrue(shouldKeepProfMainTabsMounted(frame(AppRoute.ChatInfo), true, MainTab.Chats))

        assertFalse(shouldKeepProfMainTabsMounted(frame(AppRoute.Chat), false, MainTab.Chats))
        assertFalse(shouldKeepProfMainTabsMounted(frame(AppRoute.Chat, twoPane = true), true, MainTab.Chats))
        assertFalse(shouldKeepProfMainTabsMounted(frame(AppRoute.Chat), true, MainTab.Contacts))
        assertFalse(shouldKeepProfMainTabsMounted(frame(AppRoute.Proxy), true, MainTab.Chats))
    }

}
