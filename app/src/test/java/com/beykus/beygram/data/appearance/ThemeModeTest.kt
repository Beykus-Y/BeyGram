package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test fun storedNamesReadBackToTheSameChoice() {
        ThemeMode.entries.forEach { mode -> assertEquals(mode, themeModeFromStored(mode.name)) }
    }

    @Test fun missingOrUnknownStoredValueFollowsSystem() {
        assertEquals(ThemeMode.FollowSystem, themeModeFromStored(null))
        assertEquals(ThemeMode.FollowSystem, themeModeFromStored("AMOLED"))
    }

    @Test fun followSystemMirrorsTheSystemSetting() {
        assertTrue(ThemeMode.FollowSystem.isDark(systemDark = true))
        assertFalse(ThemeMode.FollowSystem.isDark(systemDark = false))
    }

    @Test fun explicitChoiceIgnoresTheSystemSetting() {
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
    }
}
