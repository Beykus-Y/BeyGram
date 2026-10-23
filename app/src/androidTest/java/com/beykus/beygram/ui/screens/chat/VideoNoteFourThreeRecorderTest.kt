package com.beykus.beygram.ui.screens.chat

import androidx.camera.core.AspectRatio
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware-independent smoke coverage for the Recorder configuration used in #237.
 * The device matrix in docs/video-note-fov-qa.md is still required to prove actual FOV.
 */
@RunWith(AndroidJUnit4::class)
class VideoNoteFourThreeRecorderTest {
    @Test fun fourThreeHdRecorderExposesRequestedAspect() {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
            .setTargetVideoEncodingBitRate(4_000_000)
            .setAspectRatio(AspectRatio.RATIO_4_3)
            .build()
        assertEquals(AspectRatio.RATIO_4_3, recorder.aspectRatio)
        assertNotNull(VideoCapture.Builder(recorder).build())
    }

    @Test fun fallbackRecorderKeepsCameraXDefaultAspect() {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
            .setTargetVideoEncodingBitRate(4_000_000)
            .build()
        assertEquals(AspectRatio.RATIO_DEFAULT, recorder.aspectRatio)
        assertNotNull(VideoCapture.Builder(recorder).build())
    }
}
