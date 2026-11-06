package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class DelayedSendDraftTest {
    @Test
    fun anUnchangedComposerIsClearedOnceTheMessageIsStored() {
        assertEquals(MessageDraft(), draftAfterDelayedQueue(MessageDraft(text = "hello"), storedText = "hello"))
    }

    @Test
    fun textTypedWhileTheWriteRunsStaysInTheComposer() {
        // The user typed on while the stored copy was being written: the stored text goes, the new text stays.
        val typed = MessageDraft(text = "hello again")
        assertEquals(typed, draftAfterDelayedQueue(typed, storedText = "hello"))
    }
}
