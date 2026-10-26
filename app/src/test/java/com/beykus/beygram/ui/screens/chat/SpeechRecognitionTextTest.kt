package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class SpeechRecognitionTextTest {
    @Test
    fun `reset date is formatted with the requested locale and timezone`() {
        // 2026-11-17 12:00:00 UTC
        assertEquals("Nov 17, 2026", formatTranscriptionResetDate(1_794_916_800L, Locale.US, TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun `duration uses minutes and zero-padded seconds`() {
        assertEquals("0:05", formatTranscriptionDuration(5))
        assertEquals("0:59", formatTranscriptionDuration(59))
        assertEquals("1:00", formatTranscriptionDuration(60))
        assertEquals("12:34", formatTranscriptionDuration(754))
        assertEquals("0:00", formatTranscriptionDuration(-1))
    }
}
