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
