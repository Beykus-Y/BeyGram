package com.beykus.beygram.data.tdlib

import org.junit.Assert.assertEquals
import org.junit.Test

class CallEventDurationTest {
    @Test fun shortDuration() {
        assertEquals("0:00", callEventDuration(0))
        assertEquals("0:59", callEventDuration(59))
        assertEquals("1:05", callEventDuration(65))
    }

    @Test fun longDurationAndInvalidInput() {
        assertEquals("1:00:00", callEventDuration(3600))
        assertEquals("2:03:04", callEventDuration(7384))
        assertEquals("0:00", callEventDuration(-1))
    }
}
