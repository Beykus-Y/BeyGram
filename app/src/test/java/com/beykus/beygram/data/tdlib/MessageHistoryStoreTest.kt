package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHistoryStoreTest {
    private fun message(id: Long, date: Long = 1L, state: MessageSendState = MessageSendState.Sent, chatId: Long = 1L) =
        ChatMessage(id, chatId, "test", "12:00", true, date, state)

    @Test fun successReplacesTemporaryMessageAndLateResponseCannotRestoreIt() {
        val store = MessageHistoryStore()
        val pending = message(10L, state = MessageSendState.Sending)
        store.put(pending)
        store.put(pending)
        store.put(message(100L), replaces = 10L)
        store.put(pending) // A late SendMessage callback after the success update.
        assertEquals(listOf(message(100L)), store.observe(1L).value.messages)
    }

    @Test fun overlappingHistoryRetainsLiveMessagesWithoutDuplicates() {
        val store = MessageHistoryStore()
        store.put(message(3L, 3L))
        store.finishLoad(1L, listOf(message(2L, 2L), message(1L)), true)
        store.finishLoad(1L, listOf(message(3L, 3L), message(2L, 2L)), true)
        assertEquals(listOf(1L, 2L, 3L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun failedSendUpdatesExistingMessage() {
        val store = MessageHistoryStore()
        store.put(message(10L, state = MessageSendState.Sending))
        store.put(message(10L, state = MessageSendState.Failed), replaces = 10L)
        assertEquals(listOf(MessageSendState.Failed), store.observe(1L).value.messages.map { it.sendState })
    }

    @Test fun deletedMessageCannotReturnFromAnOlderHistoryRequest() {
        val store = MessageHistoryStore()
        store.put(message(1L))
        store.delete(1L, listOf(1L))
        store.finishLoad(1L, listOf(message(1L)), false)
        assertEquals(emptyList<ChatMessage>(), store.observe(1L).value.messages)
        assertFalse(store.observe(1L).value.isLoading)
    }

    @Test fun jumpWindowReplacesLoadedMessagesButKeepsUnsentOnes() {
        val store = MessageHistoryStore()
        store.put(message(1L))
        store.put(message(2L, 2L, state = MessageSendState.Sending))
        store.replaceWithWindow(1L, listOf(message(50L, 50L), message(51L, 51L)), reachesLatest = false)
        val history = store.observe(1L).value
        assertEquals(listOf(2L, 50L, 51L), history.messages.map { it.id })
        assertTrue(history.hasNewerMessages)
        assertTrue(history.hasOlderMessages)
        assertFalse(history.isLoading)
    }

    @Test fun windowThatReachesTheNewestMessagesIsNotMarkedAsGapped() {
        val store = MessageHistoryStore()
        store.replaceWithWindow(1L, listOf(message(50L, 50L)), reachesLatest = true)
        assertFalse(store.observe(1L).value.hasNewerMessages)
    }

    @Test fun incomingMessageDoesNotJoinAJumpWindow() {
        val store = MessageHistoryStore()
        store.replaceWithWindow(1L, listOf(message(50L, 50L)), reachesLatest = false)
        store.put(message(60L, 60L))
        assertEquals(listOf(50L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun sentConfirmationReplacesUnsentMessageInsideAJumpWindow() {
        val store = MessageHistoryStore()
        store.replaceWithWindow(1L, listOf(message(50L, 50L)), reachesLatest = false)
        store.put(message(10L, state = MessageSendState.Sending))
        store.put(message(100L, 70L), replaces = 10L)
        val messages = store.observe(1L).value.messages
        assertEquals(listOf(50L, 100L), messages.map { it.id })
        assertEquals(listOf(MessageSendState.Sent, MessageSendState.Sent), messages.map { it.sendState })
    }

    @Test fun loadStartedBeforeJumpWindowCannotMergeIntoIt() {
        val store = MessageHistoryStore()
        val epoch = store.beginLoad(1L)!!
        store.replaceWithWindow(1L, listOf(message(50L, 50L)), reachesLatest = false)
        val applied = store.finishLoad(1L, listOf(message(1L), message(2L, 2L)), hasOlder = true, epoch = epoch)
        assertFalse(applied)
        assertEquals(listOf(50L), store.observe(1L).value.messages.map { it.id })
        assertTrue(store.observe(1L).value.hasNewerMessages)
    }

    @Test fun restartFromLatestSupersedesOlderLoadRunningInTheJumpWindow() {
        val store = MessageHistoryStore()
        store.replaceWithWindow(1L, listOf(message(50L, 50L)), reachesLatest = false)
        val olderEpoch = store.beginLoad(1L)!!
        val latestEpoch = store.restartFromLatest(1L)
        assertFalse(store.finishLoad(1L, listOf(message(49L, 49L)), hasOlder = true, epoch = olderEpoch))
        assertTrue(store.finishLoad(1L, listOf(message(1L)), hasOlder = false, epoch = latestEpoch))
        val history = store.observe(1L).value
        assertEquals(listOf(1L), history.messages.map { it.id })
        assertFalse(history.hasNewerMessages)
        assertFalse(history.isLoading)
    }

    @Test fun snapshotRequestedBeforeAnUpdateCannotRollItBack() {
        val store = MessageHistoryStore()
        store.put(message(1L))
        store.beginLoad(1L)
        val requestVersion = store.version()
        store.update(1L, 1L) { it.copy(isEdited = true) }
        store.finishLoad(1L, listOf(message(1L)), hasOlder = false, requestVersion = requestVersion)
        assertTrue(store.observe(1L).value.messages.single().isEdited)
    }

    @Test fun coldChatsBeyondTheLimitLoseMessagesWhileOpenUnsentAndLoadingChatsKeepTheirs() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.opened(1L)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L, state = MessageSendState.Sending))
        store.put(message(3L, chatId = 3L))
        assertEquals(listOf(3L), store.observe(3L).value.messages.map { it.id })
        store.put(message(4L, chatId = 4L))
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
        assertEquals(listOf(2L), store.observe(2L).value.messages.map { it.id })
        assertEquals(emptyList<ChatMessage>(), store.observe(3L).value.messages)
        assertEquals(listOf(4L), store.observe(4L).value.messages.map { it.id })
    }

    @Test fun loadingChatIsNotEvicted() {
        val store = MessageHistoryStore(maxCachedChats = 1)
        store.beginLoad(1L)
        store.put(message(2L, chatId = 2L))
        store.put(message(3L, chatId = 3L))
        assertTrue(store.observe(1L).value.isLoading)
        assertEquals(emptyList<ChatMessage>(), store.observe(2L).value.messages)
    }

    @Test fun recentlyUsedColdChatsSurviveWhileTheLeastRecentlyUsedGoes() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L))
        store.put(message(3L, chatId = 1L))
        store.put(message(4L, chatId = 3L))
        assertEquals(listOf(1L, 3L), store.observe(1L).value.messages.map { it.id })
        assertEquals(emptyList<ChatMessage>(), store.observe(2L).value.messages)
        assertEquals(listOf(4L), store.observe(3L).value.messages.map { it.id })
    }

    @Test fun closedChatIsEvictedLastBecauseItWasUsedLast() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.opened(1L)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L))
        store.closed(1L)
        store.put(message(3L, chatId = 3L))
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
        assertEquals(emptyList<ChatMessage>(), store.observe(2L).value.messages)
    }

    @Test fun chatsWithoutMessagesDoNotTakeACacheSlot() {
        val store = MessageHistoryStore(maxCachedChats = 2)
        store.put(message(1L, chatId = 1L))
        store.opened(2L)
        store.opened(3L)
        store.closed(3L)
        store.put(message(4L, chatId = 4L))
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
        assertEquals(listOf(4L), store.observe(4L).value.messages.map { it.id })
    }

    @Test fun evictionKeepsReadMarkers() {
        val store = MessageHistoryStore(maxCachedChats = 1)
        store.readInbox(1L, 5L)
        store.readOutbox(1L, 6L)
        store.put(message(1L, chatId = 1L))
        store.put(message(2L, chatId = 2L))
        assertEquals(emptyList<ChatMessage>(), store.observe(1L).value.messages)
        assertEquals(5L, store.observe(1L).value.lastReadInboxMessageId)
        assertEquals(6L, store.observe(1L).value.lastReadOutboxMessageId)
    }

    @Test fun retiredIdsOfABulkDeleteStayUntilTheRunningLoadEnds() {
        val store = MessageHistoryStore()
        val ids = (1L..300L).toList()
        store.beginLoad(1L)
        store.delete(1L, ids)
        store.finishLoad(1L, ids.map { message(it) }, hasOlder = false, continueLoading = true)
        assertTrue(store.observe(1L).value.messages.isEmpty())
        store.finishLoad(1L, ids.map { message(it) }, hasOlder = false)
        assertTrue(store.observe(1L).value.messages.isEmpty())
        // The load has ended, so the oldest retired ids are forgotten while the newest still filter.
        store.finishLoad(1L, listOf(message(1L), message(300L)), hasOlder = false)
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun oldestRetiredIdsAreForgottenWhenTheLimitIsReached() {
        val store = MessageHistoryStore()
        store.delete(1L, (1L..300L).toList())
        store.finishLoad(1L, listOf(message(300L), message(1L)), hasOlder = false)
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun failedLoadAlsoEndsTheRetiredIdsWindow() {
        val store = MessageHistoryStore()
        store.beginLoad(1L)
        store.delete(1L, (1L..300L).toList())
        store.fail(1L, UiText.Raw("offline"))
        store.finishLoad(1L, listOf(message(300L), message(1L)), hasOlder = false)
        assertEquals(listOf(1L), store.observe(1L).value.messages.map { it.id })
    }
    @Test fun forumTopicStoresWithSameParentChatStayIndependent() {
        val chatId = 900L
        val firstTopic = MessageHistoryStore(maxCachedChats = 1)
        val secondTopic = MessageHistoryStore(maxCachedChats = 1)
        val firstEpoch = firstTopic.restartFromLatest(chatId)
        val secondEpoch = secondTopic.restartFromLatest(chatId)

        firstTopic.finishLoad(chatId, listOf(message(101L, chatId = chatId)), hasOlder = false, epoch = firstEpoch)
        secondTopic.finishLoad(chatId, listOf(message(202L, chatId = chatId)), hasOlder = false, epoch = secondEpoch)

        assertEquals(listOf(101L), firstTopic.observe(chatId).value.messages.map { it.id })
        assertEquals(listOf(202L), secondTopic.observe(chatId).value.messages.map { it.id })

        firstTopic.delete(chatId, listOf(101L))
        assertTrue(firstTopic.observe(chatId).value.messages.isEmpty())
        assertEquals(listOf(202L), secondTopic.observe(chatId).value.messages.map { it.id })
    }

    @Test fun serviceEventRemainsAnEventAfterHistoryMerge() {
        val history = MessageHistoryStore()
        val event = message(77L).copy(text = "Создана тема", serviceText = "Создана тема")
        history.finishLoad(1L, listOf(event), hasOlder = false)
        history.put(event)
        assertEquals("Создана тема", history.observe(1L).value.messages.single().serviceText)
    }

}
