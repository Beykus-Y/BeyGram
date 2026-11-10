package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerHeadroomTest {
    @Test
    fun theLargestBoostIsTakenOffEveryBand() {
        assertEquals(listOf(0, -300, -600), equalizerWithHeadroom(listOf(600, 300, 0), -1500))
    }

    @Test
    fun levelsWithoutABoostStayUntouched() {
        assertEquals(listOf(0, -200, -400), equalizerWithHeadroom(listOf(0, -200, -400), -1500))
        assertEquals(emptyList<Int>(), equalizerWithHeadroom(emptyList(), -1500))
    }

    @Test
    fun noBandGoesBelowTheHardwareFloor() {
        assertEquals(listOf(0, -1500), equalizerWithHeadroom(listOf(600, -1400), -1500))
    }
}
