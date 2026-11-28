package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdProfileColorsMappingTest {
    @Test
    fun profileAccentThemeMapsValidColorsAndRejectsMissing() {
        val peerColors = TdPeerColors()
        assertNull(peerColors.profileAccentTheme(-1))
        assertNull(peerColors.profileAccentTheme(42))

        peerColors.updateProfiles(
            TdApi.UpdateProfileAccentColors(
                arrayOf(
                    TdApi.ProfileAccentColor(
                        42,
                        TdApi.ProfileAccentColors(intArrayOf(0x112233), intArrayOf(0x445566, 0x778899), intArrayOf()),
                        TdApi.ProfileAccentColors(intArrayOf(0xAABBCC), intArrayOf(0x102030, 0x203040), intArrayOf()),
                        0,
                        0
                    )
                ),
                intArrayOf(42)
            )
        )

        val mapped = peerColors.profileAccentTheme(42)!!
        assertEquals(listOf(0x445566, 0x778899), mapped.lightBackground)
        assertEquals(listOf(0x102030, 0x203040), mapped.darkBackground)
        assertEquals(listOf(0x112233), mapped.lightPalette)
        assertEquals(listOf(0xAABBCC), mapped.darkPalette)
    }

    @Test
    fun upgradedGiftColorsMapToProfileAccentTheme() {
        val giftColors = TdApi.UpgradedGiftColors(
            7L,
            100L,
            200L,
            0x336699,
            intArrayOf(0xDDEEFF, 0xBBCCDD),
            0x6699CC,
            intArrayOf(0x112233, 0x223344)
        )
        val mapped = giftColors.toProfileAccentTheme()!!
        assertEquals(listOf(0xDDEEFF, 0xBBCCDD), mapped.lightBackground)
        assertEquals(listOf(0x112233, 0x223344), mapped.darkBackground)
        assertEquals(listOf(0x336699), mapped.lightPalette)
        assertEquals(listOf(0x6699CC), mapped.darkPalette)
    }
}
