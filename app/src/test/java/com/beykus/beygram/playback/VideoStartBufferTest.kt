package com.beykus.beygram.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoStartBufferTest {
    @Test fun unknownOrMissingWireValueFallsBackToDefault() {
        assertEquals(VideoStartBuffer.DEFAULT, VideoStartBuffer.fromWire(null))
        assertEquals(VideoStartBuffer.DEFAULT, VideoStartBuffer.fromWire("42"))
    }

    @Test fun everyExperimentalVariantRoundTripsItsWireValue() {
        for (mode in VideoStartBuffer.entries) {
            assertEquals(mode, VideoStartBuffer.fromWire(mode.wire))
        }
    }

    @Test fun experimentalThresholdsAreTheMeasuredSet() {
        assertEquals(listOf(null, 100, 250, 500, 1000), VideoStartBuffer.entries.map { it.bufferForPlaybackMs })
    }

    @Test fun defaultLeavesMedia3LoadControlUntouched() {
        assertNull(VideoStartBuffer.DEFAULT.loadControlOrNull())
    }

    @Test fun experimentalVariantsBuildALoadControl() {
        for (mode in VideoStartBuffer.entries.filter { it.bufferForPlaybackMs != null }) {
            assertNotNull(mode.wire, mode.loadControlOrNull())
        }
    }
}
