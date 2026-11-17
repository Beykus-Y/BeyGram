package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleDecodeDimensionTest {

    @Test
    fun unknownSizeFallsBackToThousandTwentyFour() {
        assertEquals(1024, bubbleDecodeDimension(0, 0, 732f, 549f))
    }

    @Test
    fun landscapePhotoRoundsUpToNextStep() {
        // 1280x960 into 732x549: longest side 1280 * 0.571875 = 732 -> 768.
        assertEquals(768, bubbleDecodeDimension(1280, 960, 732f, 549f))
    }

    @Test
    fun portraitPhotoInTallBoxUsesLongestSide() {
        // 1080x1920 into 732x840: scale 0.6778 (width bound), 1920 * scale = 1302 -> 1536.
        assertEquals(1536, bubbleDecodeDimension(1080, 1920, 732f, 840f))
    }

    @Test
    fun panoramaIsCappedAtBubbleMaximum() {
        // 2560x400 into 732x360: scale 0.9, 2560 * 0.9 = 2304 -> 2304 is capped to 2048.
        assertEquals(2048, bubbleDecodeDimension(2560, 400, 732f, 360f))
    }

    @Test
    fun smallSourceStillRequestsBoxCoverage() {
        // 100x100 into 732x732: scale 7.32, 100 * 7.32 = 732 -> 768. The decoder never upscales.
        assertEquals(768, bubbleDecodeDimension(100, 100, 732f, 732f))
    }

    @Test
    fun tinyBoxClampsToMinimum() {
        // 1000x1000 into 10x10: 1000 * 0.01 = 10 -> 256 -> clamped to 512.
        assertEquals(512, bubbleDecodeDimension(1000, 1000, 10f, 10f))
    }

    @Test
    fun resultIsAlwaysStepOfTwoFiftySixWithinRange() {
        val sizes = listOf(
            1 to 1, 320 to 240, 640 to 480, 1280 to 960, 1080 to 1920,
            2560 to 400, 4000 to 3000, 100 to 100, 9000 to 16
        )
        val boxes = listOf(120f to 120f, 244f to 120f, 732f to 549f, 732f to 840f, 732f to 1120f)
        for ((w, h) in sizes) for ((bw, bh) in boxes) {
            val result = bubbleDecodeDimension(w, h, bw, bh)
            assertEquals("for $w x $h in $bw x $bh", 0, result % 256)
            assertTrue("for $w x $h in $bw x $bh was $result", result in 512..2048)
        }
    }
}
