package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ChatHistory
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.LiveMessageArrival
import com.beykus.beygram.domain.model.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageArrivalTrackerTest {
    private fun message(id: Long, outgoing: Boolean = false, state: MessageSendState = MessageSendState.Sent) =
        ChatMessage(id = id, chatId = 1L, text = "message $id", time = "12:00", isOutgoing = outgoing, sendState = state)

    private fun history(
        messages: List<ChatMessage>,
        arrivals: List<LiveMessageArrival> = emptyList(),
        sequence: Long = arrivals.lastOrNull()?.sequence ?: 0L
    ) = ChatHistory(messages = messages, liveArrivals = arrivals, liveArrivalSequence = sequence)

    @Test fun openingChatWithHistoryNeverAnimatesExistingMessages() {
        val tracker = MessageArrivalTracker()
        val existing = listOf(message(1), message(2), message(3))
        tracker.observe(history(existing, listOf(LiveMessageArrival(1, 1), LiveMessageArrival(2, 2), LiveMessageArrival(3, 3))))
        assertEquals(0, tracker.pendingCount())
        assertFalse(tracker.claim(listOf(1, 2, 3)))
    }

    @Test fun loadingAnOlderPageAddsNoAnimation() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(5), message(4))))
        tracker.observe(history(listOf(message(5), message(4), message(3), message(2))))
        assertEquals(0, tracker.pendingCount())
        assertFalse(tracker.claim(listOf(3, 2)))
    }

    @Test fun aLiveMessageAnimatesOnceAndOnlyOnce() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        tracker.observe(history(listOf(message(1), message(2)), listOf(LiveMessageArrival(1, 2)), sequence = 1))
        assertEquals(1, tracker.pendingCount())
        assertTrue(tracker.claim(listOf(2)))
        assertFalse(tracker.claim(listOf(2)))
        // Recomposition and delivery updates repeat the same history snapshot.
        tracker.observe(history(listOf(message(1), message(2)), listOf(LiveMessageArrival(1, 2)), sequence = 1))
        assertFalse(tracker.claim(listOf(2)))
    }

    @Test fun aFastBurstAnimatesEveryNewMessage() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        val burst = listOf(
            LiveMessageArrival(1, 2),
            LiveMessageArrival(2, 3),
            LiveMessageArrival(3, 4),
            LiveMessageArrival(4, 5)
        )
        tracker.observe(history(listOf(message(1), message(2), message(3), message(4), message(5)), burst, sequence = 4))
        assertEquals(4, tracker.pendingCount())
        assertTrue(tracker.claim(listOf(2)))
        assertTrue(tracker.claim(listOf(3)))
        assertTrue(tracker.claim(listOf(4)))
        assertTrue(tracker.claim(listOf(5)))
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun temporaryToServerReplacementDoesNotAnimateTheServerId() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        val temporary = message(10, outgoing = true, state = MessageSendState.Sending)
        tracker.observe(history(listOf(message(1), temporary), listOf(LiveMessageArrival(1, 10)), sequence = 1))
        assertTrue(tracker.claim(listOf(10)))
        // UpdateMessageSendSucceeded replaces the temporary id and is deliberately absent from liveArrivals.
        tracker.observe(history(listOf(message(1), message(100, outgoing = true)), sequence = 1))
        assertEquals(0, tracker.pendingCount())
        assertFalse(tracker.claim(listOf(100)))
    }

    @Test fun editingDeliveryAndMediaUpdatesDoNotAnimateAgain() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        tracker.observe(history(listOf(message(1), message(2)), listOf(LiveMessageArrival(1, 2)), sequence = 1))
        assertTrue(tracker.claim(listOf(2)))
        val edited = message(2).copy(isEdited = true, text = "edited")
        tracker.observe(history(listOf(message(1), edited), listOf(LiveMessageArrival(1, 2)), sequence = 1))
        assertFalse(tracker.claim(listOf(2)))
        val read = edited.copy(sendState = MessageSendState.Sent)
        tracker.observe(history(listOf(message(1), read), listOf(LiveMessageArrival(1, 2)), sequence = 1))
        assertFalse(tracker.claim(listOf(2)))
    }

    @Test fun aRecreatedScreenSeedsTheCurrentSequenceAndAnimatesOnlyLaterArrivals() {
        val existing = history(
            listOf(message(1), message(2)),
            listOf(LiveMessageArrival(1, 1), LiveMessageArrival(2, 2)),
            sequence = 2
        )
        val recreated = MessageArrivalTracker()
        recreated.observe(existing)
        assertEquals(0, recreated.pendingCount())
        assertFalse(recreated.claim(listOf(2)))

        recreated.observe(
            history(
                listOf(message(1), message(2), message(3)),
                listOf(LiveMessageArrival(1, 1), LiveMessageArrival(2, 2), LiveMessageArrival(3, 3)),
                sequence = 3
            )
        )
        assertTrue(recreated.claim(listOf(3)))
    }

    @Test fun arrivalsForMessagesThatAreNotInTheLoadedWindowAreIgnored() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        tracker.observe(
            history(
                listOf(message(1)),
                listOf(LiveMessageArrival(1, 50)),
                sequence = 1
            )
        )
        assertEquals(0, tracker.pendingCount())
        assertFalse(tracker.claim(listOf(50)))
    }

    @Test fun backgroundBatchBecomesTheBaselineAndOnlyLaterMessagesAnimate() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        tracker.pause()
        // The collection resumes with messages received while the app was away.
        tracker.observe(
            history(
                listOf(message(1), message(2), message(3)),
                listOf(LiveMessageArrival(1, 2), LiveMessageArrival(2, 3)),
                sequence = 2
            )
        )
        assertEquals(0, tracker.pendingCount())
        tracker.resume()
        tracker.observe(
            history(
                listOf(message(1), message(2), message(3), message(4)),
                listOf(LiveMessageArrival(1, 2), LiveMessageArrival(2, 3), LiveMessageArrival(3, 4)),
                sequence = 3
            )
        )
        assertTrue(tracker.claim(listOf(4)))
    }

    @Test fun pausingDropsClaimsForRowsThatWereNeverComposed() {
        val tracker = MessageArrivalTracker()
        tracker.observe(history(listOf(message(1))))
        tracker.observe(history(listOf(message(1), message(2)), listOf(LiveMessageArrival(1, 2)), sequence = 1))
        assertEquals(1, tracker.pendingCount())
        tracker.pause()
        assertEquals(0, tracker.pendingCount())
    }

    @Test fun pendingClaimsAreBoundedSoOffscreenArrivalsCannotGrowForever() {
        val tracker = MessageArrivalTracker(maxPending = 2)
        tracker.observe(history(listOf(message(0))))
        tracker.observe(
            history(
                listOf(message(0), message(1), message(2), message(3)),
                listOf(LiveMessageArrival(1, 1), LiveMessageArrival(2, 2), LiveMessageArrival(3, 3)),
                sequence = 3
            )
        )
        assertEquals(2, tracker.pendingCount())
        assertFalse(tracker.claim(listOf(1)))
        assertTrue(tracker.claim(listOf(2)))
        assertTrue(tracker.claim(listOf(3)))
    }
}
