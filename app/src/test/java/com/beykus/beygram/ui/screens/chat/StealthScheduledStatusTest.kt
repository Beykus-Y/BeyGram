package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Test

class StealthScheduledStatusTest {
    @Test fun pendingAndFailedMessagesDoNotShowAsQueued() {
        assertEquals(R.string.stealth_schedule_status_pending, scheduledMessageStatus(MessageSendState.Sending, queueKnown = true))
        assertEquals(R.string.chat_scheduled_failed, scheduledMessageStatus(MessageSendState.Failed, queueKnown = true))
        assertEquals(R.string.stealth_schedule_status_queued, scheduledMessageStatus(MessageSendState.Sent, queueKnown = true))
    }

    @Test fun staleRowsNeverClaimAConfirmedStatus() {
        MessageSendState.entries.forEach { state ->
            assertEquals(R.string.stealth_schedule_status_unknown, scheduledMessageStatus(state, queueKnown = false))
        }
    }
}
