package com.beykus.beygram.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun inStableAvatarTapAlwaysOpensGallery() {
        assertEquals(AvatarTap.OpenGallery, ProfileSceneSettings.avatarTapAction(sceneC = false, photoExpanded = false))
        assertEquals(AvatarTap.OpenGallery, ProfileSceneSettings.avatarTapAction(sceneC = false, photoExpanded = true))
    }

    @Test
    fun inSceneFirstTapRevealsThenSecondOpensGallery() {
        assertEquals(AvatarTap.RevealPhoto, ProfileSceneSettings.avatarTapAction(sceneC = true, photoExpanded = false))
        assertEquals(AvatarTap.OpenGallery, ProfileSceneSettings.avatarTapAction(sceneC = true, photoExpanded = true))
    }

    @Test
    fun backFoldsRevealedPhotoOnlyInScene() {
        assertTrue(ProfileSceneSettings.backClosesExpansion(sceneC = true, photoExpanded = true))
        assertFalse(ProfileSceneSettings.backClosesExpansion(sceneC = true, photoExpanded = false))
        assertFalse(ProfileSceneSettings.backClosesExpansion(sceneC = false, photoExpanded = true))
        assertFalse(ProfileSceneSettings.backClosesExpansion(sceneC = false, photoExpanded = false))
    }

    @Test
    fun densityDefaultsAndMigratesLegacyExpressiveFlag() {
        assertEquals(ProfileSceneDensity.Balanced, ProfileSceneSettings.parseDensity(null, legacyExpressive = false))
        assertEquals(ProfileSceneDensity.Rich, ProfileSceneSettings.parseDensity(null, legacyExpressive = true))
        assertEquals(ProfileSceneDensity.Calm, ProfileSceneSettings.parseDensity("Calm", legacyExpressive = true))
        assertEquals(ProfileSceneDensity.Balanced, ProfileSceneSettings.parseDensity("Balanced", legacyExpressive = false))
        assertEquals(ProfileSceneDensity.Rich, ProfileSceneSettings.parseDensity("Rich", legacyExpressive = false))
        assertEquals(ProfileSceneDensity.Balanced, ProfileSceneSettings.parseDensity("Unknown", legacyExpressive = false))
    }
}
