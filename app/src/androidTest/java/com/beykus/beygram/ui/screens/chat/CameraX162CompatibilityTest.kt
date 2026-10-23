package com.beykus.beygram.ui.screens.chat

import android.util.Range
import android.util.Rational
import android.view.Surface
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware-independent CameraX 1.6.x contract smoke tests.
 *
 * CI compiles this APK; running these tests on an Android device verifies that
 * CameraPipe-era CameraX still constructs the Preview + VideoCapture configuration
 * used by [VideoNoteRecorder]. These tests do NOT claim that any camera delivers
 * the requested FPS, has a particular FOV, or supports camera switching.
 */
@RunWith(AndroidJUnit4::class)
class CameraX162CompatibilityTest {
    @Test
    fun previewAndVideoCaptureShareSquareViewportWithFrontMirror() {
        val frameRange = Range(30, 30)
        val preview = Preview.Builder()
            .setTargetFrameRate(frameRange)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        val capture = VideoCapture.Builder(
            Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))
                )
                .setTargetVideoEncodingBitRate(4_000_000)
                .build()
        )
            .setTargetFrameRate(frameRange)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        val viewport = ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .setViewPort(viewport)
            .build()

        assertEquals(listOf(preview, capture), group.useCases)
        assertEquals(Rational(1, 1), group.viewPort?.aspectRatio)
        assertEquals(ViewPort.FILL_CENTER, group.viewPort?.scaleType)
    }

    @Test
    fun sdAndHdVideoCaptureConfigurationsAcceptVariableFrameRanges() {
        for (quality in listOf(Quality.SD, Quality.HD)) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality)))
                .setTargetVideoEncodingBitRate(2_500_000)
                .build()
            val capture = VideoCapture.Builder(recorder)
                .setTargetFrameRate(Range(30, 60))
                .setMirrorMode(MirrorMode.MIRROR_MODE_OFF)
                .build()

            // An unbound VideoCapture is valid. ResolutionInfo must be measured
            // only after binding to a real camera, not inferred from Quality.
            assertTrue(capture.resolutionInfo == null)
        }
    }
}
