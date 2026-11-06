package com.beykus.beygram.data.delayed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedSendDispatchTest {
    private fun entry(disableNotification: Boolean) = DelayedSendEntry(
        id = "message-1",
        accountId = "account-1",
        chatId = 1L,
        topicId = 0L,
        replyToMessageId = 0L,
        text = "текст",
        entities = emptyList(),
        disableNotification = disableNotification,
        createdAtMillis = 0L,
        notBeforeMillis = 5_000L,
        status = DelayedSendStatus.Dispatching
    )

    @Test fun aMessageSentWithSendNowCarriesTheUsersAgreement() {
        // Otherwise a stealth policy enabled after queuing would schedule it again, or fail where scheduling is unsupported.
        assertTrue(delayedDispatchOptions(entry(disableNotification = false), explicitSend = true).sendNowConsent)
    }

    @Test fun anAutomaticDispatchLeavesTheStealthPolicyInForce() {
        assertFalse(delayedDispatchOptions(entry(disableNotification = false), explicitSend = false).sendNowConsent)
    }

    @Test fun silentDeliveryIsKeptForBothKindsOfDispatch() {
        assertTrue(delayedDispatchOptions(entry(disableNotification = true), explicitSend = true).disableNotification)
        assertTrue(delayedDispatchOptions(entry(disableNotification = true), explicitSend = false).disableNotification)
    }
}
