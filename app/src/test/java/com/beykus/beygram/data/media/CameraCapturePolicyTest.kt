package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapturePolicyTest {
    @Test
    fun timerCyclesThroughOffThreeAndTenSeconds() {
        assertEquals(3, cameraNextTimerSeconds(0))
        assertEquals(10, cameraNextTimerSeconds(3))
        assertEquals(0, cameraNextTimerSeconds(10))
        assertEquals(3, cameraNextTimerSeconds(42))
    }

    @Test
    fun videoLimitIsClampedToASafeMaximum() {
        assertEquals(15, cameraVideoLimitSeconds(15))
        assertEquals(1, cameraVideoLimitSeconds(0))
        assertEquals(CAMERA_VIDEO_HARD_LIMIT_SECONDS, cameraVideoLimitSeconds(Int.MAX_VALUE))
    }

    @Test
    fun flashFollowsTheCameraCapability() {
        assertTrue(cameraFlashAvailable(true))
        assertFalse(cameraFlashAvailable(false))
    }

    @Test
    fun zoomStaysInsideTheCameraRange() {
        assertEquals(2f, cameraZoomTarget(1f, 2f, 1f, 4f), 0.001f)
        assertEquals(4f, cameraZoomTarget(3f, 2f, 1f, 4f), 0.001f)
        assertEquals(1f, cameraZoomTarget(1f, 0.1f, 1f, 4f), 0.001f)
        // Some devices report no zoom state at bind time; the request still has to be valid.
        assertEquals(1f, cameraZoomTarget(1f, 0.5f, null, null), 0.001f)
    }

    @Test
    fun remainingTimeAndProgressNeverGoNegativeOrAboveOne() {
        assertEquals(60, cameraRemainingSeconds(60, 0))
        assertEquals(1, cameraRemainingSeconds(60, 59_500))
        assertEquals(0, cameraRemainingSeconds(60, 90_000))
        assertEquals(0f, cameraRecordingProgress(60, -5), 0.001f)
        assertEquals(0.5f, cameraRecordingProgress(60, 30_000), 0.001f)
        assertEquals(1f, cameraRecordingProgress(60, 90_000), 0.001f)
        assertEquals(0f, cameraRecordingProgress(0, 5_000), 0.001f)
    }

    @Test
    fun captureNamesUseTheExpectedContainer() {
        assertEquals("camera-abc.jpg", cameraCaptureFileName(CameraCaptureMode.Photo, "abc"))
        assertEquals("camera-abc.mp4", cameraCaptureFileName(CameraCaptureMode.Video, "abc"))
    }

    @Test
    fun rotationDegreesMapAndroidDisplayRotation() {
        assertEquals(0, cameraRotationDegrees(0))
        assertEquals(90, cameraRotationDegrees(1))
        assertEquals(180, cameraRotationDegrees(2))
        assertEquals(270, cameraRotationDegrees(3))
    }

    @Test
    fun takeOutcomePrefersErrorsAndRequiresExplicitStop() {
        assertEquals(CameraTakeOutcome.Send, cameraTakeOutcome(0, false, 5_000, true))
        assertEquals(CameraTakeOutcome.Discarded, cameraTakeOutcome(0, true, 5_000, true))
        assertEquals(CameraTakeOutcome.TooShort, cameraTakeOutcome(0, false, 400, true))
        assertEquals(CameraTakeOutcome.Failed, cameraTakeOutcome(1, false, 5_000, true))
        assertEquals(CameraTakeOutcome.Failed, cameraTakeOutcome(0, false, 5_000, false))
    }
}
