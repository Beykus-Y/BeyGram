package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppearanceMigrationTest {
    @Test fun legacyDarkSwitchMigratesToDarkOrLight() {
        assertEquals(ThemeMode.Dark, migratedThemeMode(hasStoredMode = false, legacyDarkTheme = true))
        assertEquals(ThemeMode.Light, migratedThemeMode(hasStoredMode = false, legacyDarkTheme = false))
    }

    @Test fun storedChoiceIsNeverOverwrittenByTheLegacySwitch() {
        assertNull(migratedThemeMode(hasStoredMode = true, legacyDarkTheme = true))
        assertNull(migratedThemeMode(hasStoredMode = true, legacyDarkTheme = false))
    }

    @Test fun noLegacySwitchMeansNothingToMigrate() {
        assertNull(migratedThemeMode(hasStoredMode = false, legacyDarkTheme = null))
    }

    @Test fun missingOrDamagedPlacementFollowsTheStyle() {
        assertEquals(NavigationPlacement.Auto, navigationPlacementFromStored(null))
        assertEquals(NavigationPlacement.Auto, navigationPlacementFromStored(""))
        assertEquals(NavigationPlacement.Auto, navigationPlacementFromStored("Dock"))
        assertEquals(NavigationPlacement.Pinned, navigationPlacementFromStored("Pinned"))
        assertEquals(NavigationPlacement.Floating, navigationPlacementFromStored("Floating"))
    }

    @Test fun autoIsNotAForcedPlacementOfItsOwn() {
        assertNull(NavigationPlacement.Auto.forced)
        assertEquals(NavigationPlacement.Pinned, NavigationPlacement.Pinned.forced)
        assertEquals(NavigationPlacement.Floating, NavigationPlacement.Floating.forced)
    }
}
