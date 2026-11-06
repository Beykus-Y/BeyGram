package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.MessageDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldDelayedSendTest {
    @Test fun aHeldSendGoesOutWhileTheComposerStillHoldsIt() {
        val sent = MessageDraft("привет")
        assertTrue(heldSendStillMatches(sent, MessageDraft("привет"), attachmentsActive = false, stickerPending = false))
    }

    @Test fun anEditMadeWhileTheDelayLoadedCancelsTheSend() {
        // Nothing that the user did not send may go out in place of the text that was sent.
        val sent = MessageDraft("привет")
        assertFalse(heldSendStillMatches(sent, MessageDraft("привет!"), attachmentsActive = false, stickerPending = false))
        assertFalse(heldSendStillMatches(sent, MessageDraft(""), attachmentsActive = false, stickerPending = false))
    }

    @Test fun anAttachmentOrStickerAddedMeanwhileCancelsTheSend() {
        val sent = MessageDraft("привет")
        assertFalse(heldSendStillMatches(sent, sent, attachmentsActive = true, stickerPending = false))
        assertFalse(heldSendStillMatches(sent, sent, attachmentsActive = false, stickerPending = true))
    }
}
