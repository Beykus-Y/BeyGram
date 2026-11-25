package com.beykus.beygram.data.appearance

import android.app.Application
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import com.beykus.beygram.ui.design.LensTuningPresets
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.screens.theme.resetAllAppearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppearanceSettingsStoreTest {
    @Test
    fun freshConfigAndEmptyPreferencesEnableV2ClearByDefault() {
        val fresh = AppearanceConfig()
        assertTrue(fresh.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Clear, fresh.liquidGlassPreset)

        val fromEmpty = appearanceConfigFromPreferences(emptyPreferences())
        assertTrue(fromEmpty.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Clear, fromEmpty.liquidGlassPreset)
    }

    @Test
    fun corruptedLensPresetFallsBackToClear() {
        val corrupted = preferencesOf(
            LiquidGlassV2Key to true,
            LiquidGlassLensPresetKey to "CorruptedPreset"
        )
        val config = appearanceConfigFromPreferences(corrupted)
        assertTrue(config.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Clear, config.liquidGlassPreset)
    }

    @Test
    fun legacyExplicitFalseAndStandardArePreserved() {
        val legacy = preferencesOf(
            LiquidGlassV2Key to false,
            LiquidGlassLensPresetKey to LiquidGlassLensPreset.Standard.name
        )
        val config = appearanceConfigFromPreferences(legacy)
        assertFalse(config.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Standard, config.liquidGlassPreset)
    }

    @Test
    fun freshStoreSaveStyleSwitchAndFullResetWorkEndToEnd() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = AppearanceSettingsStore(context)

        val initial = store.config.first()
        assertTrue(initial.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Clear, initial.liquidGlassPreset)

        // Switching to Liquid Glass on a fresh install uses V2 Clear.
        val liquidGlass = initial.copy(designStyle = DesignStyle.LiquidGlass)
        val resolvedDefaultGlass = designFor(
            liquidGlass.designStyle,
            liquidGlass.glassQuality,
            liquidGlassV2 = liquidGlass.liquidGlassV2,
            liquidGlassPreset = liquidGlass.liquidGlassPreset
        ).surfaces.glass!!
        assertTrue(resolvedDefaultGlass.lensRefraction)
        assertEquals(LensTuningPresets.Clear, resolvedDefaultGlass.lensTuning)

        // User disables V2 and switches to another style and back: choice is preserved.
        val disabledV2 = liquidGlass.copy(liquidGlassV2 = false, liquidGlassPreset = LiquidGlassLensPreset.Bold)
        val switchedAwayAndBack = disabledV2
            .copy(designStyle = DesignStyle.Signature)
            .copy(designStyle = DesignStyle.LiquidGlass)
        store.save(switchedAwayAndBack)

        val persisted = store.config.first()
        assertFalse(persisted.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Bold, persisted.liquidGlassPreset)

        // Full reset restores V2 Clear.
        val reset = persisted.resetAllAppearance()
        store.save(reset)
        val afterReset = store.config.first()
        assertTrue(afterReset.liquidGlassV2)
        assertEquals(LiquidGlassLensPreset.Clear, afterReset.liquidGlassPreset)
    }
}
