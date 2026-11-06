package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.model.ChatMemoryMark
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.DiceStickerAsset
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageDiceInfo
import com.beykus.beygram.domain.model.MessageDiceState
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageSendState
import com.beykus.beygram.domain.model.MessageSpecialContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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

    @Test fun dropChatMemoryRemovesKeptMessagesAndOtherMarks() {
        val store = MessageHistoryStore()
        store.put(message(1L, 1L))
        store.put(message(2L, 2L))
        store.put(message(3L, 3L))
        store.markDeleted(1L, listOf(2L), deletedAtMillis = 5L)
        store.update(1L, 3L) { it.copy(memory = ChatMemoryMark(versionCount = 2)) }
        store.dropChatMemory(1L)
        val history = store.observe(1L).value
        assertEquals(listOf(1L, 3L), history.messages.map { it.id })
        assertEquals(listOf<ChatMemoryMark?>(null, null), history.messages.map { it.memory })
    }

    @Test fun droppedKeptMessageComesBackWhenChatMemoryKeepsTheChatAgain() {
        val store = MessageHistoryStore()
        store.put(message(2L, 2L))
        store.markDeleted(1L, listOf(2L), deletedAtMillis = 5L)
        store.dropChatMemory(1L)
        assertEquals(emptyList<ChatMessage>(), store.observe(1L).value.messages)
        store.finishLoad(1L, listOf(message(2L, 2L).copy(memory = ChatMemoryMark(deletedAtMillis = 5L))), false)
        assertEquals(listOf(2L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun chatsWithChatMemoryMarksListsOnlyChatsThatHoldThem() {
        val store = MessageHistoryStore()
        store.put(message(1L, 1L, chatId = 1L))
        store.put(message(2L, 2L, chatId = 2L))
        store.markDeleted(2L, listOf(2L), deletedAtMillis = 5L)
        assertEquals(listOf(2L), store.chatsWithChatMemoryMarks())
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

    @Test fun outsideWindowCommentsTakeUpdatesWithoutJoiningTheWindow() {
        val store = MessageHistoryStore()
        store.recordOutsideWindow(1L, listOf(message(5L, 5L)))
        store.update(1L, 5L) { it.copy(isEdited = true) }
        val history = store.observe(1L).value
        assertEquals(emptyList<ChatMessage>(), history.messages)
        assertTrue(history.outsideWindowMessages.single().isEdited)
    }

    @Test fun olderPageCannotRollBackAnOutsideWindowUpdate() {
        val store = MessageHistoryStore()
        store.recordOutsideWindow(1L, listOf(message(5L, 5L)))
        store.update(1L, 5L) { it.copy(isEdited = true) }
        store.recordOutsideWindow(1L, listOf(message(5L, 5L)))
        assertEquals(1, store.observe(1L).value.outsideWindowMessages.size)
        assertTrue(store.observe(1L).value.outsideWindowMessages.single().isEdited)
    }

    @Test fun outsideCopyUpdatedWhileAPageIsInFlightIsNotRolledBackByThePage() {
        val store = MessageHistoryStore()
        store.recordOutsideWindow(1L, listOf(message(5L, 5L)))
        store.beginLoad(1L)
        val requestVersion = store.version()
        store.update(1L, 5L) { it.copy(isEdited = true) }
        store.finishLoad(1L, listOf(message(5L, 5L)), hasOlder = false, requestVersion = requestVersion)
        val history = store.observe(1L).value
        assertEquals(listOf(5L), history.messages.map { it.id })
        assertTrue(history.messages.single().isEdited)
        assertTrue(history.outsideWindowMessages.single().isEdited)
    }

    @Test fun windowLoadsAndDeletionsLeaveOutsideWindowCommentsConsistent() {
        val store = MessageHistoryStore()
        store.recordOutsideWindow(1L, listOf(message(5L, 5L), message(6L, 6L)))
        store.finishLoad(1L, listOf(message(9L, 9L)), hasOlder = false)
        store.delete(1L, listOf(6L))
        val history = store.observe(1L).value
        assertEquals(listOf(9L), history.messages.map { it.id })
        assertEquals(listOf(5L), history.outsideWindowMessages.map { it.id })
    }

    @Test fun liveCopyOfAnOutsideWindowCommentRefreshesItsKeptCopy() {
        val store = MessageHistoryStore()
        store.recordOutsideWindow(1L, listOf(message(5L, 5L)))
        store.put(message(5L, 5L).copy(isEdited = true))
        assertTrue(store.observe(1L).value.outsideWindowMessages.single().isEdited)
    }

    @Test fun deletedOutsideWindowCommentCannotBeRecordedAgain() {
        val store = MessageHistoryStore()
        store.delete(1L, listOf(5L))
        store.recordOutsideWindow(1L, listOf(message(5L, 5L)))
        assertTrue(store.observe(1L).value.outsideWindowMessages.isEmpty())
    }

    @Test fun outsideWindowCommentMediaStaysReferenced() {
        val store = MessageHistoryStore()
        val file = message(5L, 5L).copy(media = MessageMedia(MediaKind.Document, fileId = 77))
        store.recordOutsideWindow(1L, listOf(file))
        assertEquals(setOf(77), store.mediaFileIds())
    }

    @Test fun diceAnimationFilesStayReferenced() {
        val store = MessageHistoryStore()
        val sticker = DiceStickerAsset(fileId = 88)
        val dice = MessageDiceInfo(
            emoji = "🎲",
            value = 4,
            initialState = MessageDiceState.Regular(sticker),
            finalState = MessageDiceState.Regular(sticker.copy(fileId = 89))
        )
        store.recordOutsideWindow(1L, listOf(message(5L, 5L).copy(special = MessageSpecialContent.Dice(dice))))
        assertEquals(setOf(88, 89), store.mediaFileIds())
    }

    @Test fun confirmedSendIsSplicedIntoItsPlaceAndItsTemporaryCopyLeaves() {
        val store = MessageHistoryStore()
        store.finishLoad(1L, listOf(message(1L, 1L), message(2L, 2L), message(4L, 4L)), hasOlder = false)
        store.put(message(-1L, 3L, state = MessageSendState.Sending))
        assertEquals(listOf(1L, 2L, -1L, 4L), store.observe(1L).value.messages.map { it.id })
        store.put(message(3L, 3L), replaces = -1L)
        assertEquals(listOf(1L, 2L, 3L, 4L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun mergeAfterAnEditThatChangesTheOrderSortsAgain() {
        val store = MessageHistoryStore()
        store.finishLoad(1L, listOf(message(1L, 5L), message(2L, 5L)), hasOlder = false)
        store.update(1L, 1L) { it.copy(sendState = MessageSendState.Sending) }
        store.put(message(3L, 5L))
        assertEquals(listOf(2L, 3L, 1L), store.observe(1L).value.messages.map { it.id })
    }

    @Test fun messageOrderIsTheOrderTheFullSortUses() {
        val legacy = compareBy<ChatMessage> { it.dateSeconds }.thenBy { it.sendState == MessageSendState.Sending }.thenBy { it.id }
        val random = Random(42L)
        val states = listOf(MessageSendState.Sending, MessageSendState.Sent, MessageSendState.Failed)
        repeat(2_000) {
            val a = message(random.nextLong(-3L, 4L), random.nextLong(0L, 3L), states[random.nextInt(states.size)])
            val b = message(random.nextLong(-3L, 4L), random.nextLong(0L, 3L), states[random.nextInt(states.size)])
            assertEquals(Integer.signum(legacy.compare(a, b)), Integer.signum(compareMessageOrder(a, b)))
        }
    }

    /**
     * Sends, confirmations, edits, deletions, pages and window loads, applied to a store that splices small batches and to one
     * that sorts every merge. Both get the same calls with the same arguments, so their lists must match after each call.
     */
    @Test fun splicedMergeGivesTheSameListAsAFullSortOnRandomSequences() {
        val random = Random(20261103)
        val spliced = MessageHistoryStore(maxCachedChats = 2)
        val sorted = MessageHistoryStore(maxCachedChats = 2, fastMerge = false)
        val chats = listOf(1L, 2L, 3L)
        var clock = 1_000L
        var nextServerId = 1L
        var nextTempId = -1L
        fun both(call: MessageHistoryStore.() -> Unit) {
            spliced.call()
            sorted.call()
        }
        repeat(3_000) { step ->
            if (random.nextInt(3) == 0) clock++
            val chatId = chats[random.nextInt(chats.size)]
            val held = spliced.observe(chatId).value.messages
            val sending = held.filter { it.sendState == MessageSendState.Sending }
            when (random.nextInt(20)) {
                0, 1 -> {
                    val temp = message(nextTempId--, clock, MessageSendState.Sending, chatId)
                    both { put(temp) }
                }
                2, 3, 4 -> if (sending.isNotEmpty()) {
                    val temp = sending[random.nextInt(sending.size)]
                    val server = message(nextServerId++, clock + random.nextLong(0L, 2L), MessageSendState.Sent, chatId)
                    both { put(server, replaces = temp.id) }
                }
                5 -> if (sending.isNotEmpty()) {
                    val temp = sending[random.nextInt(sending.size)]
                    both { put(temp.copy(sendState = MessageSendState.Failed), replaces = temp.id) }
                }
                6, 7, 8, 9 -> {
                    // A live arrival, or a newer copy of a message already held, as an edit or a reaction update brings.
                    val arrival = if (held.isNotEmpty() && random.nextInt(4) == 0) {
                        held[random.nextInt(held.size)].copy(isEdited = true)
                    } else {
                        message(nextServerId++, clock, MessageSendState.Sent, chatId)
                    }
                    both { put(arrival) }
                }
                10 -> if (nextTempId < -1L) {
                    // A late copy of a temporary message, which may have been retired already.
                    val late = message(random.nextLong(nextTempId + 1L, 0L), clock, MessageSendState.Sending, chatId)
                    both { put(late) }
                }
                11 -> if (held.isNotEmpty()) {
                    val target = held[random.nextInt(held.size)]
                    // Some edits change the sort keys, so the list is out of order until the next merge.
                    val kind = random.nextInt(10)
                    val edit: (ChatMessage) -> ChatMessage = { m ->
                        when (kind) {
                            0 -> m.copy(sendState = MessageSendState.Sent)
                            1 -> m.copy(dateSeconds = m.dateSeconds + 1L)
                            2 -> m.copy(isPinned = !m.isPinned)
                            else -> m.copy(text = "edit $step", isEdited = true)
                        }
                    }
                    both { update(chatId, target.id, edit) }
                }
                12 -> if (held.isNotEmpty()) {
                    val ids = held.shuffled(random).take(1 + random.nextInt(3)).map { it.id }
                    both { delete(chatId, ids) }
                }
                13, 14, 15 -> {
                    // A history page: it may overlap the messages held, and some of it may be older than an update.
                    val requestVersion = spliced.version()
                    val epoch = spliced.beginLoad(chatId)
                    assertEquals("epoch at step $step", epoch, sorted.beginLoad(chatId))
                    val size = if (random.nextInt(3) == 0) random.nextInt(1, 6) else random.nextInt(10, 60)
                    val page = List(size) {
                        val state = if (random.nextInt(8) == 0) MessageSendState.Failed else MessageSendState.Sent
                        message(random.nextLong(1L, nextServerId + 3L), clock - random.nextLong(0L, 20L), state, chatId)
                    }
                    val hasOlder = random.nextBoolean()
                    val continueLoading = random.nextInt(4) == 0
                    both { finishLoad(chatId, page, hasOlder, continueLoading, requestVersion, epoch) }
                }
                16 -> {
                    val first = random.nextLong(1L, nextServerId + 3L)
                    val window = List(random.nextInt(1, 40)) { message(first + it, clock - it, MessageSendState.Sent, chatId) }
                    val reachesLatest = random.nextBoolean()
                    both { replaceWithWindow(chatId, window, reachesLatest) }
                }
                17 -> {
                    val epoch = spliced.restartFromLatest(chatId)
                    assertEquals("restart at step $step", epoch, sorted.restartFromLatest(chatId))
                }
                18 -> {
                    val comments = List(random.nextInt(1, 4)) { message(nextServerId++, clock, MessageSendState.Sent, chatId) }
                    both { recordOutsideWindow(chatId, comments) }
                }
                else -> when (random.nextInt(4)) {
                    0 -> both { opened(chatId) }
                    1 -> both { closed(chatId) }
                    2 -> {
                        val readId = random.nextLong(1L, nextServerId + 1L)
                        both { readInbox(chatId, readId) }
                    }
                    else -> {
                        val endLoading = random.nextBoolean()
                        both { fail(chatId, UiText.Raw("offline"), endLoading = endLoading) }
                    }
                }
            }
            assertEquals("version at step $step", sorted.version(), spliced.version())
            for (id in chats) {
                assertEquals("chat $id at step $step", sorted.observe(id).value, spliced.observe(id).value)
            }
        }
    }
}
