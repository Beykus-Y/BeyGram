package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.PeerAccentColor
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdPeerColorsTest {
    @Test fun profileUpdatesDoNotReplaceTheNameAccentWithTheSameId() {
        val colors = TdPeerColors()
        val light = TdApi.ProfileAccentColors(intArrayOf(0x112233), intArrayOf(0x445566), intArrayOf())
        val dark = TdApi.ProfileAccentColors(intArrayOf(0xAABBCC), intArrayOf(0x223344), intArrayOf())
        colors.update(TdApi.UpdateAccentColors(arrayOf(
            TdApi.AccentColor(12, 4, intArrayOf(0x123456), intArrayOf(), 0)
        ), intArrayOf()))
        colors.updateProfiles(TdApi.UpdateProfileAccentColors(
            arrayOf(TdApi.ProfileAccentColor(12, light, dark, 0, 0)), intArrayOf(12)
        ))

        assertEquals(listOf(0x123456), colors.accent(12)?.lightRgb)
        assertEquals(listOf(0x112233), colors.profilePalette(12)?.light?.palette)
        assertEquals(listOf(0x223344), colors.profilePalette(12)?.dark?.background)
        assertEquals(1L, colors.revision.value)
    }

    @Test fun keepsServerPalettesAndBaseColorScopedToTheClient() {
        val first = TdPeerColors()
        val second = TdPeerColors()
        first.update(TdApi.UpdateAccentColors(arrayOf(
            TdApi.AccentColor(12, 4, intArrayOf(0x123456), intArrayOf(0xABCDEF, 0x112233), 0)
        ), intArrayOf()))

        assertEquals(PeerAccentColor(4, listOf(0x123456), listOf(0xABCDEF, 0x112233)), first.accent(12))
        assertNull(second.accent(12))
        assertEquals(PeerAccentColor(4), second.accent(4))

        first.update(TdApi.UpdateAccentColors(emptyArray(), intArrayOf()))
        assertNull(first.accent(12))
        assertEquals(2L, first.revision.value)
        assertEquals(0L, second.revision.value)
    }

    @Test fun malformedRgbFallsBackToTheServerSpecifiedBase() {
        val colors = TdPeerColors()
        colors.update(TdApi.UpdateAccentColors(arrayOf(
            TdApi.AccentColor(12, 4, intArrayOf(-1), intArrayOf(0xABCDEF), 0)
        ), intArrayOf()))

        assertEquals(PeerAccentColor(4, darkRgb = listOf(0xABCDEF)), colors.accent(12))
    }
}
