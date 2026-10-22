package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoNoteSourceSizingTest {
    @Test fun exportDoesNotUpscaleSdCapture() {
        assertEquals(480, videoNoteOutputSide(640, 640, 480))
        assertEquals(640, videoNoteOutputSide(640, 1280, 720))
        assertEquals(384, videoNoteOutputSide(384, 640, 480))
        assertEquals(640, videoNoteOutputSide(720, 1920, 1080))
    }

    @Test fun unknownSourceSizeKeepsRequestedSideWithoutFailing() {
        assertEquals(480, videoNoteOutputSide(480, 0, 0))
        assertEquals(640, videoNoteOutputSide(720, 1280, 0))
    }
}
