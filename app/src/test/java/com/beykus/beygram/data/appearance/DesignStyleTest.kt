package com.beykus.beygram.data.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored design choice must never be able to leave the app without a valid style. */
class DesignStyleTest {
    @Test fun newInstallationKeepsTheOriginalDesign() {
        assertEquals(DesignStyle.Default, AppearanceConfig().designStyle)
        assertEquals(GlassQuality.Auto, AppearanceConfig().glassQuality)
    }

    @Test fun everyStyleRoundTripsThroughItsStoredName() {
        DesignStyle.entries.forEach { style ->
            assertEquals(style, designStyleFromStored(style.name))
        }
        GlassQuality.entries.forEach { quality ->
            assertEquals(quality, glassQualityFromStored(quality.name))
        }
    }

    @Test fun missingDamagedOrUnknownValuesFallBackToTheDefaults() {
        assertEquals(DesignStyle.Default, designStyleFromStored(null))
        assertEquals(DesignStyle.Default, designStyleFromStored(""))
        assertEquals(DesignStyle.Default, designStyleFromStored("Material 3"))
        assertEquals(DesignStyle.Default, designStyleFromStored("material3"))
        assertEquals(GlassQuality.Auto, glassQualityFromStored(null))
        assertEquals(GlassQuality.Auto, glassQualityFromStored("Ultra"))
    }

    @Test fun theSelectorOrderIsStable() {
        // The selector in Appearance shows the styles in this order; changing it changes the stored names.
        assertEquals(
            listOf("Default", "Material2", "Material3", "LiquidGlass", "Signature"),
            DesignStyle.entries.map { it.name }
        )
    }
}
