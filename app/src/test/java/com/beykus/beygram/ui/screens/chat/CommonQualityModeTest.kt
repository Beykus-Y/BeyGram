package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonQualityModeTest {
    @Test
    fun sameModeForAllFilesIsReturned() {
        assertEquals(MediaQualityMode.Compact, commonQualityMode(listOf(MediaQualityMode.Compact, MediaQualityMode.Compact)))
    }

    @Test
    fun mixedModesGiveNoCommonMode() {
        assertNull(commonQualityMode(listOf(MediaQualityMode.Compact, MediaQualityMode.Original)))
    }

    @Test
    fun missingPreparationBreaksTheCommonMode() {
        assertNull(commonQualityMode(listOf(MediaQualityMode.Balanced, null)))
    }

    @Test
    fun emptyListHasNoCommonMode() {
        assertNull(commonQualityMode(emptyList()))
    }
}
