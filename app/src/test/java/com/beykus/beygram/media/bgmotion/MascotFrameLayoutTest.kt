package com.beykus.beygram.media.bgmotion

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotFrameLayoutTest {
    @Test
    fun nativeContractLengthAndPulseFieldsAreStable() {
        assertEquals(40, MascotFrameLayout.FLOAT_COUNT)
        assertEquals(37, MascotFrameLayout.SEEK_PULSE)
        assertEquals(38, MascotFrameLayout.SEEK_DIRECTION)
        assertEquals(39, MascotFrameLayout.TRANSITION_PULSE)
    }

    @Test
    fun pulseReadersClampAndKeepDirection() {
        val values = FloatArray(MascotFrameLayout.FLOAT_COUNT)
        values[MascotFrameLayout.SEEK_PULSE] = 1.4f
        values[MascotFrameLayout.SEEK_DIRECTION] = -1f
        values[MascotFrameLayout.TRANSITION_PULSE] = -0.2f

        assertEquals(1f, values.seekPulse(), 0f)
        assertEquals(-1f, values.seekDirection(), 0f)
        assertEquals(0f, values.transitionPulse(), 0f)
    }
}
