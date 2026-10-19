package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePresetTest {
    @Test fun storedNamesReadBackToTheSamePreset() {
        ThemePreset.entries.forEach { preset -> assertEquals(preset, themePresetFromStored(preset.name)) }
    }

    @Test fun systemColorsPresetIsStoredByName() {
        assertEquals(ThemePreset.MaterialYou, themePresetFromStored("MaterialYou"))
    }

    @Test fun missingOrUnknownStoredValueIsWarm() {
        assertEquals(ThemePreset.Warm, themePresetFromStored(null))
        assertEquals(ThemePreset.Warm, themePresetFromStored("Neon"))
    }

    @Test fun defaultChoiceIsWarmFollowingSystem() {
        assertEquals(AppearanceConfig(ThemePreset.Warm, ThemeMode.FollowSystem), AppearanceConfig())
    }
}
