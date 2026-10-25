package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoNoteSessionConfigurationTest {
    @Test fun oldAndroidIsUnknownNotUnsupported() {
        val (support, reason) = sessionConfigurationOutcome(apiLevel = 34, setupSupported = null, configurationSupported = null)
        assertEquals(VideoNoteSessionSupport.UNKNOWN, support)
        assertEquals(VideoNoteSessionReason.API_TOO_OLD, reason)
    }

    @Test fun unavailableSetupCheckIsUnknownNotACombinationFailure() {
        val (support, reason) = sessionConfigurationOutcome(apiLevel = 35, setupSupported = false, configurationSupported = null)
        assertEquals(VideoNoteSessionSupport.UNKNOWN, support)
        assertEquals(VideoNoteSessionReason.SETUP_CHECK_UNAVAILABLE, reason)
    }

    @Test fun missingAnswerIsUnknown() {
        val (support, reason) = sessionConfigurationOutcome(apiLevel = 35, setupSupported = true, configurationSupported = null)
        assertEquals(VideoNoteSessionSupport.UNKNOWN, support)
        assertEquals(VideoNoteSessionReason.SETUP_CHECK_UNAVAILABLE, reason)
    }

    @Test fun missingSetupAnswerIsUnknownEvenIfAConfigurationAnswerExists() {
        val (support, reason) = sessionConfigurationOutcome(apiLevel = 35, setupSupported = null, configurationSupported = true)
        assertEquals(VideoNoteSessionSupport.UNKNOWN, support)
        assertEquals(VideoNoteSessionReason.SETUP_CHECK_UNAVAILABLE, reason)
    }

    @Test fun rejectedCombinationIsUnsupported() {
        val (support, reason) = sessionConfigurationOutcome(apiLevel = 35, setupSupported = true, configurationSupported = false)
        assertEquals(VideoNoteSessionSupport.UNSUPPORTED, support)
        assertEquals(VideoNoteSessionReason.CONFIGURATION_REJECTED, reason)
    }

    @Test fun acceptedCombinationIsSupported() {
        val (support, reason) = sessionConfigurationOutcome(apiLevel = 35, setupSupported = true, configurationSupported = true)
        assertEquals(VideoNoteSessionSupport.SUPPORTED, support)
        assertEquals(VideoNoteSessionReason.CONFIGURATION_SUPPORTED, reason)
    }

    @Test fun exceptionIsUnknownWithDetail() {
        val (support, reason) = sessionConfigurationOutcome(
            apiLevel = 35, setupSupported = true, configurationSupported = true,
            error = "IllegalArgumentException"
        )
        assertEquals(VideoNoteSessionSupport.UNKNOWN, support)
        assertEquals(VideoNoteSessionReason.EXCEPTION, reason)
    }
}
