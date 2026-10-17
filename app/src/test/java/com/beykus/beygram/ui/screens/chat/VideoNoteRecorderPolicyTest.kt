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
    @Test fun switchRequiresPersistentRecordingAndBothCameras() {
        assertTrue(videoSwitchCameraAvailable(persistentSupported = true, hasFrontCamera = true, hasBackCamera = true))
        assertFalse(videoSwitchCameraAvailable(persistentSupported = false, hasFrontCamera = true, hasBackCamera = true))
        assertFalse(videoSwitchCameraAvailable(persistentSupported = true, hasFrontCamera = false, hasBackCamera = true))
        assertFalse(videoSwitchCameraAvailable(persistentSupported = true, hasFrontCamera = true, hasBackCamera = false))
    }

    @Test fun healthyRecordingSends() {
        assertEquals(Send, videoTakeOutcome(error = 0, discard = false, durationSeconds = 5))
    }

    @Test fun cancelledHealthyRecordingIsDiscarded() {
        assertEquals(Discarded, videoTakeOutcome(error = 0, discard = true, durationSeconds = 12))
    }

    @Test fun shortHealthyRecordingIsTooShort() {
        assertEquals(TooShort, videoTakeOutcome(error = 0, discard = false, durationSeconds = 0))
    }

    @Test fun erroredFinalizeNeverSendsEvenIfUserCancelled() {
        assertEquals(SaveFailed, videoTakeOutcome(error = 1, discard = false, durationSeconds = 30))
        assertEquals(SaveFailed, videoTakeOutcome(error = 4, discard = true, durationSeconds = 0))
    }
}
