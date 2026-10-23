package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNoteUseCaseGeometryTest {
    @Test
    fun hdSixteenToNineSquareCropRetainsLessHorizontalAreaThanSdFourToThree() {
        val hd = VideoNoteUseCaseGeometry(1280, 720, 280, 0, 1000, 720, 0)
        val sd = VideoNoteUseCaseGeometry(640, 480, 80, 0, 560, 480, 0)
        assertEquals("stream=1280x720 crop=(280,0) 720x720 retained=56%W/100%H rotation=0°",
            videoNoteUseCaseGeometrySummary(hd))
        assertEquals("stream=640x480 crop=(80,0) 480x480 retained=75%W/100%H rotation=0°",
            videoNoteUseCaseGeometrySummary(sd))
    }

    @Test
    fun squareSourceNeedsNoExtraViewportCrop() {
        assertEquals("stream=720x720 crop=(0,0) 720x720 retained=100%W/100%H rotation=90°",
            videoNoteUseCaseGeometrySummary(VideoNoteUseCaseGeometry(720, 720, 0, 0, 720, 720, 90)))
    }


    @Test
    fun portraitRotationDoesNotInventExtraCropOrSensorZoom() {
        val preview = VideoNoteUseCaseGeometry(
            streamWidth = 1920, streamHeight = 1080,
            cropLeft = 420, cropTop = 0, cropRight = 1500, cropBottom = 1080,
            rotationDegrees = 270
        )
        assertEquals(
            "stream=1920x1080 crop=(420,0) 1080x1080 retained=56%W/100%H rotation=270°",
            videoNoteUseCaseGeometrySummary(preview)
        )
    }

    @Test
    fun partialCropIsReportedWithoutRoundingToAnImaginaryFullFrame() {
        assertEquals(
            "stream=1000x1000 crop=(100,100) 800x800 retained=80%W/80%H rotation=0°",
            videoNoteUseCaseGeometrySummary(VideoNoteUseCaseGeometry(1000, 1000, 100, 100, 900, 900, 0))
        )
    }

    @Test
    fun missingOrImpossibleMetadataMustNotBeReportedAsMeasuredFov() {
        assertNull(videoNoteUseCaseGeometrySummary(null))
        assertNull(videoNoteUseCaseGeometrySummary(VideoNoteUseCaseGeometry(0, 480, 0, 0, 0, 480, 0)))
        assertNull(videoNoteUseCaseGeometrySummary(VideoNoteUseCaseGeometry(640, 480, -2, 0, 480, 480, 0)))
        assertNull(videoNoteUseCaseGeometrySummary(VideoNoteUseCaseGeometry(640, 480, 0, 0, 700, 480, 0)))
        assertNull(videoNoteUseCaseGeometrySummary(VideoNoteUseCaseGeometry(640, 480, 100, 0, 100, 480, 0)))
    }
}
