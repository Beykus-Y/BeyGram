package com.beykus.beygram.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteRestoreTest {
    @Test fun removedThemeEditorRouteRestoresAsAppearance() {
        assertEquals(AppRoute.Appearance, appRouteFromStored("ThemeEditor"))
    }

    @Test fun currentRouteNamesRoundTrip() {
        AppRoute.entries.forEach { route ->
            assertEquals(route, appRouteFromStored(route.name))
        }
    }

    @Test fun missingOrUnknownRouteFallsBackToStart() {
        assertEquals(AppRoute.Start, appRouteFromStored(null))
        assertEquals(AppRoute.Start, appRouteFromStored(""))
        assertEquals(AppRoute.Start, appRouteFromStored("NotARoute"))
    }
}
