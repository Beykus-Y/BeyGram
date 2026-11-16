package com.beykus.beygram.media.bgmotion

import org.junit.Assert.assertEquals
import org.junit.Test

class BgMascotSettingsStoreTest {
    @Test
    fun intensityIsClampedToTheSupportedRange() {
        assertEquals(0.5f, BgMascotSettingsStore.clampIntensity(-10f), 0f)
        assertEquals(1.5f, BgMascotSettingsStore.clampIntensity(10f), 0f)
        assertEquals(1.0f, BgMascotSettingsStore.clampIntensity(Float.NaN), 0f)
    }

    @Test
    fun audioOffsetIsClampedToTheCalibrationRange() {
        assertEquals(-150, BgMascotSettingsStore.clampAudioOffsetMs(-10_000))
        assertEquals(150, BgMascotSettingsStore.clampAudioOffsetMs(10_000))
        assertEquals(0, BgMascotSettingsStore.clampAudioOffsetMs(0))
    }
}
