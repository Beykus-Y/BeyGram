package com.beykus.beygram.data.tdlib

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoNoteLossTransportTest {
    @Test fun lengthIsTheSideAndDurationIsSeconds() {
        // The 3-second 480x480 synthetic clip was reported as "length 480 s".
        val note = TdApi.VideoNote(3, null, 480, null, null, null, null)
        assertEquals(480 to 3, confirmedVideoNoteShape(note))
    }
}
