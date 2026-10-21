package com.beykus.beygram.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoteSendTrackerTest {
    private val events = mutableListOf<Pair<String, VideoNoteSendEvent>>()
    private val tracker = VideoNoteSendTracker { session, event -> events += session to event }

    @Test fun pendingThenStoredIsReportedAgainstTheSession() {
        tracker.expect(1L, "s1")
        tracker.onPending(1L, -10L)
        tracker.onSucceeded(1L, -10L)
        assertEquals(listOf("s1" to VideoNoteSendEvent.Pending, "s1" to VideoNoteSendEvent.Succeeded), events)
    }

    @Test fun segmentsBindInOrder() {
        tracker.expect(1L, "s1")
        tracker.onPending(1L, -1L)
        tracker.expect(1L, "s1")
        tracker.onPending(1L, -2L)
        tracker.onFailed(1L, -2L, 400)
        assertEquals("s1" to VideoNoteSendEvent.Failed(400), events.last())
    }

    @Test fun otherMessagesAreIgnored() {
        tracker.onPending(1L, -1L)
        tracker.onSucceeded(1L, -1L)
        tracker.expect(2L, "s2")
        tracker.onPending(1L, -3L)
        assertTrue(events.isEmpty())
    }

    @Test fun forgottenExpectationDoesNotStealTheNextMessage() {
        tracker.expect(1L, "s1")
        tracker.forget("s1")
        tracker.onPending(1L, -1L)
        assertTrue(events.isEmpty())
    }
}
