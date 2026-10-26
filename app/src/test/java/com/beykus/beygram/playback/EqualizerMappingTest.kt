package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerMappingTest {
    private fun capabilities(
        frequencies: List<Int> = listOf(60, 230, 910, 3_600, 14_000),
        minMb: Int = -1_500,
        maxMb: Int = 1_500
    ) = EqualizerCapabilities(
        frequenciesHz = frequencies,
        hardwareMinLevelMb = minMb,
        hardwareMaxLevelMb = maxMb,
        systemPresets = listOf("Normal")
    )

    @Test
    fun flatIsNeutralOnEverySupportedBandCount() {
        listOf(
            listOf(60, 230, 910, 3_600, 14_000),
            listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000),
            listOf(100, 1_000, 10_000)
        ).forEach { frequencies ->
            val levels = capabilities(frequencies).levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Flat))
            assertEquals("Flat must be exactly neutral for $frequencies", List(frequencies.size) { 0 }, levels)
        }
    }

    @Test
    fun presetsUseTheRealBandCountAndOrderedFrequencies() {
        val levels = capabilities().levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Bass))
        assertEquals(5, levels.size)
        assertTrue("Bass must lift the low band above the high band", levels.first() > levels.last())
        assertTrue("Bass must actually boost", levels.first() > 0)
        assertEquals(5, capabilities().numberOfBands)
    }

    @Test
    fun tenBandDeviceUsesEveryReportedBand() {
        val frequencies = listOf(31, 63, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
        val levels = capabilities(frequencies).levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Bass))
        assertEquals(10, levels.size)
        assertEquals(600, levels.first())
        assertEquals(500, levels[1])
        assertEquals(0, levels.last())
    }

    @Test
    fun presetsStayInsideTheSafetyBoostLimit() {
        val limited = capabilities(maxMb = 300)
        EqualizerPreset.entries
            .filterNot { it == EqualizerPreset.Custom }
            .forEach { preset ->
                val levels = limited.levelsFor(EqualizerSettings(selectedPreset = preset))
                assertTrue("$preset exceeded a +3 dB hardware limit", levels.all { it <= 300 })
                assertTrue("$preset undercut the hardware floor", levels.all { it >= -1_500 })
            }
        val wide = capabilities(maxMb = 1_500)
        EqualizerPreset.Bass.let { preset ->
            val levels = wide.levelsFor(EqualizerSettings(selectedPreset = preset))
            assertTrue("Presets must respect BeyGram's clipping guard", levels.all { it <= EQUALIZER_MAX_BOOST_MB })
        }
    }

    @Test
    fun customLevelsAreRestoredByFrequencyNotByBandPosition() {
        val saved = mapOf(60 to 600, 230 to 300, 910 to 0, 3_600 to -300, 14_000 to -600)
        val sameDevice = capabilities().levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Custom, customLevelsMb = saved))
        assertEquals(listOf(600, 300, 0, -300, -600), sameDevice)

        // A device with fewer bands gets the same curve at the frequencies it does support.
        val reduced = capabilities(listOf(230, 3_600, 14_000))
            .levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Custom, customLevelsMb = saved))
        assertEquals(listOf(300, -300, -600), reduced)
    }

    @Test
    fun customCurveIsInterpolatedAndClamped() {
        val saved = mapOf(100 to 2_000, 1_000 to 0, 10_000 to -2_000)
        val levels = capabilities(listOf(100, 500, 1_000, 5_000, 10_000))
            .levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Custom, customLevelsMb = saved))
        assertEquals(EQUALIZER_MAX_BOOST_MB, levels.first())
        assertTrue("Mid-band must interpolate between the saved points", levels[1] in 1..EQUALIZER_MAX_BOOST_MB)
        assertEquals(0, levels[2])
        assertTrue("High interpolation must stay negative", levels[3] < 0)
        assertEquals(-1_500, levels.last())
    }

    @Test
    fun emptyCustomCurveIsNeutralAndDoesNotInventLevels() {
        assertEquals(
            List(3) { 0 },
            capabilities(listOf(100, 1_000, 10_000))
                .levelsFor(EqualizerSettings(selectedPreset = EqualizerPreset.Custom))
        )
    }

    @Test
    fun boostLimitReportsWhetherTheHardwareCouldGoHigher() {
        assertTrue(capabilities(maxMb = 1_500).boostIsLimited)
        assertFalse(capabilities(maxMb = 600).boostIsLimited)
        assertEquals(600, capabilities(maxMb = 1_500).maxLevelMb)
        assertEquals(300, capabilities(maxMb = 300).maxLevelMb)
        assertEquals(-1_500, capabilities().minLevelMb)
    }
}
