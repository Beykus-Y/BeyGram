package com.beykus.beygram.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerReturnRouteTest {
    private fun destination(
        stored: String = "Settings",
        previous: String = "Equalizer",
        returnTo: String = "Chat"
    ) = equalizerBackRouteName(
        storedRouteName = stored,
        previousRouteName = previous,
        equalizerRouteName = "Equalizer",
        settingsRouteName = "Settings",
        returnRouteName = returnTo
    )

    private fun entryReturn(
        route: String = "Equalizer",
        previous: String = "Chat",
        current: String = "Settings"
    ) = equalizerEntryReturnRouteName(
        routeName = route,
        previousRouteName = previous,
        equalizerRouteName = "Equalizer",
        startRouteName = "Start",
        currentReturnRouteName = current
    )

    @Test
    fun enteringFromSettingsReplacesAnOldChatReturnDestination() {
        assertEquals("Settings", entryReturn(previous = "Settings", current = "Chat"))
    }

    @Test
    fun enteringFromPlayerKeepsTheChatOrProfileDestination() {
        assertEquals("Chat", entryReturn(previous = "Chat", current = "Settings"))
        assertEquals("MyProfile", entryReturn(previous = "MyProfile", current = "Settings"))
    }

    @Test
    fun settingsEntryThenBothBackPathsStayInSettings() {
        val returnTo = entryReturn(previous = "Settings", current = "Chat")
        assertEquals("Settings", destination(returnTo = returnTo))
    }

    @Test
    fun routeNotEnteringEqualizerKeepsTheStoredDestination() {
        assertEquals("Chat", entryReturn(route = "Settings", previous = "Chat", current = "Chat"))
    }

    @Test
    fun restoredOrStartEntryKeepsTheStoredDestination() {
        assertEquals("Chat", entryReturn(previous = "Equalizer", current = "Chat"))
        assertEquals("Chat", entryReturn(previous = "Start", current = "Chat"))
    }

    @Test
    fun leavingEqualizerFromChatRestoresChat() {
        assertEquals("Chat", destination(returnTo = "Chat"))
    }

    @Test
    fun leavingEqualizerFromGroupProfileRestoresThatProfileRoute() {
        assertEquals("MyProfile", destination(returnTo = "MyProfile"))
    }

    @Test
    fun leavingEqualizerFromSettingsStaysInSettings() {
        assertEquals("Settings", destination(returnTo = "Settings"))
    }

    @Test
    fun settingsOpenedFromAnotherScreenIsNotRedirected() {
        assertEquals(
            "Settings",
            destination(stored = "Settings", previous = "Chat", returnTo = "Chat")
        )
    }

    @Test
    fun unrelatedRouteChangesAreNotRedirected() {
        assertEquals(
            "Chat",
            destination(stored = "Chat", previous = "Settings", returnTo = "MyProfile")
        )
    }

    @Test
    fun missingOrSelfReturnFallsBackToSettings() {
        assertEquals("Settings", destination(returnTo = ""))
        assertEquals("Settings", destination(returnTo = "Equalizer"))
    }
}
