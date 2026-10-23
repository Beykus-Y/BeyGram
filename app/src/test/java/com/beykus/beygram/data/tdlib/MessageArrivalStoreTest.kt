package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageArrivalStoreTest {
    private fun message(id: Long, date: Long = id, state: MessageSendState = MessageSendState.Sent) =
        ChatMessage(id = id, chatId = 1L, text = "message $id", time = "12:00", isOutgoing = false, dateSeconds = date, sendState = state)

    @Test fun aLiveInsertionIsRecordedOnceAndAnUpdateIsNotAnotherArrival() {
        val store = MessageHistoryStore()
        store.put(message(7))
        store.put(message(7).copy(text = "edited"))
        val history = store.observe(1L).value
        assertEquals(1L, history.liveArrivalSequence)
        assertEquals(listOf(7L), history.liveArrivals.map { it.messageId })
    }

    @Test fun initialAndOlderHistoryPagesAreNotArrivals() {
        val store = MessageHistoryStore()
        store.finishLoad(1L, listOf(message(5), message(4), message(3)), hasOlder = true)
        store.finishLoad(1L, listOf(message(2), message(1)), hasOlder = false)
        val history = store.observe(1L).value
        assertEquals(0L, history.liveArrivalSequence)
        assertTrue(history.liveArrivals.isEmpty())
    }

    @Test fun aSendConfirmationReplacingTheTemporaryBubbleIsNotAnotherArrival() {
        val store = MessageHistoryStore()
        store.put(message(10, state = MessageSendState.Sending))
        store.put(message(100), replaces = 10L)
        val history = store.observe(1L).value
        assertEquals(1L, history.liveArrivalSequence)
        assertEquals(listOf(10L), history.liveArrivals.map { it.messageId })
        assertEquals(listOf(100L), history.messages.map { it.id })
    }

    @Test fun aServerMessageThatReplacesNothingVisibleStillArrives() {
        val store = MessageHistoryStore()
        store.put(message(100), replaces = 10L)
        val history = store.observe(1L).value
        assertEquals(1L, history.liveArrivalSequence)
        assertEquals(listOf(100L), history.liveArrivals.map { it.messageId })
    }

    @Test fun reactionEditsAndReadMarkersDoNotAddArrivals() {
        val store = MessageHistoryStore()
        store.put(message(1))
        store.update(1L, 1L) { it.copy(isEdited = true) }
        store.readInbox(1L, 1L)
        store.readOutbox(1L, 1L)
        assertEquals(1, store.observe(1L).value.liveArrivals.size)
    }

    @Test fun theArrivalLogIsBoundedToARecentBurst() {
        val store = MessageHistoryStore()
        (1L..100L).forEach { store.put(message(it)) }
        val history = store.observe(1L).value
        assertEquals(100L, history.liveArrivalSequence)
        assertEquals(64, history.liveArrivals.size)
        assertEquals(37L, history.liveArrivals.first().messageId)
        assertEquals(100L, history.liveArrivals.last().messageId)
    }

    @Test fun sendConfirmationsAreRecordedAsIdReplacements() {
        val store = MessageHistoryStore()
        store.put(message(10, state = MessageSendState.Sending))
        store.put(message(100), replaces = 10L)
        val history = store.observe(1L).value
        assertEquals(listOf(10L to 100L), history.recentReplacements.map { it.oldId to it.newId })
    }

    @Test fun theReplacementLogIsBounded() {
        val store = MessageHistoryStore()
        (1L..100L).forEach { index ->
            store.put(message(1000L + index, state = MessageSendState.Sending))
            store.put(message(2000L + index), replaces = 1000L + index)
        }
        val replacements = store.observe(1L).value.recentReplacements
        assertEquals(64, replacements.size)
        assertEquals(1037L, replacements.first().oldId)
        assertEquals(2100L, replacements.last().newId)
    }
}
