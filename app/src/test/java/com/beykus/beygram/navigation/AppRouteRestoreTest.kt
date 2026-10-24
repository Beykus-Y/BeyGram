package com.beykus.beygram.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteRestoreTest {
    @Test fun removedThemeEditorRouteRestoresAsAppearance() {
        assertEquals(AppRoute.Appearance, appRouteFromStored("ThemeEditor"))
    }

    @Test fun currentRouteNamesRoundTrip() {
        // These routes belong to the removed experimental account screens.
        val migratedAccountRoutes = setOf(
            AppRoute.AccountPicker,
            AppRoute.SecondaryAccounts,
            AppRoute.SecondaryChats
        )
        AppRoute.entries.filterNot { it in migratedAccountRoutes }.forEach { route ->
            assertEquals(route, appRouteFromStored(route.name))
        }
    }

    @Test fun experimentalAccountRoutesRestoreThroughTheSharedSwitcher() {
        listOf(
            AppRoute.AccountPicker,
            AppRoute.SecondaryAccounts,
            AppRoute.SecondaryChats
        ).forEach { route ->
            assertEquals(AppRoute.Chats, appRouteFromStored(route.name))
        }
    }

    @Test fun missingOrUnknownRouteFallsBackToStart() {
        assertEquals(AppRoute.Start, appRouteFromStored(null))
        assertEquals(AppRoute.Start, appRouteFromStored(""))
        assertEquals(AppRoute.Start, appRouteFromStored("NotARoute"))
    }
}
