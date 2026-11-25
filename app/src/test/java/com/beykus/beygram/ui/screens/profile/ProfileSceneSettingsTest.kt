package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSceneSettingsTest {
    @Test
    fun disabledFlagKeepsStableProfileA() {
        assertEquals(ProfileVariant.A, ProfileSceneSettings.variantFor(false))
    }

    @Test
    fun enabledFlagSelectsSceneC() {
        assertEquals(ProfileVariant.C, ProfileSceneSettings.variantFor(true))
    }
}
