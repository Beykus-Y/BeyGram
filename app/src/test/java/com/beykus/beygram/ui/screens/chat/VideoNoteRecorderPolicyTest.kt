package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.ui.screens.chat.VideoTakeOutcome.Discarded
import com.beykus.beygram.ui.screens.chat.VideoTakeOutcome.SaveFailed
import com.beykus.beygram.ui.screens.chat.VideoTakeOutcome.Send
import com.beykus.beygram.ui.screens.chat.VideoTakeOutcome.TooShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteRecorderPolicyTest {
    @Test fun missingZoomStateStillSendsNative1xRequest() {
        assertEquals(1f, videoNoteZoomTarget(1f, null, null), 0f)
        assertEquals(2f, videoNoteZoomTarget(2f, null, null), 0f)
        assertEquals(1f, videoNoteZoomTarget(Float.NaN, null, null), 0f)
    }

    @Test fun knownCameraBoundsClampZoomWithoutSneakingInExtraZoom() {
        assertEquals(1f, videoNoteZoomTarget(1f, 0.5f, 8f), 0f)
        assertEquals(0.5f, videoNoteZoomTarget(0.3f, 0.5f, 8f), 0f)
        assertEquals(8f, videoNoteZoomTarget(12f, 0.5f, 8f), 0f)
    }

    @Test fun switchRequiresPersistentRecordingAndBothCameras() {
        assertTrue(videoSwitchCameraAvailable(persistentSupported = true, hasFrontCamera = true, hasBackCamera = true))
        assertFalse(videoSwitchCameraAvailable(persistentSupported = false, hasFrontCamera = true, hasBackCamera = true))
        assertFalse(videoSwitchCameraAvailable(persistentSupported = true, hasFrontCamera = false, hasBackCamera = true))
        assertFalse(videoSwitchCameraAvailable(persistentSupported = true, hasFrontCamera = true, hasBackCamera = false))
    }

    @Test fun frontWithoutHardwareTorchUsesScreenLight() {
        assertEquals(VideoNoteLightMode.Screen, videoNoteLightMode(front = true, hasFlashUnit = false))
    }
    @Test fun hardwareTorchWinsForEitherLens() {
        assertEquals(VideoNoteLightMode.Torch, videoNoteLightMode(front = true, hasFlashUnit = true))
        assertEquals(VideoNoteLightMode.Torch, videoNoteLightMode(front = false, hasFlashUnit = true))
    }
    @Test fun backWithoutHardwareFlashDoesNotPretendToIlluminate() {
        assertEquals(VideoNoteLightMode.Unavailable, videoNoteLightMode(front = false, hasFlashUnit = false))
    }
    @Test fun healthyRecordingSends() {
        assertEquals(Send, videoTakeOutcome(error = 0, discard = false, durationSeconds = 5, stopRequested = true))
    }

    @Test fun cancelledHealthyRecordingIsDiscarded() {
        assertEquals(Discarded, videoTakeOutcome(error = 0, discard = true, durationSeconds = 12, stopRequested = true))
    }

    @Test fun shortHealthyRecordingIsTooShort() {
        assertEquals(TooShort, videoTakeOutcome(error = 0, discard = false, durationSeconds = 0, stopRequested = true))
    }

    @Test fun erroredFinalizeNeverSendsEvenIfUserCancelled() {
        assertEquals(SaveFailed, videoTakeOutcome(error = 1, discard = false, durationSeconds = 30, stopRequested = true))
        assertEquals(SaveFailed, videoTakeOutcome(error = 4, discard = true, durationSeconds = 0, stopRequested = true))
    }

    @Test fun unplannedFinalizeWithoutStopNeverSends() {
        assertEquals(SaveFailed, videoTakeOutcome(error = 0, discard = false, durationSeconds = 30, stopRequested = false))
        assertEquals(SaveFailed, videoTakeOutcome(error = 0, discard = true, durationSeconds = 30, stopRequested = false))
        assertEquals(SaveFailed, videoTakeOutcome(error = 0, discard = false, durationSeconds = 0, stopRequested = false))
    }
}
