package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerCounterVisibilityTest {
    @Test fun captionCounterAppearsAtNinetyPercent() {
        assertFalse(showCaptionCounter(921, 1024))
        assertTrue(showCaptionCounter(922, 1024))
        assertTrue(showCaptionCounter(1025, 1024))
        assertFalse(showCaptionCounter(0, 0))
    }

    @Test fun attachmentCountAppearsOneBeforeActualLimit() {
        assertFalse(showAttachmentLimit(3, 10))
        assertTrue(showAttachmentLimit(9, 10))
        assertTrue(showAttachmentLimit(10, 10))
        assertTrue(showAttachmentLimit(1, 1))
        assertFalse(showAttachmentLimit(0, 0))
    }
}
