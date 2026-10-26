package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPosterSizingTest {
    @Test
    fun wideVideoKeepsItsAspectRatio() {
        assertEquals(320 to 180, fitVideoPosterSize(1920, 1080, 320))
    }

    @Test
    fun portraitVideoKeepsItsAspectRatio() {
        assertEquals(180 to 320, fitVideoPosterSize(1080, 1920, 320))
    }

    @Test
    fun alreadySmallThumbnailIsNotUpscaled() {
        assertEquals(100 to 50, fitVideoPosterSize(100, 50, 320))
    }

    @Test
    fun invalidMetadataUsesBoundedSquareFallback() {
        assertEquals(320 to 320, fitVideoPosterSize(0, -1, 320))
    }

    @Test
    fun veryThinVideoStillProducesNonzeroDimensions() {
        assertEquals(1 to 320, fitVideoPosterSize(1, 4000, 320))
    }
}
